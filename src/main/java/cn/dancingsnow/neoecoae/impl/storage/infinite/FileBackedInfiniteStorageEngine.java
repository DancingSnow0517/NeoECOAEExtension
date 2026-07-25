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
import java.io.EOFException;
import java.io.FilterOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
    private static final int BINARY_WAL_MAGIC = 0x45434F33;
    private static final int BINARY_WAL_VERSION = 3;
    private static final int MAX_WAL_RECORD_BYTES = 16 * 1024 * 1024;
    private static final int WAL_BUFFER_BYTES = 64 * 1024;
    private static final long WAL_SEGMENT_MAX_BYTES = 32L * 1024L * 1024L;
    private static final long WAL_SEGMENT_MAX_RECORDS = 100_000L;
    private static final long WAL_CHECKPOINT_INTERVAL_NANOS = 60_000_000_000L;
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
    private final Map<AEKey, PendingWalDelta> pendingWalDeltas = new HashMap<>();
    private final List<WalRecord> stagedWalRecords = new ArrayList<>();
    private final Set<Integer> dirtyShards = new HashSet<>();
    private final Map<Integer, CheckpointWrite> checkpointWrites = new HashMap<>();
    private final Object walStateLock = new Object();
    private final List<SealedWalSegment> sealedWalSegments = new ArrayList<>();
    private final Map<Integer, Long> activeWalShardRevisions = new HashMap<>();
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
    private volatile long lastCheckpointNanos = System.nanoTime();
    private volatile boolean degraded;
    @Nullable private volatile Throwable persistenceFailure;

    @Nullable private DataOutputStream walOut;
    @Nullable private FileOutputStream walFileOut;
    @Nullable private Future<?> pendingWalWrite;
    private long activeWalBytes;
    private long activeWalRecordCount;
    private long activeWalMaxRevision;

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
        submitWalRecords(drainPendingWalRecords());
        awaitPendingWal();
        applyDelta(key, amount, false);
        submitWalRecords(List.of(createWalRecord(key, BigInteger.valueOf(amount), transactionId, revision)));
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
        completeCheckpointWrites(false);
        reclaimCoveredWalSegments();
        if (maxNanos <= 0L) {
            awaitPendingWal();
            checkpointShards(Long.MAX_VALUE, true);
            return;
        }
        long now = System.nanoTime();
        boolean idle = lastMutationNanos != Long.MIN_VALUE
            && now - lastMutationNanos >= IDLE_CHECKPOINT_DELAY_NANOS;
        if (!idle && !hasUncoveredWalSegments()) {
            return;
        }
        awaitPendingWal();
        checkpointShards(now + maxNanos, false);
    }

    synchronized void submitPendingWal() {
        throwIfPersistenceFailed();
        if (!degraded) {
            submitWalRecords(drainPendingWalRecords());
        }
    }

    private void checkpointShards(long deadline, boolean forceAll) {
        // Snapshot construction is bounded on the server thread; compression, replacement, and force happen on the
        // checkpoint worker. Sealed WAL segments only require the snapshot to cover their per-shard barrier; newer
        // mutations may leave the shard dirty without preventing reclamation of the older segment.
        boolean waitForAll = forceAll || deadline == Long.MAX_VALUE;
        do {
            completeCheckpointWrites(false);
            Set<Integer> pending = forceAll ? new HashSet<>(dirtyShards) : checkpointCandidates();
            for (int shard : pending) {
                scheduleCheckpoint(shard);
                if (System.nanoTime() >= deadline) {
                    break;
                }
            }
            completeCheckpointWrites(waitForAll);
        } while (waitForAll && !dirtyShards.isEmpty());
        reclaimCoveredWalSegments();
        if (dirtyShards.isEmpty() && checkpointWrites.isEmpty() && !hasSealedWalSegments()) {
            awaitPendingWal();
            truncateWal();
        }
    }

    private Set<Integer> checkpointCandidates() {
        Set<Integer> candidates = new HashSet<>();
        synchronized (walStateLock) {
            for (SealedWalSegment segment : sealedWalSegments) {
                for (Map.Entry<Integer, Long> entry : segment.shardRevisions().entrySet()) {
                    if (shardRevisions[entry.getKey()] < entry.getValue()) {
                        candidates.add(entry.getKey());
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(dirtyShards);
        }
        return candidates;
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
            lastCheckpointNanos = System.nanoTime();
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
        checkpointShards(Long.MAX_VALUE, true);
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
        PendingWalDelta pending = pendingWalDeltas.get(key);
        if (pending == null) {
            pendingWalDeltas.put(key, new PendingWalDelta(delta, revision));
            return;
        }
        try {
            long merged = Math.addExact(pending.delta(), delta);
            if (merged == 0L) {
                pendingWalDeltas.remove(key);
            } else {
                pendingWalDeltas.put(key, new PendingWalDelta(merged, revision));
            }
        } catch (ArithmeticException overflow) {
            stagedWalRecords.add(createWalRecord(key, BigInteger.valueOf(pending.delta()), null, pending.revision()));
            pendingWalDeltas.put(key, new PendingWalDelta(delta, revision));
        }
    }

    private List<WalRecord> drainPendingWalRecords() {
        if (pendingWalDeltas.isEmpty() && stagedWalRecords.isEmpty()) {
            return List.of();
        }
        List<WalRecord> records = new ArrayList<>(stagedWalRecords.size() + pendingWalDeltas.size());
        records.addAll(stagedWalRecords);
        stagedWalRecords.clear();
        for (Map.Entry<AEKey, PendingWalDelta> entry : new ArrayList<>(pendingWalDeltas.entrySet())) {
            PendingWalDelta pending = entry.getValue();
            long delta = pending.delta();
            if (delta != 0L) {
                records.add(createWalRecord(entry.getKey(), BigInteger.valueOf(delta), null, pending.revision()));
            }
        }
        pendingWalDeltas.clear();
        return records;
    }

    private void submitWalRecords(List<WalRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        if (pendingWalWrite != null) {
            awaitPendingWal();
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
        // WAL replay updates the derived totals and indexes through applyChange. Initialize them from the
        // checkpoint first, otherwise the first negative delta tries to subtract from a zero storedAmount.
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
            Path tmp = domainPath.resolve(shardFileName(shard) + ".tmp");
            try (FileOutputStream fileOut = new FileOutputStream(tmp.toFile(), false)) {
                OutputStream nonClosingOut = new FilterOutputStream(fileOut) {
                    @Override
                    public void close() throws IOException {
                        flush();
                    }
                };
                NbtIo.writeCompressed(tag, nonClosingOut);
                nonClosingOut.flush();
                fileOut.getChannel().force(true);
            }
            replaceAtomically(tmp, shardPath(shard));
        } catch (IOException | RuntimeException e) {
            degraded = true;
            persistenceFailure = e;
            LOGGER.error("Unable to write ECO infinite storage shard {} at revision {}", shard, snapshotRevision, e);
            throw new IllegalStateException("Unable to write ECO infinite storage shard " + shard, e);
        }
    }

    private WalRecord createWalRecord(
        AEKey key,
        BigInteger delta,
        @Nullable UUID transactionId,
        long recordRevision
    ) {
        return new WalRecord(recordRevision, shardFor(key), key, delta, transactionId);
    }

    private void writeWalRecords(List<WalRecord> records) {
        try {
            DataOutputStream out = walOutput();
            long bytesWritten = 0L;
            for (WalRecord record : records) {
                byte[] payload = encodeWalRecord(record);
                if (payload.length <= 0 || payload.length > MAX_WAL_RECORD_BYTES) {
                    throw new IOException("ECO infinite storage WAL record is too large");
                }
                writeWalFrame(out, payload);
                bytesWritten += Integer.BYTES * 2L + payload.length;
            }
            out.flush();

            boolean rotate;
            synchronized (walStateLock) {
                activeWalBytes += bytesWritten;
                activeWalRecordCount += records.size();
                for (WalRecord record : records) {
                    activeWalMaxRevision = Math.max(activeWalMaxRevision, record.revision());
                    activeWalShardRevisions.merge(record.shard(), record.revision(), Math::max);
                }
                rotate = shouldRotateActiveWal();
            }

            // Strict durability is intentionally fixed: each tick's WAL batch is forced before the next tick waits
            // for this task, preserving the existing one-tick maximum durability window.
            if (walFileOut != null) {
                walFileOut.getChannel().force(false);
            }
            if (rotate) {
                sealActiveWal();
            }
        } catch (IOException | RuntimeException e) {
            degraded = true;
            persistenceFailure = e;
            LOGGER.error("Unable to persist ECO infinite storage WAL {}", walPath, e);
            throw new IllegalStateException("Unable to persist ECO infinite storage WAL", e);
        }
    }

    private byte[] encodeWalRecord(WalRecord record) throws IOException {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(payloadBytes)) {
            payload.writeInt(BINARY_WAL_MAGIC);
            payload.writeInt(BINARY_WAL_VERSION);
            payload.writeLong(domainId.getMostSignificantBits());
            payload.writeLong(domainId.getLeastSignificantBits());
            payload.writeLong(record.revision());
            payload.writeBoolean(record.transactionId() != null);
            if (record.transactionId() != null) {
                payload.writeLong(record.transactionId().getMostSignificantBits());
                payload.writeLong(record.transactionId().getLeastSignificantBits());
            }

            ByteArrayOutputStream keyBytes = new ByteArrayOutputStream();
            try (DataOutputStream keyOut = new DataOutputStream(keyBytes)) {
                NbtIo.write(record.key().toTagGeneric(registries), keyOut);
            }
            payload.writeInt(keyBytes.size());
            keyBytes.writeTo(payload);

            byte[] delta = record.delta().toByteArray();
            payload.writeInt(delta.length);
            payload.write(delta);
        }
        return payloadBytes.toByteArray();
    }

    private boolean shouldRotateActiveWal() {
        if (activeWalRecordCount <= 0L) {
            return false;
        }
        return activeWalBytes >= WAL_SEGMENT_MAX_BYTES
            || activeWalRecordCount >= WAL_SEGMENT_MAX_RECORDS
            || System.nanoTime() - lastCheckpointNanos >= WAL_CHECKPOINT_INTERVAL_NANOS;
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

    private void sealActiveWal() throws IOException {
        long barrierRevision;
        Map<Integer, Long> shardBarriers;
        synchronized (walStateLock) {
            if (activeWalRecordCount <= 0L) {
                return;
            }
            barrierRevision = activeWalMaxRevision;
            shardBarriers = Map.copyOf(activeWalShardRevisions);
        }

        closeWalOutputChecked();
        Path sealedPath = sealedWalPath(barrierRevision);
        if (Files.exists(sealedPath)) {
            throw new IOException("ECO infinite storage sealed WAL already exists: " + sealedPath);
        }
        moveAtomically(walPath, sealedPath);
        synchronized (walStateLock) {
            sealedWalSegments.add(new SealedWalSegment(sealedPath, barrierRevision, shardBarriers));
            sealedWalSegments.sort(Comparator.comparingLong(SealedWalSegment::barrierRevision));
            activeWalBytes = 0L;
            activeWalRecordCount = 0L;
            activeWalMaxRevision = 0L;
            activeWalShardRevisions.clear();
        }
    }

    private void closeWalOutputChecked() throws IOException {
        if (walOut == null) {
            return;
        }
        try {
            walOut.close();
        } finally {
            walOut = null;
            walFileOut = null;
        }
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
        List<Path> sealedPaths = listSealedWalPaths();
        for (Path sealedPath : sealedPaths) {
            WalScanResult result = replayWalFile(sealedPath, false);
            if (degraded) {
                return;
            }
            sealedWalSegments.add(new SealedWalSegment(
                sealedPath,
                result.maxRevision(),
                Map.copyOf(result.shardRevisions())
            ));
        }
        sealedWalSegments.sort(Comparator.comparingLong(SealedWalSegment::barrierRevision));
        if (Files.isRegularFile(walPath)) {
            WalScanResult active = replayWalFile(walPath, true);
            if (degraded) {
                return;
            }
            try {
                activeWalBytes = Files.size(walPath);
            } catch (IOException e) {
                degraded = true;
                LOGGER.error("Unable to inspect ECO infinite storage WAL {}", walPath, e);
                return;
            }
            activeWalRecordCount = active.recordCount();
            activeWalMaxRevision = active.maxRevision();
            activeWalShardRevisions.putAll(active.shardRevisions());
        }
        for (UUID transactionId : committedTransactions) {
            writeTransactionReceipt(transactionId);
        }
    }

    private WalScanResult replayWalFile(Path path, boolean repairTail) {
        Map<Integer, Long> walShardRevisions = new HashMap<>();
        long recordCount = 0L;
        long maxRevision = 0L;
        long repairOffset = -1L;
        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            long fileSize = Files.size(path);
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
                        LOGGER.error("Invalid ECO infinite storage WAL record length {} in {}", length, path);
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
                        LOGGER.error("CRC mismatch in ECO infinite storage WAL {}", path);
                    }
                    break;
                }
                List<WalRecord> records = decodeWalPayload(payload);
                for (WalRecord record : records) {
                    replayWalRecord(record);
                    recordCount++;
                    maxRevision = Math.max(maxRevision, record.revision());
                    walShardRevisions.merge(record.shard(), record.revision(), Math::max);
                }
            }
        } catch (RuntimeException | IOException e) {
            degraded = true;
            LOGGER.error("Unable to replay ECO infinite storage WAL {}", path, e);
        }
        if (!degraded && repairOffset >= 0L && repairTail) {
            repairWalTail(path, repairOffset);
        } else if (!degraded && repairOffset >= 0L) {
            degraded = true;
            LOGGER.error("Incomplete sealed ECO infinite storage WAL {}", path);
        }
        return new WalScanResult(recordCount, maxRevision, walShardRevisions);
    }

    private List<WalRecord> decodeWalPayload(byte[] payload) throws IOException {
        if (payload.length >= Integer.BYTES && readInt(payload, 0) == BINARY_WAL_MAGIC) {
            return List.of(decodeBinaryWalRecord(payload));
        }
        CompoundTag tag = NbtIo.readCompressed(
            new ByteArrayInputStream(payload),
            NbtAccounter.unlimitedHeap()
        );
        int version = tag.getInt("version");
        if (version == WAL_VERSION) {
            validateWalDomain(tag);
            ListTag entries = tag.getList("records", Tag.TAG_COMPOUND);
            List<WalRecord> records = new ArrayList<>(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                WalRecord record = decodeLegacyWalRecord(entries.getCompound(i));
                if (record != null) {
                    records.add(record);
                }
            }
            return records;
        }
        if (version == LEGACY_WAL_VERSION) {
            validateWalDomain(tag);
            WalRecord record = decodeLegacyWalRecord(tag);
            return record == null ? List.of() : List.of(record);
        }
        throw new IOException("Unsupported ECO infinite storage WAL version " + version);
    }

    private WalRecord decodeBinaryWalRecord(byte[] payload) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.readInt() != BINARY_WAL_MAGIC || in.readInt() != BINARY_WAL_VERSION) {
                throw new IOException("Unsupported ECO infinite storage binary WAL record");
            }
            UUID recordDomain = new UUID(in.readLong(), in.readLong());
            if (!domainId.equals(recordDomain)) {
                throw new IOException("ECO infinite storage WAL domain mismatch: " + recordDomain);
            }
            long recordRevision = in.readLong();
            UUID transactionId = in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null;
            int keyLength = in.readInt();
            if (keyLength <= 0 || keyLength > MAX_WAL_RECORD_BYTES || keyLength > in.available()) {
                throw new IOException("Invalid ECO infinite storage WAL key length " + keyLength);
            }
            byte[] keyBytes = in.readNBytes(keyLength);
            CompoundTag keyTag;
            try (DataInputStream keyIn = new DataInputStream(new ByteArrayInputStream(keyBytes))) {
                keyTag = NbtIo.read(keyIn, NbtAccounter.unlimitedHeap());
            }
            AEKey key = AEKey.fromTagGeneric(registries, keyTag);
            int deltaLength = in.readInt();
            if (deltaLength <= 0 || deltaLength > MAX_WAL_RECORD_BYTES || deltaLength > in.available()) {
                throw new IOException("Invalid ECO infinite storage WAL delta length " + deltaLength);
            }
            byte[] deltaBytes = in.readNBytes(deltaLength);
            if (in.available() != 0) {
                throw new IOException("Trailing bytes in ECO infinite storage WAL record");
            }
            if (key == null) {
                throw new IOException("Unknown AE key in ECO infinite storage WAL");
            }
            return new WalRecord(recordRevision, shardFor(key), key, new BigInteger(deltaBytes), transactionId);
        } catch (EOFException e) {
            throw new IOException("Truncated ECO infinite storage binary WAL record", e);
        }
    }

    @Nullable
    private WalRecord decodeLegacyWalRecord(CompoundTag tag) {
        AEKey key = AEKey.fromTagGeneric(registries, tag.getCompound("key"));
        if (key == null) {
            return null;
        }
        UUID transactionId = tag.hasUUID("transaction") ? tag.getUUID("transaction") : null;
        return new WalRecord(
            tag.getLong("revision"),
            shardFor(key),
            key,
            new BigInteger(tag.getString("delta")),
            transactionId
        );
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24
            | (bytes[offset + 1] & 0xff) << 16
            | (bytes[offset + 2] & 0xff) << 8
            | bytes[offset + 3] & 0xff;
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
            synchronized (walStateLock) {
                activeWalBytes = 0L;
                activeWalRecordCount = 0L;
                activeWalMaxRevision = 0L;
                activeWalShardRevisions.clear();
            }
        } catch (IOException e) {
            degraded = true;
            persistenceFailure = e;
            throw new IllegalStateException("Unable to checkpoint ECO infinite storage WAL", e);
        }
    }

    private void replayWalRecord(WalRecord record) {
        long shardCheckpointRevision = shardRevisions[record.shard()];
        long loadedKeyRevision = loadedKeyRevisions.getOrDefault(record.key(), 0L);
        if (!isWalRecordCovered(record.revision(), shardCheckpointRevision, loadedKeyRevision)) {
            applyDelta(record.key(), record.delta());
        }
        if (record.transactionId() != null) {
            committedTransactions.add(record.transactionId());
        }
        revision = Math.max(revision, record.revision());
    }

    static boolean isWalRecordCovered(
        long recordRevision,
        long shardCheckpointRevision,
        long loadedKeyRevision
    ) {
        return recordRevision <= Math.max(shardCheckpointRevision, loadedKeyRevision);
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

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private void repairWalTail(Path path, long validLength) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.truncate(validLength);
            channel.force(true);
            LOGGER.warn("Discarded incomplete ECO infinite storage WAL tail in {} at byte {}", path, validLength);
        } catch (IOException e) {
            degraded = true;
            LOGGER.error("Unable to repair ECO infinite storage WAL tail {}", path, e);
        }
    }

    private boolean hasSealedWalSegments() {
        synchronized (walStateLock) {
            return !sealedWalSegments.isEmpty();
        }
    }

    private boolean hasUncoveredWalSegments() {
        synchronized (walStateLock) {
            for (SealedWalSegment segment : sealedWalSegments) {
                if (!isWalSegmentCovered(segment)) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean isWalSegmentCovered(SealedWalSegment segment) {
        return isCheckpointBarrierCovered(segment.shardRevisions(), shardRevisions);
    }

    static boolean isCheckpointBarrierCovered(Map<Integer, Long> barrier, long[] checkpointRevisions) {
        for (Map.Entry<Integer, Long> entry : barrier.entrySet()) {
            if (checkpointRevisions[entry.getKey()] < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void reclaimCoveredWalSegments() {
        synchronized (walStateLock) {
            while (!sealedWalSegments.isEmpty()) {
                SealedWalSegment segment = sealedWalSegments.getFirst();
                if (!isWalSegmentCovered(segment)) {
                    return;
                }
                try {
                    Files.deleteIfExists(segment.path());
                    sealedWalSegments.removeFirst();
                } catch (IOException e) {
                    LOGGER.warn("Unable to remove checkpointed ECO infinite storage WAL segment {}", segment.path(), e);
                    return;
                }
            }
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

    private Path sealedWalPath(long barrierRevision) {
        return domainPath.resolve("wal_%020d.sealed".formatted(barrierRevision));
    }

    private List<Path> listSealedWalPaths() {
        try (var paths = Files.list(domainPath)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith("wal_") && name.endsWith(".sealed");
                })
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        } catch (IOException e) {
            degraded = true;
            LOGGER.error("Unable to list ECO infinite storage WAL segments in {}", domainPath, e);
            return List.of();
        }
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

    private record PendingWalDelta(long delta, long revision) {}

    private record WalRecord(
        long revision,
        int shard,
        AEKey key,
        BigInteger delta,
        @Nullable UUID transactionId
    ) {}

    private record WalScanResult(long recordCount, long maxRevision, Map<Integer, Long> shardRevisions) {}

    private record SealedWalSegment(Path path, long barrierRevision, Map<Integer, Long> shardRevisions) {}

    private static final class MutableTypeStats {
        private long storedTypes;
        private HugeAmount storedAmount = HugeAmount.ZERO;
    }
}
