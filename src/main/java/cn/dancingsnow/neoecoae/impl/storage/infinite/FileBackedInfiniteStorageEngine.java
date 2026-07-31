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
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.zip.CRC32;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frozen V1 format implementation. Runtime domains never instantiate this class; the strict loader is used only on
 * a disposable working copy by {@link LegacyV1Reader}.
 */
@Deprecated
final class FileBackedInfiniteStorageEngine implements ECOInfiniteStorageEngine {
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
    private static final String DOMAIN_MARKER_VERSION = "1";
    private static final ResourceLocation AE2_MISSING_CONTENT =
        ResourceLocation.fromNamespaceAndPath("ae2", "missing_content");
    private static final HugeAmount LONG_MAX_AMOUNT = HugeAmount.of(Long.MAX_VALUE);

    private final HolderLookup.Provider registries;
    private final UUID domainId;
    private final Path domainPath;
    private final Path shardsPath;
    private final Path walDirectory;
    private final Path transactionsPath;
    private final Path transactionLedgerPath;
    private final Path domainMarkerPath;
    private final Map<AEKey, HugeAmount> amounts = new HashMap<>();
    private final Map<String, OrphanedStack> orphanedEntries = new HashMap<>();
    private final Map<AEKey, Integer> keyShards = new HashMap<>();
    private final List<Set<AEKey>> keysByShard = createShardKeySets();
    private final KeyCounter visibleStacks = new KeyCounter();
    private final Map<AEKeyType, MutableTypeStats> typeStats = new HashMap<>();
    private final Map<AEKey, HugeAmount> hugeStacks = new HashMap<>();
    private final Map<AEKey, PendingWalDelta> pendingWalDeltas = new HashMap<>();
    private final List<WalRecord> stagedWalRecords = new ArrayList<>();
    private final Set<Integer> dirtyShards = new HashSet<>();
    private final Set<Integer> loggedCheckpointFailures = ConcurrentHashMap.newKeySet();
    private final Map<Integer, CheckpointWrite> checkpointWrites = new HashMap<>();
    private final Object walStateLock = new Object();
    private final List<SealedWalSegment> sealedWalSegments = new ArrayList<>();
    private final Map<Integer, Long> activeWalShardRevisions = new HashMap<>();
    private final Set<UUID> committedTransactions = new HashSet<>();
    private final Set<UUID> persistedTransactions = new HashSet<>();
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
    private volatile boolean directoryRecoveryRequired;
    private boolean recoveringDirectory;

    // Async-load state — written only by beginAsyncLoad/applyLoadResult, read by poll/isLoaded
    private volatile boolean loaded = false;
    private volatile boolean loadJustCompleted = false;
    @Nullable private volatile Future<LoadResult> pendingLoad = null;

    @Nullable private DataOutputStream walOut;
    @Nullable private FileChannel walChannel;
    @Nullable private Path activeWalPath;
    @Nullable private Future<?> pendingWalWrite;
    private long nextWalSegmentId;
    private long activeWalBytes;
    private long activeWalRecordCount;
    private long activeWalMaxRevision;

    private FileBackedInfiniteStorageEngine(HolderLookup.Provider registries, UUID domainId, Path domainPath) {
        this.registries = registries;
        this.domainId = domainId;
        this.domainPath = domainPath.toAbsolutePath().normalize();
        this.shardsPath = this.domainPath.resolve("shards");
        this.walDirectory = this.domainPath.resolve("wal");
        this.transactionsPath = this.domainPath.resolve("txn");
        this.transactionLedgerPath = this.transactionsPath.resolve("receipts.log");
        this.domainMarkerPath = this.domainPath.resolve("domain.meta");
        // load() is no longer called here — beginAsyncLoad() is called by ECOInfiniteStorageDomains.get()
    }

    /**
     * Submits the initial disk load to the dedicated load executor. Must be called once immediately
     * after construction, on any thread. The engine is unusable (isLoaded() == false) until the
     * load completes and applyLoadResult() has been called on the server thread.
     */
    void beginAsyncLoad() {
        pendingLoad = ECOInfiniteStorageIoWorker.submitLoad(
            () -> computeLoadResult(registries, domainId, domainPath, shardsPath, walDirectory,
                transactionsPath, transactionLedgerPath, domainMarkerPath));
    }

    /**
     * Must be called on the server thread. Checks whether the async load has completed; if so,
     * applies the result to this engine and returns true. Returns false if loading is still in
     * progress.
     */
    synchronized boolean applyPendingLoad() {
        Future<LoadResult> future = pendingLoad;
        if (future == null) {
            return loaded;
        }
        if (!future.isDone()) {
            return false;
        }
        pendingLoad = null;
        LoadResult result;
        try {
            result = future.get();
        } catch (java.util.concurrent.ExecutionException e) {
            degraded = true;
            persistenceFailure = e.getCause() != null ? e.getCause() : e;
            LOGGER.error("ECO infinite storage domain {} failed to load; the domain is degraded", domainId, e);
            loaded = true;
            loadJustCompleted = true;
            return true;
        } catch (java.util.concurrent.CancellationException e) {
            degraded = true;
            persistenceFailure = e;
            LOGGER.error("ECO infinite storage domain {} load was cancelled", domainId, e);
            loaded = true;
            loadJustCompleted = true;
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            degraded = true;
            persistenceFailure = e;
            LOGGER.error("Interrupted while loading ECO infinite storage domain {}", domainId, e);
            loaded = true;
            loadJustCompleted = true;
            return true;
        }
        applyLoadResult(result);
        return true;
    }

    /** Applies a LoadResult computed by computeLoadResult(). Must run on the server thread. */
    private void applyLoadResult(LoadResult result) {
        amounts.clear();
        amounts.putAll(result.amounts());
        orphanedEntries.clear();
        orphanedEntries.putAll(copyOrphanedEntries(result.orphanedEntries()));
        revision = result.revision();
        System.arraycopy(result.shardRevisions(), 0, shardRevisions, 0, SHARD_COUNT);
        dirtyShards.clear();
        dirtyShards.addAll(result.dirtyShards());
        for (int shard : dirtyShards) {
            shardMutationRevisions[shard] = revision;
        }
        committedTransactions.clear();
        committedTransactions.addAll(result.committedTransactions());
        persistedTransactions.clear();
        persistedTransactions.addAll(result.persistedTransactions());
        synchronized (walStateLock) {
            sealedWalSegments.clear();
            sealedWalSegments.addAll(result.sealedWalSegments());
        }
        nextWalSegmentId = result.nextWalSegmentId();
        if (result.degraded()) {
            degraded = true;
            if (result.persistenceFailure() != null) {
                persistenceFailure = result.persistenceFailure();
            }
        }
        rebuildIndexes();
        loaded = true;
        loadJustCompleted = true;
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public boolean tickLoad() {
        applyPendingLoad();
        return pollLoadComplete();
    }

    boolean pollLoadComplete() {
        if (!loadJustCompleted) {
            return false;
        }
        loadJustCompleted = false;
        return true;
    }

    UUID domainId() {
        return domainId;
    }

    @Override
    public synchronized long insert(AEKey key, long amount, Actionable mode) {
        if (!loaded || key == null || amount <= 0L) {
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
        // Block until loaded: insertOnce is only called during migration which requires committed
        // state; a brief server-thread wait here is acceptable and ensures no migration step is lost.
        awaitLoadedBlocking();
        if (!loaded || transactionId == null || key == null || amount <= 0L || degraded) {
            return 0L;
        }
        if (committedTransactions.contains(transactionId)) {
            return amount;
        }
        if (Files.isRegularFile(transactionReceipt(transactionId))) {
            committedTransactions.add(transactionId);
            writeTransactionReceipt(transactionId);
            return amount;
        }
        try {
            submitWalRecords(drainPendingWalRecords());
            awaitPendingWal();
            applyDelta(key, amount, false);
            submitWalRecords(List.of(createWalRecord(key, BigInteger.valueOf(amount), transactionId, revision)));
            awaitPendingWal();
            committedTransactions.add(transactionId);
            writeTransactionReceipt(transactionId);
            return amount;
        } catch (RuntimeException e) {
            degraded = true;
            if (persistenceFailure == null) {
                persistenceFailure = e;
            }
            LOGGER.error("Unable to commit ECO infinite storage transaction {} in domain {}; the domain is read-only",
                transactionId, domainId, e);
            return 0L;
        }
    }

    @Override
    public synchronized long extract(AEKey key, long amount, Actionable mode) {
        if (!loaded || key == null || amount <= 0L) {
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

    /**
     * Releases this object's monitor and spins in 50 ms sleeps until the async load finishes.
     * Must be called from a synchronized context (the monitor is re-acquired before each check).
     * If the future is already done when we wake up, we apply it inline so no external caller is
     * needed — this handles the case where migration triggers insertOnce before the first tick
     * has had a chance to call applyPendingLoad().
     */
    private void awaitLoadedBlocking() {
        while (!loaded) {
            Future<LoadResult> future = pendingLoad;
            if (future != null && future.isDone()) {
                pendingLoad = null;
                LoadResult result;
                try {
                    result = future.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    degraded = true;
                    persistenceFailure = e.getCause() != null ? e.getCause() : e;
                    LOGGER.error("ECO infinite storage domain {} failed to load", domainId, e);
                    loaded = true;
                    loadJustCompleted = true;
                    return;
                } catch (java.util.concurrent.CancellationException e) {
                    degraded = true;
                    persistenceFailure = e;
                    LOGGER.error("ECO infinite storage domain {} load was cancelled", domainId, e);
                    loaded = true;
                    loadJustCompleted = true;
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                applyLoadResult(result);
                return;
            }
            try {
                this.wait(50); // releases the monitor so the IO thread can finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public synchronized HugeAmount getAmount(AEKey key) {
        if (!loaded) return HugeAmount.ZERO;
        HugeAmount amount = amounts.get(key);
        return amount == null ? HugeAmount.ZERO : amount;
    }

    @Override
    public synchronized void getAvailableStacks(KeyCounter out) {
        if (loaded) {
            out.addAll(visibleStacks);
        }
    }

    @Override
    public synchronized long getRevision() {
        return loaded ? revision : 0L;
    }

    @Override
    public synchronized boolean isEmpty() {
        return !loaded || amounts.isEmpty() && orphanedEntries.isEmpty();
    }

    @Override
    public synchronized boolean isHealthy() {
        return !degraded;
    }

    @Override
    public synchronized HugeAmount getStoredAmount() {
        return loaded ? storedAmount.add(getOrphanedAmount()) : HugeAmount.ZERO;
    }

    @Override
    public synchronized int getStoredTypes() {
        return loaded ? amounts.size() : 0;
    }

    @Override
    public synchronized Collection<TypeStats> getTypeStats() {
        if (!loaded) return List.of();
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
        if (!loaded) return List.of();
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
    public synchronized Collection<OrphanedStack> getOrphanedStacks() {
        return loaded ? List.copyOf(orphanedEntries.values()) : List.of();
    }

    @Override
    public synchronized int getOrphanedTypes() {
        return loaded ? orphanedEntries.size() : 0;
    }

    @Override
    public synchronized HugeAmount getOrphanedAmount() {
        if (!loaded) {
            return HugeAmount.ZERO;
        }
        HugeAmount total = HugeAmount.ZERO;
        for (OrphanedStack entry : orphanedEntries.values()) {
            total = total.add(entry.amount());
        }
        return total;
    }

    @Override
    public synchronized void flushBudgeted(long maxNanos) {
        if (!loaded) return;
        try {
            submitPendingWal();
            checkpointBudgeted(maxNanos);
        } catch (RuntimeException e) {
            degraded = true;
            if (persistenceFailure == null) {
                persistenceFailure = e;
            }
            LOGGER.error("Unable to flush ECO infinite storage domain {}; the domain is read-only", domainId, e);
        }
    }

    synchronized void checkpointBudgeted(long maxNanos) {
        if (!loaded) {
            return;
        }
        recoverMissingStorageIfNeeded();
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
        if (!loaded) {
            return;
        }
        if (pendingWalWrite != null) {
            awaitPendingWal();
        }
        recoverMissingStorageIfNeeded();
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
            int dirtyBefore = dirtyShards.size();
            completeCheckpointWrites(false);
            Set<Integer> pending = forceAll ? new HashSet<>(dirtyShards) : checkpointCandidates();
            for (int shard : pending) {
                scheduleCheckpoint(shard);
                if (System.nanoTime() >= deadline) {
                    break;
                }
            }
            completeCheckpointWrites(waitForAll);
            if (waitForAll && checkpointWrites.isEmpty() && dirtyShards.size() >= dirtyBefore) {
                break;
            }
        } while (waitForAll && !dirtyShards.isEmpty());
        reclaimCoveredWalSegments();
        if (dirtyShards.isEmpty() && checkpointWrites.isEmpty() && !hasSealedWalSegments()) {
            awaitPendingWal();
            retireCheckpointedActiveWal();
            reclaimCoveredWalSegments();
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
        CompoundTag snapshot;
        try {
            snapshot = createShardSnapshot(shard, snapshotRevision);
        } catch (RuntimeException e) {
            degraded = true;
            persistenceFailure = e;
            throw new IllegalStateException("Unable to create ECO infinite storage shard snapshot " + shard, e);
        }
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
            boolean completed = awaitPersistenceTask(write.future(), "checkpoint shard " + shard);
            checkpointWrites.remove(shard);
            if (!completed) {
                continue;
            }
            shardRevisions[shard] = write.revision();
            lastCheckpointNanos = System.nanoTime();
            if (shardMutationRevisions[shard] == write.revision()) {
                dirtyShards.remove(shard);
            }
        }
    }

    @Override
    public synchronized void closeAndFlush() {
        if (!loaded) {
            // Cancel the pending load — there is nothing on the write path yet, so no flush is needed.
            Future<LoadResult> future = pendingLoad;
            if (future != null) {
                future.cancel(false);
                pendingLoad = null;
            }
            return;
        }
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
        if (revision == Long.MAX_VALUE) {
            degraded = true;
            throw new IllegalStateException("ECO infinite storage revision space is exhausted");
        }
        long nextRevision = revision + 1L;
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
        revision = nextRevision;
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
        if (!loaded) {
            return;
        }
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
                if (e.getCause() instanceof StorageDirectoryMissingException) {
                    directoryRecoveryRequired = true;
                    pendingWalWrite = null;
                } else {
                    throw persistenceException(e.getCause());
                }
            }
        }
        recoverMissingStorageIfNeeded();
        throwIfPersistenceFailed();
    }

    private boolean awaitPersistenceTask(Future<?> task, String operation) {
        try {
            task.get();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while persisting ECO infinite storage " + operation, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof StorageDirectoryMissingException) {
                directoryRecoveryRequired = true;
                return false;
            }
            if (cause instanceof CheckpointRetryException) {
                return false;
            }
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

    /**
     * Runs entirely on the IO thread. Reads shards, replays WAL, and returns a LoadResult
     * that will be applied to this engine on the server thread by applyLoadResult().
     */
    private static LoadResult computeLoadResult(
        HolderLookup.Provider registries,
        UUID domainId,
        Path domainPath,
        Path shardsPath,
        Path walDirectory,
        Path transactionsPath,
        Path transactionLedgerPath,
        Path domainMarkerPath
    ) {
        return new Loader(registries, domainId, domainPath, shardsPath, walDirectory,
            transactionsPath, transactionLedgerPath, domainMarkerPath, false).compute();
    }

    static LegacyV1Snapshot readMigrationSnapshot(
        HolderLookup.Provider registries,
        UUID domainId,
        Path copiedDomainPath
    ) {
        Path transactions = copiedDomainPath.resolve("txn");
        LoadResult result = new Loader(
            registries,
            domainId,
            copiedDomainPath,
            copiedDomainPath.resolve("shards"),
            copiedDomainPath.resolve("wal"),
            transactions,
            transactions.resolve("receipts.log"),
            copiedDomainPath.resolve("domain.meta"),
            true
        ).compute();
        if (result.degraded()) {
            throw new IllegalStateException(
                "V1 infinite-storage data could not be read without loss",
                result.persistenceFailure()
            );
        }
        Set<UUID> receipts = new HashSet<>(result.committedTransactions());
        receipts.addAll(result.persistedTransactions());
        return new LegacyV1Snapshot(
            Map.copyOf(result.amounts()),
            copyOrphanedEntries(result.orphanedEntries()),
            Set.copyOf(receipts),
            result.revision()
        );
    }

    record LegacyV1Snapshot(
        Map<AEKey, HugeAmount> amounts,
        Map<String, OrphanedStack> orphanedEntries,
        Set<UUID> receipts,
        long revision
    ) {
    }

    /** Immutable snapshot of loaded state; applied to the engine on the server thread. */
    private record LoadResult(
        Map<AEKey, HugeAmount> amounts,
        Map<String, OrphanedStack> orphanedEntries,
        long revision,
        long[] shardRevisions,
        Set<Integer> dirtyShards,
        Set<UUID> committedTransactions,
        Set<UUID> persistedTransactions,
        List<SealedWalSegment> sealedWalSegments,
        long nextWalSegmentId,
        boolean degraded,
        @Nullable Throwable persistenceFailure
    ) {}

    /** Performs all disk I/O on the background IO thread and produces a LoadResult. */
    private static final class Loader {
        private final HolderLookup.Provider registries;
        private final UUID domainId;
        private final Path domainPath;
        private final Path shardsPath;
        private final Path walDirectory;
        private final Path transactionsPath;
        private final Path transactionLedgerPath;
        private final Path domainMarkerPath;
        private final boolean strictMigration;
        private final Map<AEKey, HugeAmount> amounts = new HashMap<>();
        private final Map<String, OrphanedStack> orphanedEntries = new HashMap<>();
        private final Map<AEKey, Long> loadedKeyRevisions = new HashMap<>();
        private final Map<AEKey, Integer> loadedKeySourceShards = new HashMap<>();
        private final Map<String, Long> loadedOrphanedKeyRevisions = new HashMap<>();
        private final Map<String, Integer> loadedOrphanedKeySourceShards = new HashMap<>();
        private final long[] shardRevisions = new long[SHARD_COUNT];
        private final Set<Integer> dirtyShards = new HashSet<>();
        private final Set<UUID> committedTransactions = new HashSet<>();
        private final Set<UUID> persistedTransactions = new HashSet<>();
        private final Map<UUID, LegacyTransactionRecord> migrationTransactions = new HashMap<>();
        private final List<SealedWalSegment> sealedWalSegments = new ArrayList<>();
        private long revision = 0L;
        private boolean degraded = false;
        @Nullable private Throwable persistenceFailure = null;
        private long nextWalSegmentId = 0L;

        Loader(HolderLookup.Provider registries, UUID domainId, Path domainPath,
               Path shardsPath, Path walDirectory, Path transactionsPath,
               Path transactionLedgerPath, Path domainMarkerPath, boolean strictMigration) {
            this.registries = registries;
            this.domainId = domainId;
            this.domainPath = domainPath;
            this.shardsPath = shardsPath;
            this.walDirectory = walDirectory;
            this.transactionsPath = transactionsPath;
            this.transactionLedgerPath = transactionLedgerPath;
            this.domainMarkerPath = domainMarkerPath;
            this.strictMigration = strictMigration;
        }
        // LOADER_BODY_PLACEHOLDER

        LoadResult compute() {
            try { prepareDomainLayout(); } catch (IOException e) {
                degraded = true; persistenceFailure = e;
                LOGGER.error("Unable to prepare ECO infinite domain directory {}", domainPath, e);
                return toLoadResult();
            }
            try { loadTransactionReceipts(); } catch (IOException e) {
                degraded = true; persistenceFailure = e;
                LOGGER.error("Unable to load ECO infinite storage transaction receipts {}", transactionLedgerPath, e);
                return toLoadResult();
            }
            for (int shard = 0; shard < SHARD_COUNT; shard++) { readShard(shard); }
            if (degraded) {
                loadedKeyRevisions.clear(); loadedKeySourceShards.clear(); dirtyShards.clear();
                return toLoadResult();
            }
            Map<AEKey, HugeAmount> checkpointAmounts = new HashMap<>(amounts);
            Map<String, OrphanedStack> checkpointOrphanedEntries = copyOrphanedEntries(orphanedEntries);
            replayWal();
            if (degraded) {
                amounts.clear(); amounts.putAll(checkpointAmounts); dirtyShards.clear();
                orphanedEntries.clear(); orphanedEntries.putAll(checkpointOrphanedEntries);
                return toLoadResult();
            }
            Set<Integer> recoveredDirtyShards = new HashSet<>(dirtyShards);
            loadedKeyRevisions.clear(); loadedKeySourceShards.clear();
            dirtyShards.clear(); dirtyShards.addAll(recoveredDirtyShards);
            try { writeDomainMarker(domainId, domainPath, domainMarkerPath); } catch (IOException e) {
                degraded = true; persistenceFailure = e;
                LOGGER.error("Unable to persist ECO infinite storage domain marker {}", domainMarkerPath, e);
            }
            return toLoadResult();
        }

        private LoadResult toLoadResult() {
            return new LoadResult(new HashMap<>(amounts), copyOrphanedEntries(orphanedEntries), revision, shardRevisions.clone(),
                new HashSet<>(dirtyShards), new HashSet<>(committedTransactions),
                new HashSet<>(persistedTransactions), new ArrayList<>(sealedWalSegments),
                nextWalSegmentId, degraded, persistenceFailure);
        }

        private void prepareDomainLayout() throws IOException {
            Files.createDirectories(domainPath); Files.createDirectories(shardsPath);
            Files.createDirectories(walDirectory);
            migrateFiles(domainPath, shardsPath, n -> n.startsWith("shard_") && n.endsWith(".dat"));
            migrateFiles(domainPath, walDirectory,
                n -> n.equals("wal_000.log") || n.startsWith("wal_") && n.endsWith(".sealed"));
            Path legacyTxn = domainPath.resolve("transactions");
            if (Files.isDirectory(legacyTxn) && !Files.exists(transactionsPath)) {
                Files.move(legacyTxn, transactionsPath);
            } else {
                Files.createDirectories(transactionsPath);
                if (Files.isDirectory(legacyTxn)) migrateFiles(legacyTxn, transactionsPath, n -> n.endsWith(".done"));
            }
            nextWalSegmentId = findNextWalSegmentId(walDirectory);
            if (Files.isRegularFile(domainMarkerPath)) { validateDomainMarker(); }
            else { writeDomainMarker(domainId, domainPath, domainMarkerPath); }
        }
        // LOADER_METHODS_PLACEHOLDER

        private void validateDomainMarker() throws IOException {
            List<String> lines = Files.readAllLines(domainMarkerPath, StandardCharsets.US_ASCII);
            if (lines.size() < 2 || !DOMAIN_MARKER_VERSION.equals(lines.get(0).trim())
                    || !domainId.toString().equals(lines.get(1).trim()))
                throw new IOException("Invalid ECO infinite storage domain marker " + domainMarkerPath);
        }

        private void loadTransactionReceipts() throws IOException {
            if (Files.isRegularFile(transactionLedgerPath)) {
                try (var r = Files.newBufferedReader(transactionLedgerPath, StandardCharsets.US_ASCII)) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.isBlank()) continue;
                        try { UUID id = UUID.fromString(line.trim()); committedTransactions.add(id); persistedTransactions.add(id); }
                        catch (IllegalArgumentException e) { throw new IOException("Invalid transaction id in " + transactionLedgerPath, e); }
                    }
                }
            }
            List<Path> legacy;
            try (var paths = Files.list(transactionsPath)) {
                legacy = paths.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".done")).toList();
            }
            for (Path r : legacy) {
                String n = r.getFileName().toString();
                try { committedTransactions.add(UUID.fromString(n.substring(0, n.length() - 5))); }
                catch (IllegalArgumentException e) { throw new IOException("Invalid ECO infinite storage transaction receipt " + r, e); }
            }
            appendUnpersistedTransactions(transactionLedgerPath, transactionsPath, committedTransactions, persistedTransactions);
            for (Path r : legacy) { try { Files.deleteIfExists(r); } catch (IOException e) { LOGGER.warn("Unable to remove migrated ECO infinite storage transaction receipt {}", r, e); } }
        }

        private void readShard(int shard) {
            Path path = shardsPath.resolve(shardFileName(shard));
            if (!Files.isRegularFile(path)) return;
            try (var input = Files.newInputStream(path)) {
                CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
                if (!domainId.toString().equals(tag.getString("domain"))) throw new IOException("ECO infinite storage shard domain mismatch in " + path);
                long shardRev = tag.getLong("revision");
                if (strictMigration && shardRev < 0L) {
                    throw new IOException("Negative shard revision in " + path);
                }
                int hashVer = tag.getInt(ECOStorageKeyHash.SHARD_HASH_VERSION_TAG);
                ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);
                for (int i = 0; i < entries.size(); i++) {
                    CompoundTag e = entries.getCompound(i);
                    CompoundTag encodedKey = e.getCompound("key");
                    AEKey key = AEKey.fromTagGeneric(registries, encodedKey);
                    HugeAmount amt = HugeAmount.read(e.getCompound("amount"));
                    if (strictMigration && amt.isZero()) {
                        throw new IOException("Zero amount in ECO infinite storage shard " + path);
                    }
                    if (key == null) {
                        if (strictMigration) {
                            throw new IOException("Undecodable AE key in ECO infinite storage shard " + path);
                        }
                        continue;
                    }
                    if (!isResolvedLegacyKey(key)) {
                        if (!amt.isZero()) {
                            putOrphanedEntry(encodedKey, amt, shardRev, shard, hashVer);
                        }
                        continue;
                    }
                    if (!amt.isZero()) {
                        int targetShard = shardFor(key);
                        if (targetShard != shard || hashVer < ECOStorageKeyHash.VERSION) { dirtyShards.add(shard); dirtyShards.add(targetShard); }
                        Long prevRev = loadedKeyRevisions.get(key);
                        int prevSrc = loadedKeySourceShards.getOrDefault(key, -1);
                        boolean curIsTarget = targetShard == shard, prevIsTarget = targetShard == prevSrc;
                        if (prevRev == null || (curIsTarget && !prevIsTarget) || (curIsTarget == prevIsTarget && shardRev > prevRev)) {
                            amounts.put(key, amt); loadedKeyRevisions.put(key, shardRev); loadedKeySourceShards.put(key, shard);
                        }
                    }
                }
                revision = Math.max(revision, shardRev); shardRevisions[shard] = shardRev;
            } catch (RuntimeException | IOException e) {
                degraded = true;
                persistenceFailure = e;
                LOGGER.error("Unable to read ECO infinite storage shard {}", path, e);
            }
        }

        private int shardFor(AEKey key) { return ECOStorageKeyHash.shardFor(registries, key, SHARD_COUNT); }

        private void putOrphanedEntry(
            CompoundTag encodedKey,
            HugeAmount amount,
            long shardRevision,
            int sourceShard,
            int hashVersion
        ) throws IOException {
            String fingerprint = ECOStorageKeyHash.stableFingerprint(encodedKey);
            int targetShard = ECOStorageKeyHash.shardForEncodedKey(encodedKey, SHARD_COUNT);
            if (targetShard != sourceShard || hashVersion < ECOStorageKeyHash.VERSION) {
                dirtyShards.add(sourceShard);
                dirtyShards.add(targetShard);
            }
            Long previousRevision = loadedOrphanedKeyRevisions.get(fingerprint);
            int previousSource = loadedOrphanedKeySourceShards.getOrDefault(fingerprint, -1);
            boolean currentIsTarget = targetShard == sourceShard;
            boolean previousIsTarget = targetShard == previousSource;
            if (previousRevision == null
                    || currentIsTarget && !previousIsTarget
                    || currentIsTarget == previousIsTarget && shardRevision > previousRevision) {
                orphanedEntries.put(fingerprint, new OrphanedStack(encodedKey.copy(), amount));
                loadedOrphanedKeyRevisions.put(fingerprint, shardRevision);
                loadedOrphanedKeySourceShards.put(fingerprint, sourceShard);
            }
        }
        // LOADER_WAL_PLACEHOLDER

        private void replayWal() {
            List<Path> walPaths;
            try { walPaths = listWalPathsChecked(walDirectory); }
            catch (IOException e) { degraded = true; LOGGER.error("Unable to list ECO infinite storage WAL segments in {}", walDirectory, e); return; }
            for (Path walPath : walPaths) {
                WalScanResult result = replayWalFile(walPath, walPath.equals(walPaths.getLast()));
                if (degraded) return;
                if (result.recordCount() == 0L) { try { Files.deleteIfExists(walPath); } catch (IOException e) { LOGGER.warn("Unable to remove empty ECO infinite storage WAL segment {}", walPath, e); } }
                else { sealedWalSegments.add(new SealedWalSegment(walPath, result.maxRevision(), Map.copyOf(result.shardRevisions()))); }
            }
            try { appendUnpersistedTransactions(transactionLedgerPath, transactionsPath, committedTransactions, persistedTransactions); }
            catch (IOException e) { degraded = true; persistenceFailure = e; LOGGER.error("Unable to persist replayed ECO infinite storage transaction receipts", e); }
        }

        private WalScanResult replayWalFile(Path path, boolean repairTail) {
            Map<Integer, Long> walShardRevisions = new HashMap<>();
            long recordCount = 0L, maxRevision = 0L, repairOffset = -1L;
            try (var in = new DataInputStream(Files.newInputStream(path))) {
                long fileSize = Files.size(path), offset = 0L;
                while (offset < fileSize) {
                    long recordStart = offset;
                    if (fileSize - offset < Integer.BYTES * 2L) { repairOffset = recordStart; break; }
                    int length = in.readInt(), expectedCrc = in.readInt();
                    offset += Integer.BYTES * 2L;
                    if (length <= 0 || length > MAX_WAL_RECORD_BYTES) {
                        if (offset == fileSize) repairOffset = recordStart;
                        else { degraded = true; LOGGER.error("Invalid ECO infinite storage WAL record length {} in {}", length, path); }
                        break;
                    }
                    if (fileSize - offset < length) { repairOffset = recordStart; break; }
                    byte[] payload = new byte[length]; in.readFully(payload); offset += length;
                    CRC32 crc = new CRC32(); crc.update(payload);
                    if ((int) crc.getValue() != expectedCrc) {
                        if (!strictMigration && offset == fileSize) repairOffset = recordStart;
                        else { degraded = true; LOGGER.error("CRC mismatch in ECO infinite storage WAL {}", path); }
                        break;
                    }
                    for (WalRecord r : decodeWalPayload(payload)) {
                        replayWalRecord(r); recordCount++;
                        maxRevision = Math.max(maxRevision, r.revision());
                        walShardRevisions.merge(r.shard(), r.revision(), Math::max);
                    }
                }
            } catch (RuntimeException | IOException e) {
                degraded = true;
                persistenceFailure = e;
                LOGGER.error("Unable to replay ECO infinite storage WAL {}", path, e);
            }
            if (!degraded && repairOffset >= 0L && repairTail) repairWalTail(path, repairOffset);
            else if (!degraded && repairOffset >= 0L) { degraded = true; LOGGER.error("Incomplete sealed ECO infinite storage WAL {}", path); }
            return new WalScanResult(recordCount, maxRevision, walShardRevisions);
        }
        // LOADER_DECODE_PLACEHOLDER

        private List<WalRecord> decodeWalPayload(byte[] payload) throws IOException {
            if (payload.length >= Integer.BYTES && readInt(payload, 0) == BINARY_WAL_MAGIC)
                return List.of(decodeBinaryWalRecord(payload));
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(payload), NbtAccounter.unlimitedHeap());
            int version = tag.getInt("version");
            if (version == WAL_VERSION) {
                validateWalDomain(tag);
                ListTag entries = tag.getList("records", Tag.TAG_COMPOUND);
                List<WalRecord> records = new ArrayList<>(entries.size());
                for (int i = 0; i < entries.size(); i++) { WalRecord r = decodeLegacyWalRecord(entries.getCompound(i)); if (r != null) records.add(r); }
                return records;
            }
            if (version == LEGACY_WAL_VERSION) { validateWalDomain(tag); WalRecord r = decodeLegacyWalRecord(tag); return r == null ? List.of() : List.of(r); }
            throw new IOException("Unsupported ECO infinite storage WAL version " + version);
        }

        private WalRecord decodeBinaryWalRecord(byte[] payload) throws IOException {
            try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
                if (in.readInt() != BINARY_WAL_MAGIC || in.readInt() != BINARY_WAL_VERSION)
                    throw new IOException("Unsupported ECO infinite storage binary WAL record");
                UUID recordDomain = new UUID(in.readLong(), in.readLong());
                if (!domainId.equals(recordDomain)) throw new IOException("ECO infinite storage WAL domain mismatch: " + recordDomain);
                long recordRevision = in.readLong();
                if (strictMigration && recordRevision < 0L) {
                    throw new IOException("Negative revision in ECO infinite storage WAL");
                }
                UUID txId = in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null;
                int keyLen = in.readInt();
                if (keyLen <= 0 || keyLen > MAX_WAL_RECORD_BYTES || keyLen > in.available()) throw new IOException("Invalid ECO infinite storage WAL key length " + keyLen);
                byte[] keyBytes = in.readNBytes(keyLen);
                CompoundTag keyTag; try (var ki = new DataInputStream(new ByteArrayInputStream(keyBytes))) { keyTag = NbtIo.read(ki, NbtAccounter.unlimitedHeap()); }
                AEKey key = AEKey.fromTagGeneric(registries, keyTag);
                if (key == null) {
                    throw new IOException("Undecodable AE key in ECO infinite storage WAL");
                }
                int deltaLen = in.readInt();
                if (deltaLen <= 0 || deltaLen > MAX_WAL_RECORD_BYTES || deltaLen > in.available()) throw new IOException("Invalid ECO infinite storage WAL delta length " + deltaLen);
                byte[] deltaBytes = in.readNBytes(deltaLen);
                if (in.available() != 0) throw new IOException("Trailing bytes in ECO infinite storage WAL record");
                BigInteger delta = new BigInteger(deltaBytes);
                if (strictMigration && delta.signum() == 0) {
                    throw new IOException("Zero delta in ECO infinite storage WAL");
                }
                int shard = isResolvedLegacyKey(key)
                    ? shardFor(key)
                    : ECOStorageKeyHash.shardForEncodedKey(keyTag, SHARD_COUNT);
                return new WalRecord(recordRevision, shard, key, keyTag.copy(), delta, txId);
            } catch (EOFException e) { throw new IOException("Truncated ECO infinite storage binary WAL record", e); }
        }

        @Nullable private WalRecord decodeLegacyWalRecord(CompoundTag tag) throws IOException {
            CompoundTag encodedKey = tag.getCompound("key");
            AEKey key = AEKey.fromTagGeneric(registries, encodedKey);
            if (key == null) {
                if (strictMigration) {
                    throw new IOException("Undecodable AE key in legacy ECO infinite storage WAL");
                }
                return null;
            }
            UUID txId = tag.hasUUID("transaction") ? tag.getUUID("transaction") : null;
            long recordRevision = tag.getLong("revision");
            if (strictMigration && recordRevision < 0L) {
                throw new IOException("Negative revision in legacy ECO infinite storage WAL");
            }
            BigInteger delta = new BigInteger(tag.getString("delta"));
            if (strictMigration && delta.signum() == 0) {
                throw new IOException("Zero delta in legacy ECO infinite storage WAL");
            }
            int shard = isResolvedLegacyKey(key)
                ? shardFor(key)
                : ECOStorageKeyHash.shardForEncodedKey(encodedKey, SHARD_COUNT);
            return new WalRecord(recordRevision, shard, key, encodedKey.copy(), delta, txId);
        }

        private void validateWalDomain(CompoundTag tag) throws IOException {
            String d = tag.getString("domain");
            if (!domainId.toString().equals(d)) throw new IOException("ECO infinite storage WAL domain mismatch: " + d);
        }

        private void replayWalRecord(WalRecord record) throws IOException {
            if (strictMigration && record.transactionId() != null) {
                LegacyTransactionRecord current = new LegacyTransactionRecord(
                    ECOStorageKeyHash.stableFingerprint(record.encodedKey()),
                    record.delta()
                );
                LegacyTransactionRecord previous = migrationTransactions.putIfAbsent(record.transactionId(), current);
                if (previous != null) {
                    if (!previous.equals(current)) {
                        throw new IOException(
                            "V1 transaction ID has conflicting contents: " + record.transactionId()
                        );
                    }
                    committedTransactions.add(record.transactionId());
                    revision = Math.max(revision, record.revision());
                    return;
                }
            }
            long loadedKeyRevision = isResolvedLegacyKey(record.key())
                ? loadedKeyRevisions.getOrDefault(record.key(), 0L)
                : loadedOrphanedKeyRevisions.getOrDefault(
                    ECOStorageKeyHash.stableFingerprint(record.encodedKey()),
                    0L
                );
            if (!isWalRecordCovered(record.revision(), shardRevisions[record.shard()], loadedKeyRevision)) {
                applyDelta(record);
            }
            if (record.transactionId() != null) committedTransactions.add(record.transactionId());
            revision = Math.max(revision, record.revision());
        }

        private record LegacyTransactionRecord(String keyFingerprint, BigInteger delta) {
        }

        private void applyDelta(WalRecord record) throws IOException {
            if (!isResolvedLegacyKey(record.key())) {
                applyOrphanedDelta(record.encodedKey(), record.delta(), record.shard());
                return;
            }
            applyResolvedDelta(record.key(), record.delta());
        }

        private void applyResolvedDelta(AEKey key, BigInteger delta) throws IOException {
            if (delta.signum() == 0) return;
            HugeAmount current = amounts.getOrDefault(key, HugeAmount.ZERO);
            BigInteger rawNext = current.toBigInteger().add(delta);
            if (strictMigration && rawNext.signum() < 0) {
                throw new IOException("V1 WAL would make an infinite-storage amount negative");
            }
            HugeAmount next = HugeAmount.of(rawNext.max(BigInteger.ZERO));
            if (next.isZero()) amounts.remove(key); else amounts.put(key, next);
            if (next.compareTo(current) != 0) dirtyShards.add(shardFor(key));
        }

        private void applyOrphanedDelta(CompoundTag encodedKey, BigInteger delta, int shard) throws IOException {
            if (delta.signum() == 0) {
                return;
            }
            String fingerprint = ECOStorageKeyHash.stableFingerprint(encodedKey);
            HugeAmount current = orphanedEntries.containsKey(fingerprint)
                ? orphanedEntries.get(fingerprint).amount()
                : HugeAmount.ZERO;
            BigInteger rawNext = current.toBigInteger().add(delta);
            if (strictMigration && rawNext.signum() < 0) {
                throw new IOException("V1 WAL would make an orphaned infinite-storage amount negative");
            }
            HugeAmount next = HugeAmount.of(rawNext.max(BigInteger.ZERO));
            if (next.isZero()) {
                orphanedEntries.remove(fingerprint);
            } else {
                orphanedEntries.put(fingerprint, new OrphanedStack(encodedKey.copy(), next));
            }
            if (next.compareTo(current) != 0) {
                dirtyShards.add(shard);
            }
        }

        private void repairWalTail(Path path, long validLength) {
            try (var ch = FileChannel.open(path, StandardOpenOption.WRITE)) {
                ch.truncate(validLength); ch.force(true);
                LOGGER.warn("Discarded incomplete ECO infinite storage WAL tail in {} at byte {}", path, validLength);
            } catch (IOException e) { degraded = true; LOGGER.error("Unable to repair ECO infinite storage WAL tail {}", path, e); }
        }
    } // end Loader

    private static void migrateFiles(Path sourceDirectory, Path targetDirectory,
            java.util.function.Predicate<String> selector) throws IOException {
        if (!Files.isDirectory(sourceDirectory)) {
            return;
        }
        try (var paths = Files.list(sourceDirectory)) {
            for (Path source : paths.filter(Files::isRegularFile).toList()) {
                if (!selector.test(source.getFileName().toString())) {
                    continue;
                }
                Path target = targetDirectory.resolve(source.getFileName());
                if (!Files.exists(target)) {
                    Files.move(source, target);
                }
            }
        }
    }

    private static Map<String, OrphanedStack> copyOrphanedEntries(Map<String, OrphanedStack> source) {
        Map<String, OrphanedStack> copy = new HashMap<>();
        source.forEach((fingerprint, entry) ->
            copy.put(fingerprint, new OrphanedStack(entry.encodedKey().copy(), entry.amount()))
        );
        return copy;
    }


    private void appendUnpersistedTransactions() throws IOException {
        appendUnpersistedTransactions(transactionLedgerPath, transactionsPath,
            committedTransactions, persistedTransactions);
    }

    private static void appendUnpersistedTransactions(
        Path transactionLedgerPath,
        Path transactionsPath,
        Set<UUID> committedTransactions,
        Set<UUID> persistedTransactions
    ) throws IOException {
        List<UUID> pending = committedTransactions.stream()
            .filter(transactionId -> !persistedTransactions.contains(transactionId))
            .toList();
        if (pending.isEmpty()) {
            return;
        }
        Files.createDirectories(transactionsPath);
        boolean existed = Files.exists(transactionLedgerPath);
        try (FileChannel channel = FileChannel.open(transactionLedgerPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            for (UUID transactionId : pending) {
                byte[] line = (transactionId + "\n").getBytes(StandardCharsets.US_ASCII);
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(line);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }
            channel.force(true);
        }
        persistedTransactions.addAll(pending);
        if (!existed) {
            forceDirectoryBestEffort(transactionsPath);
        }
    }

    private boolean isStorageLayoutMissing() {
        return !Files.isRegularFile(domainMarkerPath)
            || !Files.isDirectory(shardsPath)
            || !Files.isDirectory(walDirectory)
            || !Files.isDirectory(transactionsPath);
    }

    private void recoverMissingStorageIfNeeded() {
        if (!loaded) {
            return;
        }
        if (!directoryRecoveryRequired && !isStorageLayoutMissing()) {
            return;
        }
        Future<?> walWrite = pendingWalWrite;
        if (walWrite != null && !walWrite.isDone()) {
            awaitPendingWal();
            return;
        }
        for (CheckpointWrite write : new ArrayList<>(checkpointWrites.values())) {
            awaitPersistenceTask(write.future(), "checkpoint before domain recovery");
        }
        checkpointWrites.clear();
        LOGGER.warn("ECO infinite storage domain {} disappeared or became incomplete; rebuilding it from memory",
            domainId);
        directoryRecoveryRequired = false;
        recoveringDirectory = true;
        closeWalOutput();
        try {
            Files.createDirectories(shardsPath);
            Files.createDirectories(walDirectory);
            Files.createDirectories(transactionsPath);
            for (int shard = 0; shard < SHARD_COUNT; shard++) {
                writeShardSnapshot(shard, revision, createShardSnapshot(shard, revision));
                shardRevisions[shard] = revision;
                shardMutationRevisions[shard] = revision;
            }
            writeDomainMarker();
            for (Path path : listWalPathsChecked(walDirectory)) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOGGER.warn("Unable to remove obsolete WAL segment {} after domain recovery", path, e);
                }
            }
            persistedTransactions.clear();
            appendUnpersistedTransactions();
            synchronized (walStateLock) {
                sealedWalSegments.clear();
                activeWalShardRevisions.clear();
                activeWalBytes = 0L;
                activeWalRecordCount = 0L;
                activeWalMaxRevision = 0L;
            }
            dirtyShards.clear();
            nextWalSegmentId = findNextWalSegmentId(walDirectory);
            LOGGER.info("Rebuilt ECO infinite storage domain {} at revision {}", domainId, revision);
        } catch (IOException | RuntimeException e) {
            degraded = true;
            persistenceFailure = e;
            throw new IllegalStateException("Unable to rebuild missing ECO infinite storage domain " + domainId, e);
        } finally {
            recoveringDirectory = false;
        }
    }

    private void writeDomainMarker() throws IOException {
        writeDomainMarker(domainId, domainPath, domainMarkerPath);
    }

    private static void writeDomainMarker(UUID domainId, Path domainPath, Path domainMarkerPath) throws IOException {
        Files.createDirectories(domainPath);
        Path tmp = domainMarkerPath.resolveSibling(domainMarkerPath.getFileName() + ".tmp");
        Files.writeString(tmp, DOMAIN_MARKER_VERSION + "\n" + domainId + "\n",
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        replaceAtomically(tmp, domainMarkerPath);
        forceDirectoryBestEffort(domainPath);
    }

    private static void forceDirectoryBestEffort(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException e) {
            LOGGER.debug("Directory force is unavailable for {}", directory, e);
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
            Path tmp = shardsPath.resolve(shardFileName(shard) + ".tmp");
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
            forceDirectoryBestEffort(shardsPath);
            loggedCheckpointFailures.remove(shard);
        } catch (IOException e) {
            if (!recoveringDirectory && isStorageLayoutMissing()) {
                directoryRecoveryRequired = true;
                throw new StorageDirectoryMissingException(e);
            }
            if (!recoveringDirectory) {
                if (loggedCheckpointFailures.add(shard)) {
                    LOGGER.warn("Unable to checkpoint ECO infinite storage shard {}; WAL retention will continue",
                        shard, e);
                }
                throw new CheckpointRetryException(e);
            }
            degraded = true;
            persistenceFailure = e;
            throw new IllegalStateException("Unable to rebuild ECO infinite storage shard " + shard, e);
        } catch (RuntimeException e) {
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
        CompoundTag encodedKey = key.toTagGeneric(registries);
        return new WalRecord(recordRevision, shardFor(key), key, encodedKey, delta, transactionId);
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
            if (walChannel != null) {
                walChannel.force(true);
            }
            if (rotate) {
                sealActiveWal();
            }
        } catch (IOException | RuntimeException e) {
            if (isStorageLayoutMissing()) {
                directoryRecoveryRequired = true;
                throw new StorageDirectoryMissingException(e);
            }
            degraded = true;
            persistenceFailure = e;
            LOGGER.error("Unable to persist ECO infinite storage WAL {}", activeWalPath, e);
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
            Files.createDirectories(walDirectory);
            while (true) {
                if (nextWalSegmentId < 0L || nextWalSegmentId == Long.MAX_VALUE) {
                    throw new IOException("ECO infinite storage WAL segment id space is exhausted");
                }
                Path candidate = walSegmentPath(nextWalSegmentId++);
                try {
                    walChannel = FileChannel.open(candidate, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    activeWalPath = candidate;
                    forceDirectoryBestEffort(walDirectory);
                    walOut = new DataOutputStream(new BufferedOutputStream(
                        Channels.newOutputStream(walChannel), WAL_BUFFER_BYTES));
                    break;
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // A stale counter can only skip an existing immutable segment; never overwrite it.
                }
            }
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

        Path sealedPath = activeWalPath;
        closeWalOutputChecked();
        if (sealedPath == null) {
            throw new IOException("ECO infinite storage active WAL path is missing");
        }
        synchronized (walStateLock) {
            sealedWalSegments.add(new SealedWalSegment(sealedPath, barrierRevision, shardBarriers));
            activeWalBytes = 0L;
            activeWalRecordCount = 0L;
            activeWalMaxRevision = 0L;
            activeWalShardRevisions.clear();
            activeWalPath = null;
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
            walChannel = null;
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


    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24
            | (bytes[offset + 1] & 0xff) << 16
            | (bytes[offset + 2] & 0xff) << 8
            | bytes[offset + 3] & 0xff;
    }

    private static boolean isResolvedLegacyKey(@Nullable AEKey key) {
        return key != null && !AE2_MISSING_CONTENT.equals(key.getId());
    }

    private Path transactionReceipt(UUID transactionId) {
        return transactionsPath.resolve(transactionId + ".done");
    }

    private void writeTransactionReceipt(UUID transactionId) {
        try {
            appendUnpersistedTransactions();
        } catch (IOException e) {
            if (!recoveringDirectory && isStorageLayoutMissing()) {
                directoryRecoveryRequired = true;
                recoverMissingStorageIfNeeded();
                if (persistedTransactions.contains(transactionId)) {
                    return;
                }
            }
            degraded = true;
            persistenceFailure = e;
            throw new IllegalStateException("Unable to persist ECO infinite storage transaction receipt", e);
        }
    }

    private void retireCheckpointedActiveWal() {
        if (activeWalRecordCount <= 0L) {
            return;
        }
        try {
            sealActiveWal();
        } catch (IOException e) {
            LOGGER.warn("Unable to retire checkpointed ECO infinite storage WAL {}; it will be retried",
                activeWalPath, e);
        }
    }

    static boolean isWalRecordCovered(
        long recordRevision,
        long shardCheckpointRevision,
        long loadedKeyRevision
    ) {
        return recordRevision <= Math.max(shardCheckpointRevision, loadedKeyRevision);
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
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
        recoverMissingStorageIfNeeded();
        synchronized (walStateLock) {
            boolean deleted = false;
            while (!sealedWalSegments.isEmpty()) {
                SealedWalSegment segment = sealedWalSegments.getFirst();
                if (!isWalSegmentCovered(segment)) {
                    return;
                }
                try {
                    Files.deleteIfExists(segment.path());
                    sealedWalSegments.removeFirst();
                    deleted = true;
                } catch (IOException e) {
                    if (deleted) {
                        forceDirectoryBestEffort(walDirectory);
                    }
                    LOGGER.warn("Unable to remove checkpointed ECO infinite storage WAL segment {}", segment.path(), e);
                    return;
                }
            }
            if (deleted) {
                forceDirectoryBestEffort(walDirectory);
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
            LOGGER.warn("Unable to close ECO infinite storage WAL {}", activeWalPath, e);
        } finally {
            walOut = null;
            walChannel = null;
            activeWalPath = null;
        }
    }

    private Path shardPath(int shard) {
        return shardsPath.resolve(shardFileName(shard));
    }

    private Path walSegmentPath(long segmentId) {
        return walDirectory.resolve("wal-%020d.log".formatted(segmentId));
    }

    private List<Path> listWalPaths() {
        try {
            return listWalPathsChecked(walDirectory);
        } catch (IOException e) {
            degraded = true;
            LOGGER.error("Unable to list ECO infinite storage WAL segments in {}", walDirectory, e);
            return List.of();
        }
    }

    private static List<Path> listWalPathsChecked(Path walDirectory) throws IOException {
        try (var paths = Files.list(walDirectory)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString();
                    return name.equals("wal_000.log")
                        || name.startsWith("wal_") && name.endsWith(".sealed")
                        || name.startsWith("wal-") && name.endsWith(".log");
                })
                .sorted(FileBackedInfiniteStorageEngine::compareWalPaths)
                .toList();
        }
    }

    private static int compareWalPaths(Path left, Path right) {
        String leftName = left.getFileName().toString();
        String rightName = right.getFileName().toString();
        boolean leftNew = leftName.startsWith("wal-");
        boolean rightNew = rightName.startsWith("wal-");
        if (leftNew != rightNew) {
            return leftNew ? 1 : -1;
        }
        if (!leftNew && leftName.equals("wal_000.log") != rightName.equals("wal_000.log")) {
            return leftName.equals("wal_000.log") ? 1 : -1;
        }
        return leftName.compareTo(rightName);
    }

    private static long findNextWalSegmentId(Path walDirectory) throws IOException {
        long maximum = -1L;
        try (var paths = Files.list(walDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                if (!name.startsWith("wal-") || !name.endsWith(".log")) {
                    continue;
                }
                try {
                    maximum = Math.max(maximum, Long.parseLong(name.substring(4, name.length() - 4)));
                } catch (NumberFormatException ignored) {
                    LOGGER.warn("Ignoring unrecognized ECO infinite storage WAL segment name {}", path);
                }
            }
        }
        if (maximum == Long.MAX_VALUE) {
            throw new IOException("ECO infinite storage WAL segment id space is exhausted");
        }
        return maximum + 1L;
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
        CompoundTag encodedKey,
        BigInteger delta,
        @Nullable UUID transactionId
    ) {}

    private record WalScanResult(long recordCount, long maxRevision, Map<Integer, Long> shardRevisions) {}

    private record SealedWalSegment(Path path, long barrierRevision, Map<Integer, Long> shardRevisions) {}

    private static final class StorageDirectoryMissingException extends RuntimeException {
        private StorageDirectoryMissingException(Throwable cause) {
            super(cause);
        }
    }

    private static final class CheckpointRetryException extends RuntimeException {
        private CheckpointRetryException(Throwable cause) {
            super(cause);
        }
    }

    private static final class MutableTypeStats {
        private long storedTypes;
        private HugeAmount storedAmount = HugeAmount.ZERO;
    }
}
