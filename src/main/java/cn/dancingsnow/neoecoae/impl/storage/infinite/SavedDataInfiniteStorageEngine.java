package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;

/**
 * Infinite storage domain backed by {@link ECOInfiniteStorageData}. Every operation runs on the calling thread and
 * marks the world data dirty; periodic journal batches run in the background and save/migration barriers wait for them.
 */
public final class SavedDataInfiniteStorageEngine implements ECOInfiniteStorageEngine {

    private final ECOInfiniteStorageData data;
    private final HolderLookup.Provider registries;
    private final Path dataFile;

    // Views derived from the stored amounts. They exist so the storage network and the UI do not have to walk the
    // whole domain on every query; they carry no state of their own.
    private final KeyCounter visibleStacks = new KeyCounter();
    private final Map<AEKeyType, MutableTypeStats> typeStats = new HashMap<>();
    private final Map<AEKey, HugeAmount> hugeStacks = new HashMap<>();
    private List<TypeStats> typeStatsSnapshot = List.of();
    private boolean typeStatsSnapshotDirty = true;
    private List<HugeStack> hugeStacksSnapshot = List.of();
    private boolean hugeStacksSnapshotDirty = true;
    private String statisticsFailure;

    public SavedDataInfiniteStorageEngine(ECOInfiniteStorageData data, HolderLookup.Provider registries, Path dataFile) {
        this.data = data;
        this.registries = registries;
        this.dataFile = dataFile;
        rebuildIndexes();
    }

    /**
     * The amount that may be inserted, used by both {@link Actionable} modes. An infinite domain has no byte budget,
     * so the only limits are the arguments themselves and whether the underlying world data currently accepts writes.
     */
    private long acceptableInsertAmount(AEKey key, long amount) {
        if (key == null || amount <= 0L || !data.canWrite(key)) {
            return 0L;
        }
        return amount;
    }

    @Override
    public synchronized long insert(AEKey key, long amount, Actionable mode) {
        long accepted = acceptableInsertAmount(key, amount);
        if (accepted > 0L && mode == Actionable.MODULATE) {
            applyDelta(key, accepted, true);
        }
        return accepted;
    }

    @Override
    public synchronized long insertOnce(UUID transactionId, AEKey key, long amount) {
        if (!data.canMigrate()) return 0L;
        if (transactionId == null) {
            return insert(key, amount, Actionable.MODULATE);
        }
        long accepted = acceptableInsertAmount(key, amount);
        if (accepted <= 0L) {
            return 0L;
        }
        if (data.hasMigrationReceipt(transactionId)) {
            return accepted;
        }
        applyDelta(key, accepted, true);
        // The amount and its receipt live in the same file, so they become durable together.
        data.addMigrationReceipt(transactionId, key);
        return accepted;
    }

    @Override
    public synchronized long extract(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0L || !data.canRead(key)) {
            return 0L;
        }
        long available = Math.min(amount, data.getAmount(key).toLongSaturated());
        if (available == 0L) {
            return 0L;
        }
        if (mode == Actionable.SIMULATE) {
            // A pure read of the trusted in-memory data: it must keep working even while degraded, so players and
            // the network can always see what could be withdrawn.
            return available;
        }
        if (data.canWrite(key)) {
            applyDelta(key, available, false);
            return available;
        }
        if (data.status() != ECOInfiniteStorageData.DomainStatus.RECOVERY_READ_ONLY) {
            // UNAVAILABLE: data.canRead() above already excludes this, so this is unreachable in practice; kept as
            // an explicit floor rather than falling through to the rescue path below.
            return 0L;
        }
        // Recovery rescue extraction: the domain cannot currently be written to, but a player pulling out items they
        // already own only ever shrinks what is stored, so it is safe to allow it if — and only if — we can prove
        // the withdrawal reached disk before reporting success. Apply the change, then commit synchronously; keep
        // it only if that commit actually succeeds, otherwise roll back and refuse. This never runs on the normal
        // insert/extract hot path, only while already degraded.
        applyDelta(key, available, false);
        if (commit().successful()) {
            return available;
        }
        applyDelta(key, available, true);
        return 0L;
    }

    @Override
    public synchronized HugeAmount getAmount(AEKey key) {
        return data.canRead(key) ? data.getAmount(key) : HugeAmount.ZERO;
    }

    @Override
    public synchronized void getAvailableStacks(KeyCounter out) {
        if (!data.canRead()) return;
        if (!data.hasFilteredKeys()) {
            out.addAll(visibleStacks);
            return;
        }
        for (var entry : visibleStacks) {
            if (data.canRead(entry.getKey())) out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public synchronized boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public synchronized boolean isHealthy() {
        return data.isHealthy();
    }

    @Override
    public synchronized ECOInfiniteStorageData.DomainStatus status() {
        var status = data.status();
        return statisticsFailure != null && status == ECOInfiniteStorageData.DomainStatus.HEALTHY
            ? ECOInfiniteStorageData.DomainStatus.DEGRADED : status;
    }

    @Override
    public synchronized boolean canExitOrRestore() {
        return data.canExitOrRestore();
    }

    @Override
    public synchronized Collection<TypeStats> getTypeStats() {
        if (statisticsFailure != null) return typeStatsSnapshot;
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
    public synchronized CommitResult commit() {
        data.save(dataFile.toFile(), registries);
        return new CommitResult(data.canWrite() && !data.isDirty(), data.durableRevision(), data.lastFailureReason());
    }

    @Override
    public synchronized boolean hasMigrationReceipt(UUID transactionId) { return data.hasMigrationReceipt(transactionId); }

    @Override
    public synchronized boolean hasHugeStacks() { return !hugeStacks.isEmpty(); }

    @Override
    public synchronized Collection<HugeStack> getLargestStacks(int limit) {
        if (limit <= 0) return List.of();
        var largest = new java.util.PriorityQueue<HugeStack>(java.util.Comparator.comparing(HugeStack::amount));
        for (var entry : hugeStacks.entrySet()) {
            if (largest.size() < limit) largest.add(new HugeStack(entry.getKey(), entry.getValue()));
            else if (entry.getValue().compareTo(largest.peek().amount()) > 0) {
                largest.remove();
                largest.add(new HugeStack(entry.getKey(), entry.getValue()));
            }
        }
        List<HugeStack> result = new ArrayList<>(largest);
        result.sort((left, right) -> right.amount().compareTo(left.amount()));
        return List.copyOf(result);
    }

    @Override
    public synchronized long revision() { return data.revision(); }

    @Override
    public synchronized void tick(long gameTime) { data.tick(gameTime); }

    public synchronized void close() { data.closeJournal(); }

    public synchronized List<String> failures() {
        List<String> failures = new ArrayList<>(data.failures());
        if (statisticsFailure != null) failures.add("statistics: " + statisticsFailure);
        return List.copyOf(failures);
    }

    public synchronized String persistenceSummary() {
        return "Pending keys: " + data.pendingKeyCount() + "; revision: " + data.revision()
            + "; saved revision: " + data.durableRevision();
    }

    @Override
    public synchronized boolean reserveRestore(AEKey key, UUID transaction) {
        return data.reserveRestore(key, transaction) && commit().successful();
    }

    @Override
    public synchronized boolean reserveRestore(AEKey key, UUID transaction, java.util.Set<UUID> targets) {
        return data.reserveRestore(key, transaction, targets) && commit().successful();
    }

    @Override
    public synchronized java.util.Set<UUID> restoreTargetIds(AEKey key) { return data.restoreTargetIds(key); }

    @Override
    public synchronized UUID restoreTransaction(AEKey key) { return data.restoreTransaction(key); }

    @Override
    public synchronized boolean hasPendingRestore() { return data.hasPendingRestore(); }

    @Override
    public synchronized void failRestore(AEKey key, String reason) { data.failRestore(key, reason); commit(); }

    @Override
    public synchronized boolean finishRestore(AEKey key, UUID transaction) {
        HugeAmount previous = data.getAmount(key);
        if (!data.finishRestore(key, transaction)) return false;
        updateIndexes(key, previous, HugeAmount.ZERO, previous, false);
        return commit().successful();
    }

    @Override
    public synchronized HugeAmount getRestoreAmount(AEKey key) { return data.getAmount(key); }

    @Override
    public synchronized void getRestoreStacks(KeyCounter out) { if (data.canExitOrRestore()) out.addAll(visibleStacks); }

    @Override
    public synchronized void clearMigrationReceipts() {
        data.clearMigrationReceipts();
    }

    private void applyDelta(AEKey key, long changed, boolean added) {
        if (changed == 0L) {
            return;
        }
        HugeAmount current = data.getAmount(key);
        long effective = added ? changed : Math.min(changed, current.toLongSaturated());
        if (effective == 0L) {
            return;
        }
        HugeAmount next = added ? current.add(effective) : current.subtract(effective);
        data.setAmount(key, next);
        updateIndexes(key, current, next, HugeAmount.of(effective), added);
    }

    private void rebuildIndexes() {
        visibleStacks.clear();
        typeStats.clear();
        hugeStacks.clear();
        typeStatsSnapshot = List.of();
        typeStatsSnapshotDirty = true;
        hugeStacksSnapshot = List.of();
        hugeStacksSnapshotDirty = true;
        for (Map.Entry<AEKey, HugeAmount> entry : data.getAmounts().entrySet()) {
            updateIndexes(entry.getKey(), HugeAmount.ZERO, entry.getValue(), entry.getValue(), true);
        }
    }

    /**
     * Refreshes the derived views after a single key changed from {@code previous} to {@code next}.
     */
    private void updateIndexes(AEKey key, HugeAmount previous, HugeAmount next, HugeAmount changed, boolean added) {
        if (next.isZero()) {
            visibleStacks.remove(key);
        } else {
            visibleStacks.set(key, next.toLongSaturated());
        }
        if (next.isBig()) {
            hugeStacks.put(key, next);
            hugeStacksSnapshotDirty = true;
        } else if (hugeStacks.remove(key) != null) {
            hugeStacksSnapshotDirty = true;
        }

        int typeDelta = (previous.isZero() ? 0 : -1) + (next.isZero() ? 0 : 1);
        if (changed.isZero() && typeDelta == 0) {
            return;
        }

        try {
            AEKeyType keyType = key.getType();
            MutableTypeStats stats = typeStats.computeIfAbsent(keyType, ignored -> new MutableTypeStats());
            stats.storedTypes += typeDelta;
            stats.storedAmount = added ? stats.storedAmount.add(changed) : stats.storedAmount.subtract(changed);
            if (stats.storedTypes <= 0L || stats.storedAmount.isZero()) {
                typeStats.remove(keyType);
            }
            typeStatsSnapshotDirty = true;
        } catch (RuntimeException e) {
            if (statisticsFailure == null) org.slf4j.LoggerFactory.getLogger(SavedDataInfiniteStorageEngine.class)
                .error("ECO statistics update failed; inventory operations remain available", e);
            statisticsFailure = e.toString();
        }
    }

    private static final class MutableTypeStats {
        private long storedTypes;
        private HugeAmount storedAmount = HugeAmount.ZERO;
    }
}
