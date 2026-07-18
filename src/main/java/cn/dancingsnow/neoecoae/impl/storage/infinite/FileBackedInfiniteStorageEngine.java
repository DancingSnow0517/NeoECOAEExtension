package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageKeyHash;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
import java.util.zip.CRC32;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileBackedInfiniteStorageEngine implements ECOInfiniteStorageEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileBackedInfiniteStorageEngine.class);
    private static final int SHARD_COUNT = 256;
    private static final int LEGACY_WAL_VERSION = 1;
    private static final int WAL_VERSION = 2;
    private static final int MAX_WAL_RECORD_BYTES = 16 * 1024 * 1024;
    private static final int WAL_BUFFER_BYTES = 64 * 1024;
    private static final long IDLE_CHECKPOINT_DELAY_NANOS = 5_000_000_000L;
    private static final HugeAmount LONG_MAX_AMOUNT = HugeAmount.of(Long.MAX_VALUE);

    private final HolderLookup.Provider registries;
    private final UUID domainId;
    private final Path domainPath;
    private final Path walPath;
    private final Map<AEKey, HugeAmount> amounts = new HashMap<>();
    private final Map<AEKey, Integer> keyShards = new HashMap<>();
    private final List<Set<AEKey>> keysByShard = createShardKeySets();
    private final Map<AEKey, Long> loadedKeyRevisions = new HashMap<>();
    private final Map<AEKey, Integer> loadedKeySourceShards = new HashMap<>();
    private final KeyCounter visibleStacks = new KeyCounter();
    private final Map<AEKeyType, MutableTypeStats> typeStats = new HashMap<>();
    private final Map<AEKey, HugeAmount> hugeStacks = new HashMap<>();
    private final Map<AEKey, Long> pendingWalDeltas = new HashMap<>();
    private final List<CompoundTag> stagedWalRecords = new ArrayList<>();
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
    private volatile boolean degraded;
    @Nullable private volatile Throwable persistenceFailure;

    @Nullable private DataOutputStream walOut;
    @Nullable private FileOutputStream walFileOut;
    @Nullable private Future<?> pendingWalWrite;

    public FileBackedInfiniteStorageEngine(HolderLookup.Provider registries, UUID domainId, Path domainPath) {
        this.registries = registries;
        this.domainId = domainId;
        this.domainPath = domainPath;
        this.walPath = domainPath.resolve("wal_000.log");
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
            applyDelta(key, amount, true);
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
        applyDelta(key, amount, false);
        submitWalRecords(List.of(createWalRecord(key, BigInteger.valueOf(amount), transactionId)));
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
            applyDelta(key, -visible, true);
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
            if (stats.storedTypes > 0L && !stats.storedAmount.isZero()) {
                snapshot.add(new TypeStats(entry.getKey(), stats.storedTypes, stats.storedAmount));
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
        snapshot.sort((left, right) -> right.amount().compareTo(left.amount()));
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
        if (degraded) {
            return;
        }
        if (dirtyShards.isEmpty()) {
            return;
        }
        if (maxNanos <= 0L) {
            awaitPendingWal();
            checkpointDirtyShards(Long.MAX_VALUE);
            return;
        }
        long now = System.nanoTime();
        if (lastMutationNanos != Long.MIN_VALUE && now - lastMutationNanos < IDLE_CHECKPOINT_DELAY_NANOS) {
            return;
        }
        awaitPendingWal();
        checkpointDirtyShards(now + maxNanos);
    }

    synchronized void submitPendingWal() {
        throwIfPersistenceFailed();
        if (!degraded) {
            submitWalRecords(drainPendingWalRecords());
        }
    }

    private void checkpointDirtyShards(long deadline) {
        // Snapshot construction is bounded on the server thread; compression, replacement, and force happen on the
        // checkpoint worker. A newer mutation leaves the shard dirty until a later snapshot catches up.
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

    private void scheduleCheckpoint(int shard) {
        if (checkpointWrites.containsKey(shard)) {
            return;
        }
        long snapshotRevision = shardMutationRevisions[shard];
        CompoundTag snapshot = createShardSnapshot(shard, snapshotRevision);
        Future<?> future = ECOInfiniteStorageIoWorker.submitCheckpoint(
            () -> writeShardSnapshot(shard, snapshotRevision, snapshot)
        );
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

    @Override
    public synchronized void closeAndFlush() {
        if (degraded) {
            awaitPendingWalQuietly();
            closeWalOutput();
            return;
        }
        throwIfPersistenceFailed();
        submitWalRecords(drainPendingWalRecords());
        awaitPendingWal();
        checkpointDirtyShards(Long.MAX_VALUE);
        closeWalOutput();
    }

    private void applyDelta(AEKey key, long delta, boolean writeWal) {
        if (delta == 0L) {
            return;
        }
        HugeAmount current = getAmount(key);
        boolean added = delta > 0L;
        HugeAmount changed;
        HugeAmount next;
        if (added) {
            changed = HugeAmount.of(delta);
            next = current.add(changed);
        } else {
            long requested = -delta;
            changed = HugeAmount.of(requested).min(current);
            next = current.subtract(changed);
        }
        applyChange(key, current, next, changed, added);
        if (writeWal) {
            mergePendingWalDelta(key, delta);
        }
    }

    private void applyDelta(AEKey key, BigInteger delta) {
        if (delta.signum() == 0) {
            return;
        }
        if (delta.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0
            && delta.compareTo(BigInteger.valueOf(-Long.MAX_VALUE)) >= 0) {
            applyDelta(key, delta.longValue(), false);
            return;
        }
        HugeAmount current = getAmount(key);
        BigInteger nextValue = current.toBigInteger().add(delta).max(BigInteger.ZERO);
        HugeAmount next = HugeAmount.of(nextValue);
        int comparison = next.compareTo(current);
        if (comparison == 0) {
            return;
        }
        boolean added = comparison > 0;
        HugeAmount changed = added ? next.subtract(current) : current.subtract(next);
        applyChange(key, current, next, changed, added);
    }

    private void applyChange(
        AEKey key,
        HugeAmount current,
        HugeAmount next,
        HugeAmount changed,
        boolean added
    ) {
        int shard = shardFor(key);
        if (next.isZero()) {
            amounts.remove(key);
            removeShardIndex(key, shard);
        } else {
            amounts.put(key, next);
            addShardIndex(key, shard);
        }
        storedAmount = added ? storedAmount.add(changed) : storedAmount.subtract(changed);
        updateIndexes(key, current, next, changed, added);
        revision = revision == Long.MAX_VALUE ? 0L : revision + 1L;
        dirtyShards.add(shard);
        shardMutationRevisions[shard] = revision;
        lastMutationNanos = System.nanoTime();
    }

    private void mergePendingWalDelta(AEKey key, long delta) {
        Long pending = pendingWalDeltas.get(key);
        if (pending == null) {
            pendingWalDeltas.put(key, delta);
            return;
        }
        try {
            long merged = Math.addExact(pending, delta);
            if (merged == 0L) {
                pendingWalDeltas.remove(key);
            } else {
                pendingWalDeltas.put(key, merged);
            }
        } catch (ArithmeticException overflow) {
            stagedWalRecords.add(createWalRecord(key, BigInteger.valueOf(pending), null));
            pendingWalDeltas.put(key, delta);
        }
    }

    private List<CompoundTag> drainPendingWalRecords() {
        if (pendingWalDeltas.isEmpty() && stagedWalRecords.isEmpty()) {
            return List.of();
        }
        List<CompoundTag> records = new ArrayList<>(stagedWalRecords.size() + pendingWalDeltas.size());
        records.addAll(stagedWalRecords);
        stagedWalRecords.clear();
        for (Map.Entry<AEKey, Long> entry : new ArrayList<>(pendingWalDeltas.entrySet())) {
            long delta = entry.getValue();
            if (delta != 0L) {
                records.add(createWalRecord(entry.getKey(), BigInteger.valueOf(delta), null));
            }
        }
        pendingWalDeltas.clear();
        return records;
    }

    private void submitWalRecords(List<CompoundTag> records) {
        if (records.isEmpty()) {
            return;
        }
        pendingWalWrite = ECOInfiniteStorageIoWorker.submit(() -> writeWalRecords(records));
    }

    synchronized void awaitPendingWal() {
        Future<?> pending = pendingWalWrite;
        if (pending != null) {
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
        }
        throwIfPersistenceFailed();
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

    private void awaitPendingWalQuietly() {
        try {
            awaitPendingWal();
        } catch (RuntimeException e) {
            LOGGER.error("Unable to finish ECO infinite storage WAL before shutdown", e);
        }
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
            loadedKeyRevisions.clear();
            loadedKeySourceShards.clear();
            dirtyShards.clear();
            rebuildIndexes();
            return;
        }
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
        pendingWalDeltas.clear();
        dirtyShards.clear();
        dirtyShards.addAll(recoveredDirtyShards);
        for (int shard : dirtyShards) {
            shardMutationRevisions[shard] = revision;
        }
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
            updateIndexes(entry.getKey(), HugeAmount.ZERO, entry.getValue(), entry.getValue(), true);
        }
    }

    private void updateIndexes(
        AEKey key,
        HugeAmount previous,
        HugeAmount next,
        HugeAmount changed,
        boolean added
    ) {
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

        int typeDelta = (previous.isZero() ? 0 : -1) + (next.isZero() ? 0 : 1);
        if (changed.isZero() && typeDelta == 0) {
            return;
        }

        AEKeyType keyType = key.getType();
        MutableTypeStats stats = typeStats.computeIfAbsent(keyType, ignored -> new MutableTypeStats());
        stats.storedTypes += typeDelta;
        stats.storedAmount = added ? stats.storedAmount.add(changed) : stats.storedAmount.subtract(changed);
        if (stats.storedTypes <= 0L || stats.storedAmount.isZero()) {
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
            CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
            long shardRevision = tag.getLong("revision");
            int hashVersion = tag.getInt(ECOStorageKeyHash.SHARD_HASH_VERSION_TAG);
            ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                AEKey key = AEKey.fromTagGeneric(registries, entry.getCompound("key"));
                HugeAmount amount = HugeAmount.read(entry.getCompound("amount"));
                if (key != null && !amount.isZero()) {
                    int targetShard = shardFor(key);
                    if (targetShard != shard || hashVersion < ECOStorageKeyHash.VERSION) {
                        // Rewriting the source removes stale pre-hash-version records. Without this, a key that is
                        // later emptied in its target shard would be resurrected from the old shard on next load.
                        dirtyShards.add(shard);
                        dirtyShards.add(targetShard);
                    }
                    Long previousRevision = loadedKeyRevisions.get(key);
                    int previousSource = loadedKeySourceShards.getOrDefault(key, -1);
                    boolean currentIsTarget = targetShard == shard;
                    boolean previousIsTarget = targetShard == previousSource;
                    if (previousRevision == null || (currentIsTarget && !previousIsTarget)
                            || (currentIsTarget == previousIsTarget && shardRevision > previousRevision)) {
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
            entryTag.put("key", key.toTagGeneric(registries));
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
            ByteBuffer data = ByteBuffer.wrap(compressed.toByteArray());
            Path tmp = domainPath.resolve(shardFileName(shard) + ".tmp");
            try (FileChannel channel = FileChannel.open(
                tmp,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )) {
                while (data.hasRemaining()) {
                    channel.write(data);
                }
                channel.force(true);
            }
            replaceAtomically(tmp, shardPath(shard));
        } catch (IOException | RuntimeException e) {
            degraded = true;
            persistenceFailure = e;
            LOGGER.error("Unable to write ECO infinite storage shard {} at revision {}", shard, snapshotRevision, e);
            throw new IllegalStateException("Unable to write ECO infinite storage shard " + shard, e);
        }
    }

    private CompoundTag createWalRecord(AEKey key, BigInteger delta, @Nullable UUID transactionId) {
        CompoundTag tag = new CompoundTag();
        // Keep legacy fields on nested records so an unusually large batch can fall back to v1 frames safely.
        tag.putInt("version", LEGACY_WAL_VERSION);
        tag.putString("domain", domainId.toString());
        tag.putLong("revision", revision);
        tag.put("key", key.toTagGeneric(registries));
        tag.putString("delta", delta.toString());
        if (transactionId != null) {
            tag.putUUID("transaction", transactionId);
        }
        return tag;
    }

    private void writeWalRecords(List<CompoundTag> records) {
        try {
            CompoundTag batch = new CompoundTag();
            batch.putInt("version", WAL_VERSION);
            batch.putString("domain", domainId.toString());
            ListTag entries = new ListTag();
            for (CompoundTag tag : records) {
                entries.add(tag);
            }
            batch.put("records", entries);

            ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
            NbtIo.writeCompressed(batch, payloadOut);
            byte[] payload = payloadOut.toByteArray();
            DataOutputStream out = walOutput();
            if (payload.length > 0 && payload.length <= MAX_WAL_RECORD_BYTES) {
                writeWalFrame(out, payload);
            } else {
                LOGGER.warn("ECO infinite storage WAL batch exceeded {} bytes; using legacy frames", MAX_WAL_RECORD_BYTES);
                writeLegacyWalFrames(out, records);
            }
            out.flush();
            if (walFileOut != null) {
                walFileOut.getChannel().force(false);
            }
        } catch (IOException | RuntimeException e) {
            degraded = true;
            persistenceFailure = e;
            LOGGER.error("Unable to persist ECO infinite storage WAL {}", walPath, e);
            throw new IllegalStateException("Unable to persist ECO infinite storage WAL", e);
        }
    }

    private void writeLegacyWalFrames(DataOutputStream out, List<CompoundTag> records) throws IOException {
        for (CompoundTag record : records) {
            ByteArrayOutputStream payloadOut = new ByteArrayOutputStream();
            NbtIo.writeCompressed(record, payloadOut);
            byte[] payload = payloadOut.toByteArray();
            if (payload.length <= 0 || payload.length > MAX_WAL_RECORD_BYTES) {
                throw new IOException("ECO infinite storage WAL record is too large");
            }
            writeWalFrame(out, payload);
        }
    }

    private static void writeWalFrame(DataOutputStream out, byte[] payload) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(payload);
        out.writeInt(payload.length);
        out.writeInt((int) crc.getValue());
        out.write(payload);
    }

    private DataOutputStream walOutput() throws IOException {
        if (walOut == null) {
            walFileOut = new FileOutputStream(walPath.toFile(), true);
            walOut = new DataOutputStream(new BufferedOutputStream(walFileOut, WAL_BUFFER_BYTES));
        }
        return walOut;
    }

    private void throwIfPersistenceFailed() {
        if (persistenceFailure != null) {
            throw persistenceException(persistenceFailure);
        }
    }

    private IllegalStateException persistenceException(Throwable cause) {
        if (cause instanceof IllegalStateException exception) {
            return exception;
        }
        return new IllegalStateException("Unable to persist ECO infinite storage", cause);
    }

    private void replayWal() {
        if (!Files.isRegularFile(walPath)) {
            return;
        }
        long repairOffset = -1L;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(walPath))) {
            long fileSize = Files.size(walPath);
            long offset = 0L;
            while (offset < fileSize) {
                long recordStart = offset;
                if (fileSize - offset < Integer.BYTES * 2L) {
                    repairOffset = recordStart;
                    break;
                }
                int length = in.readInt();
                int expectedCrc = in.readInt();
                offset += Integer.BYTES * 2L;
                if (length <= 0 || length > MAX_WAL_RECORD_BYTES) {
                    if (offset == fileSize) {
                        repairOffset = recordStart;
                    } else {
                        degraded = true;
                        LOGGER.error("Invalid ECO infinite storage WAL record length {} in {}", length, walPath);
                    }
                    break;
                }
                if (fileSize - offset < length) {
                    repairOffset = recordStart;
                    break;
                }
                byte[] payload = new byte[length];
                in.readFully(payload);
                offset += length;
                CRC32 crc = new CRC32();
                crc.update(payload);
                if ((int) crc.getValue() != expectedCrc) {
                    if (offset == fileSize) {
                        repairOffset = recordStart;
                    } else {
                        degraded = true;
                        LOGGER.error("CRC mismatch in ECO infinite storage WAL {}", walPath);
                    }
                    break;
                }
                CompoundTag tag = NbtIo.readCompressed(
                    new ByteArrayInputStream(payload),
                    NbtAccounter.unlimitedHeap()
                );
                int version = tag.getInt("version");
                if (version == WAL_VERSION) {
                    validateWalDomain(tag);
                    ListTag records = tag.getList("records", Tag.TAG_COMPOUND);
                    for (int i = 0; i < records.size(); i++) {
                        replayWalRecord(records.getCompound(i));
                    }
                } else if (version == LEGACY_WAL_VERSION) {
                    validateWalDomain(tag);
                    replayWalRecord(tag);
                } else {
                    throw new IOException("Unsupported ECO infinite storage WAL version " + version);
                }
            }
        } catch (RuntimeException | IOException e) {
            degraded = true;
            LOGGER.error("Unable to replay ECO infinite storage WAL {}", walPath, e);
        }
        if (!degraded && repairOffset >= 0L) {
            repairWalTail(repairOffset);
        }
        if (!degraded) {
            for (UUID transactionId : committedTransactions) {
                writeTransactionReceipt(transactionId);
            }
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
            Files.createDirectories(receipt.getParent());
            Path tmp = receipt.resolveSibling(receipt.getFileName() + ".tmp");
            Files.writeString(tmp, transactionId.toString(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
            try (var channel = java.nio.channels.FileChannel.open(tmp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            replaceAtomically(tmp, receipt);
        } catch (IOException e) {
            degraded = true;
            throw new IllegalStateException("Unable to persist ECO infinite storage transaction receipt", e);
        }
    }

    private void truncateWal() {
        try {
            closeWalOutput();
            try (FileChannel channel = FileChannel.open(
                walPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )) {
                channel.force(true);
            }
        } catch (IOException e) {
            degraded = true;
            persistenceFailure = e;
            throw new IllegalStateException("Unable to checkpoint ECO infinite storage WAL", e);
        }
    }

    private void replayWalRecord(CompoundTag tag) {
        AEKey key = AEKey.fromTagGeneric(registries, tag.getCompound("key"));
        long recordRevision = tag.getLong("revision");
        UUID transactionId = tag.hasUUID("transaction") ? tag.getUUID("transaction") : null;
        if (key != null) {
            if (recordRevision > loadedKeyRevisions.getOrDefault(key, 0L)) {
                applyDelta(key, new BigInteger(tag.getString("delta")));
            }
            if (transactionId != null) {
                committedTransactions.add(transactionId);
            }
            revision = Math.max(revision, recordRevision);
        }
    }

    private void validateWalDomain(CompoundTag tag) throws IOException {
        String recordDomain = tag.getString("domain");
        if (!domainId.toString().equals(recordDomain)) {
            throw new IOException("ECO infinite storage WAL domain mismatch: " + recordDomain);
        }
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void repairWalTail(long validLength) {
        try (FileChannel channel = FileChannel.open(walPath, StandardOpenOption.WRITE)) {
            channel.truncate(validLength);
            channel.force(true);
            LOGGER.warn("Discarded incomplete ECO infinite storage WAL tail in {} at byte {}", walPath, validLength);
        } catch (IOException e) {
            degraded = true;
            LOGGER.error("Unable to repair ECO infinite storage WAL tail {}", walPath, e);
        }
    }

    private void closeWalOutput() {
        if (walOut == null) {
            return;
        }
        try {
            walOut.close();
        } catch (IOException e) {
            LOGGER.warn("Unable to close ECO infinite storage WAL {}", walPath, e);
        } finally {
            walOut = null;
            walFileOut = null;
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
        int shard = ECOStorageKeyHash.shardFor(registries, key, SHARD_COUNT);
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

    private static final class MutableTypeStats {
        private long storedTypes;
        private HugeAmount storedAmount = HugeAmount.ZERO;
    }
}
