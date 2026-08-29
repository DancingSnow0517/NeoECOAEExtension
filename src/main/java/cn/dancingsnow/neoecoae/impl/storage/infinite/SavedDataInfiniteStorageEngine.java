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
 * only marks the world data dirty; Minecraft writes it out with the rest of the world.
 */
public final class SavedDataInfiniteStorageEngine implements ECOInfiniteStorageEngine {
    private static final HugeAmount LONG_MAX_AMOUNT = HugeAmount.of(Long.MAX_VALUE);

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
        if (key == null || amount <= 0L || !data.canWrite()) {
            return 0L;
        }
        return amount;
    }

    @Override
    public synchronized long insert(AEKey key, long amount, Actionable mode) {
        long accepted = acceptableInsertAmount(key, amount);
        if (accepted > 0L && mode == Actionable.MODULATE) {
            applyDelta(key, HugeAmount.of(accepted), true);
        }
        return accepted;
    }

    @Override
    public synchronized long insertOnce(UUID transactionId, AEKey key, long amount) {
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
        applyDelta(key, HugeAmount.of(accepted), true);
        // The amount and its receipt live in the same file, so they become durable together.
        data.addMigrationReceipt(transactionId);
        return accepted;
    }

    @Override
    public synchronized long extract(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0L || !data.canRead()) {
            return 0L;
        }
        HugeAmount available = HugeAmount.of(amount).min(data.getAmount(key));
        if (available.isZero()) {
            return 0L;
        }
        if (mode == Actionable.SIMULATE) {
            // A pure read of the trusted in-memory data: it must keep working even while degraded, so players and
            // the network can always see what could be withdrawn.
            return available.toLongSaturated();
        }
        if (data.canWrite()) {
            applyDelta(key, available, false);
            return available.toLongSaturated();
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
        commit();
        if (data.canWrite()) {
            return available.toLongSaturated();
        }
        applyDelta(key, available, true);
        return 0L;
    }

    @Override
    public synchronized HugeAmount getAmount(AEKey key) {
        return data.getAmount(key);
    }

    @Override
    public synchronized void getAvailableStacks(KeyCounter out) {
        out.addAll(visibleStacks);
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
        return data.status();
    }

    @Override
    public synchronized boolean canExitOrRestore() {
        return data.canExitOrRestore();
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
    public synchronized void commit() {
        data.save(dataFile.toFile(), registries);
    }

    @Override
    public synchronized void clearMigrationReceipts() {
        data.clearMigrationReceipts();
    }

    private void applyDelta(AEKey key, HugeAmount changed, boolean added) {
        if (changed.isZero()) {
            return;
        }
        HugeAmount current = data.getAmount(key);
        HugeAmount effective = added ? changed : changed.min(current);
        if (effective.isZero()) {
            return;
        }
        HugeAmount next = added ? current.add(effective) : current.subtract(effective);
        data.setAmount(key, next);
        updateIndexes(key, current, next, effective, added);
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

    private static final class MutableTypeStats {
        private long storedTypes;
        private HugeAmount storedAmount = HugeAmount.ZERO;
    }
}
