package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageFiles;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageIoWorker;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageKeyHash;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageWal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileBackedInfiniteStorageEngine implements ECOInfiniteStorageEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileBackedInfiniteStorageEngine.class);
    private static final int SHARD_COUNT = 256;

    /** One record per frame; the record's revision is the whole tick's revision. */
    private static final int WAL_VERSION_SINGLE = 1;

    /** Batched frame; the records still carry the tick's revision rather than their own. */
    private static final int WAL_VERSION_BATCH = 2;

    /** Batched frame whose records carry the revision of the mutation that produced them. */
    private static final int WAL_VERSION = 3;

    private static final int MAX_WAL_RECORD_BYTES = 16 * 1024 * 1024;
    private static final int WAL_BUFFER_BYTES = 64 * 1024;
    private static final long IDLE_CHECKPOINT_DELAY_NANOS = 5_000_000_000L;
    private static final long WAL_CHECKPOINT_THRESHOLD_BYTES = 32L * 1024L * 1024L;
    private static final HugeAmount LONG_MAX_AMOUNT = HugeAmount.of(Long.MAX_VALUE);

    private final UUID domainId;
    private final Path domainPath;
    private final ECOStorageWal wal;
    private final Map<AEKey, HugeAmount> amounts = new HashMap<>();
    private final Map<AEKey, Integer> keyShards = new HashMap<>();
    private final List<Set<AEKey>> keysByShard = createShardKeySets();
    private final Map<AEKey, Long> loadedKeyRevisions = new HashMap<>();
    private final Map<AEKey, Integer> loadedKeySourceShards = new HashMap<>();
    private final KeyCounter visibleStacks = new KeyCounter();
    private final Map<AEKeyType, MutableTypeStats> typeStats = new HashMap<>();
    private final Map<AEKey, HugeAmount> hugeStacks = new HashMap<>();
    private final Map<AEKey, PendingDelta> dirtyDeltas = new HashMap<>();
    private final Set<Integer> dirtyShards = new HashSet<>();
    private final Map<Integer, CheckpointWrite> checkpointWrites = new HashMap<>();
    private final Set<UUID> committedTransactions = new HashSet<>();
    private final long[] shardRevisions = new long[SHARD_COUNT];
    private final long[] shardMutationRevisions = new long[SHARD_COUNT];
    private List<TypeStats> typeStatsSnapshot = List.of();
    private boolean typeStatsSnapshotDirty = true;
    private List<HugeStack> hugeStacksSnapshot = List.of();
    private boolean hugeStacksSnapshotDirty = true;
    private HugeAmount storedAmount = HugeAmount.ZERO;
    private long revision;
    private long lastMutationNanos = Long.MIN_VALUE;
    private boolean legacyWalReplayed;
    private volatile boolean degraded;

    @Nullable private volatile Throwable persistenceFailure;

    @Nullable private Future<?> pendingWalWrite;

    public FileBackedInfiniteStorageEngine(UUID domainId, Path domainPath) {
        this.domainId = domainId;
        this.domainPath = domainPath;
        this.wal = new ECOStorageWal(domainPath.resolve("wal_000.log"), MAX_WAL_RECORD_BYTES, WAL_BUFFER_BYTES);
        load();
    }

    @Override
    public synchronized long insert(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0L) {
            return 0L;
        }
        if (mode == Actionable.MODULATE) {
            if (degraded) {
                return 0L;
            }
            applyDelta(key, BigInteger.valueOf(amount), true);
        }
        return amount;
    }

    @Override
    public synchronized long insertOnce(UUID transactionId, AEKey key, long amount) {
        if (transactionId == null || key == null || amount <= 0L || degraded) {
            return 0L;
        }
        if (committedTransactions.contains(transactionId)) {
            return amount;
        }
        if (Files.isRegularFile(transactionReceipt(transactionId))) {
            committedTransactions.add(transactionId);
            return amount;
        }
        applyDelta(key, BigInteger.valueOf(amount), false);
        // applyDelta has just stamped this mutation's revision, which is exactly what the record has to carry.
        submitWalRecords(List.of(createWalRecord(key, BigInteger.valueOf(amount), revision, transactionId)));
        awaitPendingWal();
        committedTransactions.add(transactionId);
        writeTransactionReceipt(transactionId);
        return amount;
    }

    @Override
    public synchronized long extract(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0L) {
            return 0L;
        }
        HugeAmount current = getAmount(key);
        HugeAmount extracted = HugeAmount.of(amount).min(current);
        if (extracted.isZero()) {
            return 0L;
        }
        long visible = extracted.toLongSaturated();
        if (mode == Actionable.MODULATE) {
            if (degraded) {
                return 0L;
            }
            applyDelta(key, BigInteger.valueOf(visible).negate(), true);
        }
        return visible;
    }

    @Override
    public synchronized HugeAmount getAmount(AEKey key) {
        HugeAmount amount = amounts.get(key);
        return amount == null ? HugeAmount.ZERO : amount;
    }

    @Override
    public synchronized void getAvailableStacks(KeyCounter out) {
        out.addAll(visibleStacks);
    }

    @Override
    public synchronized long getRevision() {
        return revision;
    }

    @Override
    public synchronized boolean isEmpty() {
        return amounts.isEmpty();
    }

    @Override
    public synchronized boolean isHealthy() {
        return !degraded;
    }

    @Override
    public synchronized HugeAmount getStoredAmount() {
        return storedAmount;
    }

    @Override
    public synchronized int getStoredTypes() {
        return amounts.size();
    }

    @Override
    public synchronized Collection<TypeStats> getTypeStats() {
        if (!typeStatsSnapshotDirty) {
            return typeStatsSnapshot;
        }
        List<TypeStats> snapshot = new ArrayList<>(typeStats.size());
        for (Map.Entry<AEKeyType, MutableTypeStats> entry : typeStats.entrySet()) {
            MutableTypeStats stats = entry.getValue();
            if (stats.storedTypes > 0L && stats.storedAmount.signum() > 0) {
                snapshot.add(new TypeStats(entry.getKey(), stats.storedTypes, HugeAmount.of(stats.storedAmount)));
            }
        }
        typeStatsSnapshot = List.copyOf(snapshot);
        typeStatsSnapshotDirty = false;
        return typeStatsSnapshot;
    }

    @Override
    public synchronized Collection<HugeStack> getHugeStacks() {
        if (!hugeStacksSnapshotDirty) {
            return hugeStacksSnapshot;
        }
        List<HugeStack> snapshot = new ArrayList<>(hugeStacks.size());
        for (Map.Entry<AEKey, HugeAmount> entry : hugeStacks.entrySet()) {
            snapshot.add(new HugeStack(entry.getKey(), entry.getValue()));
        }
        snapshot.sort((left, right) -> {
            int amountOrder = right.amount().compareTo(left.amount());
            if (amountOrder != 0) {
                return amountOrder;
            }
            return left.key()
                    .toTagGeneric()
                    .toString()
                    .compareTo(right.key().toTagGeneric().toString());
        });
        hugeStacksSnapshot = List.copyOf(snapshot);
        hugeStacksSnapshotDirty = false;
        return hugeStacksSnapshot;
    }

    @Override
    public synchronized void flushBudgeted(long maxNanos) {
        submitPendingWal();
        checkpointBudgeted(maxNanos);
    }

    synchronized void checkpointBudgeted(long maxNanos) {
        throwIfPersistenceFailed();
        if (degraded || dirtyShards.isEmpty()) {
            return;
        }
        if (maxNanos <= 0L) {
            awaitPendingWal();
            checkpointDirtyShards(Long.MAX_VALUE);
            return;
        }
        long now = System.nanoTime();
        boolean idle = lastMutationNanos == Long.MIN_VALUE || now - lastMutationNanos >= IDLE_CHECKPOINT_DELAY_NANOS;
        if (!idle && wal.sizeBytes() < WAL_CHECKPOINT_THRESHOLD_BYTES) {
            // Sustained traffic: batching pays off. Waiting is only safe while the log stays small enough that
            // replaying it after a crash is cheap.
            completeCheckpointWrites(false);
            return;
        }
        checkpointDirtyShards(now + maxNanos);
    }

    synchronized void submitPendingWal() {
        throwIfPersistenceFailed();
        if (!degraded) {
            submitWalRecords(drainPendingWalRecords());
        }
    }

    private void checkpointDirtyShards(long deadline) {
        // Snapshots must never be taken while deltas are still merging. A record folds several mutations into one
        // delta stamped with the newest of them; if a snapshot lands in the middle it holds part of that delta but
        // compares as older than the record, and recovery has no way to apply only the missing half.
        submitWalRecords(drainPendingWalRecords());
        boolean waitForAll = deadline == Long.MAX_VALUE;
        do {
            completeCheckpointWrites(false);
            Set<Integer> pending = new HashSet<>(dirtyShards);
            for (int shard : pending) {
                scheduleCheckpoint(shard);
                if (System.nanoTime() >= deadline) {
                    break;
                }
            }
            completeCheckpointWrites(waitForAll);
        } while (waitForAll && !dirtyShards.isEmpty());
        if (dirtyShards.isEmpty() && checkpointWrites.isEmpty()) {
            awaitPendingWal();
            truncateWal();
        }
    }

    @Override
    public synchronized void closeAndFlush() {
        if (degraded) {
            awaitPendingWalQuietly();
            wal.close();
            return;
        }
        throwIfPersistenceFailed();
        submitWalRecords(drainPendingWalRecords());
        awaitPendingWal();
        checkpointDirtyShards(Long.MAX_VALUE);
        wal.close();
    }

    private void applyDelta(AEKey key, BigInteger delta, boolean writeWal) {
        if (delta.signum() == 0) {
            return;
        }
        HugeAmount current = getAmount(key);
        BigInteger nextValue = current.toBigInteger().add(delta);
        if (nextValue.signum() < 0) {
            nextValue = BigInteger.ZERO;
        }
        HugeAmount next = HugeAmount.of(nextValue);
        if (next.isZero()) {
            amounts.remove(key);
            removeShardIndex(key, shardFor(key));
        } else {
            amounts.put(key, next);
            addShardIndex(key, shardFor(key));
        }
        storedAmount = HugeAmount.of(storedAmount.toBigInteger().add(nextValue.subtract(current.toBigInteger())));
        updateIndexes(key, current, next);
        revision = revision == Long.MAX_VALUE ? 0L : revision + 1L;
        if (writeWal) {
            // The record has to carry this mutation's revision. Stamping the tick's revision instead makes a record
            // look newer than a shard snapshot that already contains it, and recovery then applies the delta twice.
            dirtyDeltas.merge(key, new PendingDelta(delta, revision), PendingDelta::mergedWith);
        }
        dirtyShards.add(shardFor(key));
        shardMutationRevisions[shardFor(key)] = revision;
        lastMutationNanos = System.nanoTime();
    }

    private void load() {
        try {
            Files.createDirectories(domainPath);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create ECO infinite domain directory " + domainPath, e);
        }
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            readShard(shard);
        }
        if (degraded) {
            amounts.clear();
            rebuildIndexes();
            return;
        }
        discardSupersededLegacyShardEntries();
        rebuildIndexes();
        replayWal();
        if (degraded) {
            amounts.clear();
            dirtyShards.clear();
            rebuildIndexes();
            return;
        }
        Set<Integer> recoveredDirtyShards = new HashSet<>(dirtyShards);
        rebuildIndexes();
        loadedKeyRevisions.clear();
        loadedKeySourceShards.clear();
        dirtyDeltas.clear();
        dirtyShards.clear();
        dirtyShards.addAll(recoveredDirtyShards);
        for (int shard : dirtyShards) {
            shardMutationRevisions[shard] = revision;
        }
    }

    private void discardSupersededLegacyShardEntries() {
        amounts.entrySet().removeIf(entry -> {
            AEKey key = entry.getKey();
            int targetShard = shardFor(key);
            int sourceShard = loadedKeySourceShards.getOrDefault(key, targetShard);
            long sourceRevision = loadedKeyRevisions.getOrDefault(key, 0L);
            if (sourceShard != targetShard && shardRevisions[targetShard] > sourceRevision) {
                loadedKeyRevisions.put(key, shardRevisions[targetShard]);
                loadedKeySourceShards.put(key, targetShard);
                return true;
            }
            return false;
        });
    }

    private void rebuildIndexes() {
        visibleStacks.clear();
        typeStats.clear();
        hugeStacks.clear();
        keyShards.clear();
        keysByShard.forEach(Set::clear);
        hugeStacksSnapshot = List.of();
        hugeStacksSnapshotDirty = true;
        storedAmount = HugeAmount.ZERO;
        for (Map.Entry<AEKey, HugeAmount> entry : amounts.entrySet()) {
            storedAmount = storedAmount.add(entry.getValue());
            addShardIndex(entry.getKey(), shardFor(entry.getKey()));
            updateIndexes(entry.getKey(), HugeAmount.ZERO, entry.getValue());
        }
    }

    private void updateIndexes(AEKey key, HugeAmount previous, HugeAmount next) {
        if (next.isZero()) {
            visibleStacks.remove(key);
        } else {
            visibleStacks.set(key, next.toLongSaturated());
        }
        if (next.compareTo(LONG_MAX_AMOUNT) > 0) {
            hugeStacks.put(key, next);
            hugeStacksSnapshotDirty = true;
        } else if (hugeStacks.remove(key) != null) {
            hugeStacksSnapshotDirty = true;
        }

        BigInteger delta = next.toBigInteger().subtract(previous.toBigInteger());
        int typeDelta = (previous.isZero() ? 0 : -1) + (next.isZero() ? 0 : 1);
        if (delta.signum() == 0 && typeDelta == 0) {
            return;
        }

        AEKeyType keyType = key.getType();
        MutableTypeStats stats = typeStats.computeIfAbsent(keyType, ignored -> new MutableTypeStats());
        stats.storedTypes += typeDelta;
        stats.storedAmount = stats.storedAmount.add(delta);
        if (stats.storedTypes <= 0L || stats.storedAmount.signum() <= 0) {
            typeStats.remove(keyType);
        }
        typeStatsSnapshotDirty = true;
    }

    private void readShard(int shard) {
        Path path = shardPath(shard);
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (InputStream input = Files.newInputStream(path)) {
            CompoundTag tag = NbtIo.readCompressed(input);
            long shardRevision = tag.getLong("revision");
            int hashVersion = tag.getInt(ECOStorageKeyHash.SHARD_HASH_VERSION_TAG);
            ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                AEKey key = AEKey.fromTagGeneric(entry.getCompound("key"));
                HugeAmount amount = HugeAmount.read(entry.getCompound("amount"));
                if (key != null && !amount.isZero()) {
                    int targetShard = shardFor(key);
                    if (targetShard != shard || hashVersion < ECOStorageKeyHash.VERSION) {
                        dirtyShards.add(shard);
                        dirtyShards.add(targetShard);
                    }
                    Long previousRevision = loadedKeyRevisions.get(key);
                    if (previousRevision == null
                            || shardRevision > previousRevision
                            || (shardRevision == previousRevision && targetShard == shard)) {
                        amounts.put(key, amount);
                        loadedKeyRevisions.put(key, shardRevision);
                        loadedKeySourceShards.put(key, shard);
                    }
                }
            }
            revision = Math.max(revision, shardRevision);
            shardRevisions[shard] = shardRevision;
        } catch (RuntimeException | IOException e) {
            degraded = true;
            LOGGER.error("Unable to read ECO infinite storage shard {}", path, e);
        }
    }

    private CompoundTag createShardSnapshot(int shard, long snapshotRevision) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putInt(ECOStorageKeyHash.SHARD_HASH_VERSION_TAG, ECOStorageKeyHash.VERSION);
        tag.putLong("revision", snapshotRevision);
        tag.putString("domain", domainId.toString());
        ListTag entries = new ListTag();
        for (AEKey key : keysByShard.get(shard)) {
            HugeAmount amount = amounts.get(key);
            if (amount == null || amount.isZero()) {
                continue;
            }
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("key", key.toTagGeneric());
            entryTag.put("amount", amount.write());
            entries.add(entryTag);
        }
        tag.put("entries", entries);
        return tag;
    }

    private void writeShardSnapshot(int shard, long snapshotRevision, CompoundTag tag) {
        try {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, compressed);
            ECOStorageFiles.writeAtomically(shardPath(shard), compressed.toByteArray());
        } catch (IOException | RuntimeException e) {
            degraded = true;
            persistenceFailure = e;
            LOGGER.error("Unable to write ECO infinite storage shard {} at revision {}", shard, snapshotRevision, e);
            throw new IllegalStateException("Unable to write ECO infinite storage shard " + shard, e);
        }
    }

    private void scheduleCheckpoint(int shard) {
        if (checkpointWrites.containsKey(shard)) {
            return;
        }
        // The highest revision of any mutation this shard has seen, so every record the snapshot already contains
        // compares as no newer than the snapshot itself.
        long snapshotRevision = shardMutationRevisions[shard];
        CompoundTag snapshot = createShardSnapshot(shard, snapshotRevision);
        Future<?> future =
                ECOStorageIoWorker.submitCheckpoint(() -> writeShardSnapshot(shard, snapshotRevision, snapshot));
        checkpointWrites.put(shard, new CheckpointWrite(snapshotRevision, future));
    }

    private void completeCheckpointWrites(boolean waitForAll) {
        for (Map.Entry<Integer, CheckpointWrite> entry : new ArrayList<>(checkpointWrites.entrySet())) {
            int shard = entry.getKey();
            CheckpointWrite write = entry.getValue();
            if (!waitForAll && !write.future().isDone()) {
                continue;
            }
            awaitPersistenceTask(write.future(), "checkpoint shard " + shard);
            checkpointWrites.remove(shard);
            shardRevisions[shard] = write.revision();
            if (shardMutationRevisions[shard] == write.revision()) {
                dirtyShards.remove(shard);
            }
        }
    }

    private CompoundTag createWalRecord(
            AEKey key, BigInteger delta, long recordRevision, @Nullable UUID transactionId) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", WAL_VERSION);
        tag.putString("domain", domainId.toString());
        tag.putLong("revision", recordRevision);
        tag.put("key", key.toTagGeneric());
        tag.putString("delta", delta.toString());
        if (transactionId != null) {
            tag.putUUID("transaction", transactionId);
        }
        return tag;
    }

    private List<CompoundTag> drainPendingWalRecords() {
        if (dirtyDeltas.isEmpty()) {
            return List.of();
        }
        List<CompoundTag> records = new ArrayList<>(dirtyDeltas.size());
        for (Map.Entry<AEKey, PendingDelta> entry : dirtyDeltas.entrySet()) {
            PendingDelta pending = entry.getValue();
            if (pending.delta().signum() != 0) {
                records.add(createWalRecord(entry.getKey(), pending.delta(), pending.revision(), null));
            }
        }
        dirtyDeltas.clear();
        return records;
    }

    private void submitWalRecords(List<CompoundTag> records) {
        if (records.isEmpty()) {
            return;
        }
        pendingWalWrite = ECOStorageIoWorker.submit(() -> writeWalRecords(records));
    }

    private void writeWalRecords(List<CompoundTag> records) {
        try {
            byte[] payload = encodeWalFrame(records);
            if (payload.length > 0 && payload.length <= wal.maxRecordBytes()) {
                wal.append(payload);
            } else {
                // Too big for one frame; split it so each record still lands in a well-formed frame.
                for (CompoundTag record : records) {
                    wal.append(encodeWalFrame(List.of(record)));
                }
            }
            wal.sync();
        } catch (IOException | RuntimeException e) {
            degraded = true;
            persistenceFailure = e;
            LOGGER.error("Unable to persist ECO infinite storage WAL {}", wal.path(), e);
            throw new IllegalStateException("Unable to persist ECO infinite storage WAL", e);
        }
    }

    private byte[] encodeWalFrame(List<CompoundTag> records) throws IOException {
        CompoundTag batch = new CompoundTag();
        batch.putInt("version", WAL_VERSION);
        batch.putString("domain", domainId.toString());
        ListTag entries = new ListTag();
        for (CompoundTag record : records) {
            entries.add(record);
        }
        batch.put("records", entries);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(batch, out);
        return out.toByteArray();
    }

    synchronized void awaitPendingWal() {
        Future<?> pending = pendingWalWrite;
        if (pending == null) {
            return;
        }
        try {
            pending.get();
            if (pendingWalWrite == pending) {
                pendingWalWrite = null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while persisting ECO infinite storage WAL", e);
        } catch (ExecutionException e) {
            throw persistenceException(e.getCause());
        }
        throwIfPersistenceFailed();
    }

    private void awaitPendingWalQuietly() {
        try {
            awaitPendingWal();
        } catch (RuntimeException e) {
            LOGGER.error("Unable to finish ECO infinite storage WAL before shutdown", e);
        }
    }

    private void awaitPersistenceTask(Future<?> task, String operation) {
        try {
            task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while persisting ECO infinite storage " + operation, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            persistenceFailure = cause;
            degraded = true;
            throw persistenceException(cause);
        }
    }

    private void throwIfPersistenceFailed() {
        if (persistenceFailure != null) {
            throw persistenceException(persistenceFailure);
        }
    }

    private IllegalStateException persistenceException(Throwable cause) {
        return cause instanceof IllegalStateException exception
                ? exception
                : new IllegalStateException("Unable to persist ECO infinite storage", cause);
    }

    private void replayWal() {
        ECOStorageWal.Status status = wal.replay(this::replayWalFrame);
        if (status == ECOStorageWal.Status.CORRUPT) {
            degraded = true;
            LOGGER.error("Unable to replay ECO infinite storage WAL {}", wal.path());
            return;
        }
        if (legacyWalReplayed) {
            LOGGER.warn(
                    "Replayed ECO infinite storage WAL records from before the per-mutation revision fix in {}. Those"
                            + " records carry the whole tick's revision, so a delta may be applied twice if its shard"
                            + " happened to be checkpointed mid-tick. Stopping the server cleanly once removes the log"
                            + " and rules this out",
                    wal.path());
        }
        for (UUID transactionId : committedTransactions) {
            writeTransactionReceipt(transactionId);
        }
    }

    private void replayWalFrame(byte[] payload) {
        CompoundTag tag;
        try (InputStream input = new ByteArrayInputStream(payload)) {
            tag = NbtIo.readCompressed(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to decode an ECO infinite storage WAL frame", e);
        }
        int version = tag.getInt("version");
        if (version == WAL_VERSION || version == WAL_VERSION_BATCH) {
            validateWalDomain(tag);
            legacyWalReplayed |= version == WAL_VERSION_BATCH;
            ListTag records = tag.getList("records", Tag.TAG_COMPOUND);
            for (int i = 0; i < records.size(); i++) {
                replayWalRecord(records.getCompound(i));
            }
        } else if (version == WAL_VERSION_SINGLE) {
            validateWalDomain(tag);
            legacyWalReplayed = true;
            replayWalRecord(tag);
        } else {
            throw new IllegalStateException("Unsupported ECO infinite storage WAL version " + version);
        }
    }

    private void truncateWal() {
        try {
            wal.truncate();
        } catch (IOException e) {
            degraded = true;
            persistenceFailure = e;
            throw new IllegalStateException("Unable to checkpoint ECO infinite storage WAL", e);
        }
    }

    private void replayWalRecord(CompoundTag tag) {
        AEKey key = AEKey.fromTagGeneric(tag.getCompound("key"));
        long recordRevision = tag.getLong("revision");
        UUID transactionId = tag.hasUUID("transaction") ? tag.getUUID("transaction") : null;
        if (key != null) {
            if (recordRevision > loadedKeyRevisions.getOrDefault(key, 0L)) {
                applyDelta(key, new BigInteger(tag.getString("delta")), false);
            }
            if (transactionId != null) {
                committedTransactions.add(transactionId);
            }
            revision = Math.max(revision, recordRevision);
        }
    }

    private void validateWalDomain(CompoundTag tag) {
        String recordDomain = tag.getString("domain");
        if (!domainId.toString().equals(recordDomain)) {
            throw new IllegalStateException("ECO infinite storage WAL domain mismatch: " + recordDomain);
        }
    }

    private Path transactionReceipt(UUID transactionId) {
        return domainPath.resolve("transactions").resolve(transactionId + ".done");
    }

    private void writeTransactionReceipt(UUID transactionId) {
        Path receipt = transactionReceipt(transactionId);
        if (Files.isRegularFile(receipt)) {
            return;
        }
        try {
            ECOStorageFiles.writeAtomically(receipt, transactionId.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            degraded = true;
            persistenceFailure = e;
            throw new IllegalStateException("Unable to persist ECO infinite storage transaction receipt", e);
        }
    }

    private Path shardPath(int shard) {
        return domainPath.resolve(shardFileName(shard));
    }

    private static String shardFileName(int shard) {
        return "shard_%03d.dat".formatted(shard);
    }

    private int shardFor(AEKey key) {
        Integer cached = keyShards.get(key);
        if (cached != null) {
            return cached;
        }
        int shard = ECOStorageKeyHash.shardFor(key, SHARD_COUNT);
        keyShards.put(key, shard);
        return shard;
    }

    private void addShardIndex(AEKey key, int shard) {
        keyShards.put(key, shard);
        keysByShard.get(shard).add(key);
    }

    private void removeShardIndex(AEKey key, int shard) {
        keysByShard.get(shard).remove(key);
        keyShards.remove(key);
    }

    private static List<Set<AEKey>> createShardKeySets() {
        List<Set<AEKey>> shards = new ArrayList<>(SHARD_COUNT);
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            shards.add(new HashSet<>());
        }
        return shards;
    }

    private record CheckpointWrite(long revision, Future<?> future) {}

    /** A merged run of deltas for one key, carrying the revision of the newest mutation folded into it. */
    private record PendingDelta(BigInteger delta, long revision) {
        PendingDelta mergedWith(PendingDelta newer) {
            return new PendingDelta(delta.add(newer.delta), Math.max(revision, newer.revision));
        }
    }

    private static final class MutableTypeStats {
        private long storedTypes;
        private BigInteger storedAmount = BigInteger.ZERO;
    }
}
