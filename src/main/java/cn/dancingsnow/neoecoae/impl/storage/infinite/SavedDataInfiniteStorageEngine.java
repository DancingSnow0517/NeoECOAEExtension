package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.impl.storage.StorageByteAccounting;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-thread facade: primitive quantities, incremental statistics. Changes stay in memory and reach disk through
 * the vanilla SavedData cycle, like every other world-owned inventory.
 */
public final class SavedDataInfiniteStorageEngine implements ECOInfiniteStorageEngine {
    private final ECOInfiniteStorageData data;
    private final Map<AEKeyType, MutableTypeStats> typeStats = new HashMap<>();
    private List<TypeStats> snapshot = List.of();
    private boolean statisticsDirty = true;
    private AEKeyType lastType;
    private MutableTypeStats lastStats;

    public SavedDataInfiniteStorageEngine(ECOInfiniteStorageData data) {
        this.data = data;
        data.amounts.forEach((key, amount) -> {
            MutableTypeStats stats = typeStats.computeIfAbsent(key.getType(), ignored -> new MutableTypeStats());
            stats.types++;
            stats.total.add(amount.toBigInteger());
        });
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0 || !data.canWrite(key)) return 0;
        if (mode == Actionable.MODULATE) insertAmount(key, data.amounts.visible(key), amount);
        return amount;
    }

    @Override
    public BigInteger insert(AEKey key, BigInteger amount, Actionable mode) {
        if (key == null || amount == null || amount.signum() <= 0 || !data.canWrite(key)) return BigInteger.ZERO;
        if (amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
            return BigInteger.valueOf(insert(key, amount.longValueExact(), mode));
        }
        if (mode == Actionable.MODULATE) change(key, amount);
        return amount;
    }

    @Override
    public long insertOnce(UUID transaction, AEKey key, long amount) {
        if (key == null || amount <= 0 || !data.canMigrate()) return 0;
        if (transaction == null) return insert(key, amount, Actionable.MODULATE);
        if (data.hasMigrationReceipt(transaction)) return amount;
        if (!data.canWrite(key)) return 0;
        insertAmount(key, data.amounts.visible(key), amount);
        // Quantity and receipt share an atomic snapshot; a sealed source can safely retry after a restart.
        data.addMigrationReceipt(transaction);
        return amount;
    }

    @Override
    public long insertOnce(Set<UUID> transactions, AEKey key, long amount) {
        if (key == null || amount <= 0 || !data.canMigrate()) return 0;
        if (transactions == null || transactions.isEmpty()) return insert(key, amount, Actionable.MODULATE);
        if (transactions.stream().anyMatch(data::hasMigrationReceipt)) return amount;
        if (!data.canWrite(key)) return 0;
        insertAmount(key, data.amounts.visible(key), amount);
        data.addMigrationReceipts(transactions);
        return amount;
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0 || !data.canRead(key)) return 0;
        long current = data.amounts.visible(key);
        long extracted = Math.min(amount, current);
        if (mode == Actionable.SIMULATE || extracted == 0) return extracted;
        // canRead(key) already checked the restore lock; only the global write state remains.
        if (!data.canWrite()) return 0;
        MutableTypeStats stats = statsFor(key.getType());
        long remaining = data.amounts.subtract(key, current, extracted);
        stats.total.subtract(extracted);
        if (remaining == 0) {
            if (--stats.types == 0) removeStats(key.getType());
            data.setDirty();
        } else data.setDirty(true);
        statisticsDirty = true;
        return extracted;
    }

    private MutableTypeStats statsFor(AEKeyType type) {
        if (type != lastType || lastStats == null) {
            lastType = type;
            lastStats = typeStats.computeIfAbsent(type, ignored -> new MutableTypeStats());
        }
        return lastStats;
    }

    private void removeStats(AEKeyType type) {
        typeStats.remove(type);
        if (lastType == type) lastStats = null;
    }

    private void insertAmount(AEKey key, long current, long amount) {
        MutableTypeStats stats = statsFor(key.getType());
        data.amounts.add(key, current, amount);
        stats.total.add(amount);
        if (current == 0) {
            stats.types++;
            data.setDirty();
        } else data.setDirty(true);
        statisticsDirty = true;
    }

    private void change(AEKey key, BigInteger amount) {
        MutableTypeStats stats = statsFor(key.getType());
        boolean wasEmpty = data.amounts.visible(key) == 0;
        data.add(key, amount);
        stats.total.add(amount);
        if (wasEmpty) {
            stats.types++;
            data.setDirty();
        }
        statisticsDirty = true;
    }

    @Override
    public HugeAmount getAmount(AEKey key) {
        return data.canRead(key) ? data.getAmount(key) : HugeAmount.ZERO;
    }

    @Override
    public boolean contains(AEKey key) {
        return key != null && data.canRead(key) && data.amounts.visible(key) > 0;
    }

    @Override
    public boolean canUseMountedStorage() { return data.canUseMountedStorage(); }

    @Override
    public void visitExactAmounts(java.util.function.BiConsumer<AEKey, BigInteger> visitor) {
        if (!data.canRead()) return;
        if (!data.hasPendingRestore()) data.amounts.visitExact(visitor);
        else data.amounts.visitExact((key, amount) -> {
            if (data.canRead(key)) visitor.accept(key, amount);
        });
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!data.canRead()) return;
        boolean empty = out.isEmpty();
        if (!data.hasPendingRestore()) {
            if (empty) data.amounts.visitVisible(out::set);
            else data.amounts.visitVisible((key, amount) -> addVisible(out, key, amount));
            return;
        }
        data.amounts.visitVisible((key, amount) -> {
            if (data.canRead(key)) {
                if (empty) out.set(key, amount);
                else addVisible(out, key, amount);
            }
        });
    }

    static void addVisible(KeyCounter out, AEKey key, long amount) {
        long existing = out.get(key);
        long headroom = existing > 0 ? Long.MAX_VALUE - existing : Long.MAX_VALUE;
        if (headroom > 0) out.add(key, Math.min(amount, headroom));
    }

    @Override
    public BigInteger usedBytes() {
        BigInteger used = BigInteger.ZERO;
        for (TypeStats stats : getTypeStats()) {
            used = used.add(StorageByteAccounting.usedBytes(stats.storedTypes(), stats.storedAmount().toBigInteger(),
                    stats.keyType().getAmountPerByte(), 1L << (12 + ECOTier.L9.getTier())));
        }
        return used;
    }

    @Override
    public Collection<TypeStats> getTypeStats() {
        if (statisticsDirty) {
            snapshot = typeStats.entrySet().stream()
                    .map(e -> new TypeStats(e.getKey(), e.getValue().types, e.getValue().total.snapshot())).toList();
            statisticsDirty = false;
        }
        return snapshot;
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public boolean isHealthy() {
        return data.canWrite();
    }

    @Override
    public ECOInfiniteStorageData.DomainStatus status() {
        return data.status();
    }

    @Override
    public boolean canExitOrRestore() {
        return data.canExitOrRestore();
    }

    @Override
    public boolean hasHugeStacks() {
        return data.amounts.hasOverflow();
    }

    @Override
    public boolean hasMigrationReceipt(UUID transaction) {
        return data.hasMigrationReceipt(transaction);
    }

    @Override
    public long revision() {
        return data.revision();
    }

    @Override
    public CommitResult commit() {
        data.setDirty();
        return new CommitResult(data.canWrite(), data.revision(), data.lastFailureReason());
    }

    public List<String> failures() {
        return data.failures();
    }

    public String persistenceSummary() {
        return "SavedData revision: " + data.revision() + "; unsaved changes: " + data.isDirty();
    }

    @Override
    public boolean reserveRestore(AEKey key, UUID transaction, Set<UUID> targets) {
        return reserveRestores(Map.of(key, transaction), targets, Map.of(key, Map.of()));
    }

    @Override
    public boolean reserveRestores(Map<AEKey, UUID> transactions, Set<UUID> targets,
                                   Map<AEKey, Map<UUID, RestoreTargetAmounts>> plans) {
        if (!data.canExitOrRestore()) return false;
        for (var entry : transactions.entrySet()) {
            if (!data.canReserveRestore(entry.getKey(), entry.getValue(), targets, plans.getOrDefault(entry.getKey(), Map.of())))
                return false;
        }
        for (var entry : transactions.entrySet()) {
            if (!data.reserveRestore(entry.getKey(), entry.getValue(), targets, plans.getOrDefault(entry.getKey(), Map.of())))
                return false;
        }
        return commit().successful();
    }

    @Override
    public Set<UUID> restoreTargetIds(AEKey key) {
        return data.restoreTargetIds(key);
    }

    @Override
    public UUID restoreTransaction(AEKey key) {
        return data.restoreTransaction(key);
    }

    @Override
    public Map<UUID, RestoreTargetAmounts> restorePlan(AEKey key) {
        return data.restorePlan(key);
    }

    @Override
    public boolean hasPendingRestore() {
        return data.hasPendingRestore();
    }

    @Override
    public void failRestore(AEKey key, String reason) {
        data.failRestore(key, reason);
        commit();
    }

    @Override
    public boolean finishRestore(AEKey key, UUID transaction) {
        return finishRestores(Map.of(key, transaction));
    }

    @Override
    public boolean finishRestores(Map<AEKey, UUID> transactions) {
        for (var entry : transactions.entrySet()) {
            if (!data.canFinishRestore(entry.getKey(), entry.getValue())) return false;
        }
        for (var entry : transactions.entrySet()) {
            HugeAmount previous = data.getAmount(entry.getKey());
            if (!data.finishRestore(entry.getKey(), entry.getValue())) return false;
            HugeAmount remaining = data.getAmount(entry.getKey());
            MutableTypeStats stats = typeStats.get(entry.getKey().getType());
            if (stats != null) {
                stats.total.subtract(previous.subtract(remaining).toBigInteger());
                if (remaining.isZero() && --stats.types == 0) removeStats(entry.getKey().getType());
            }
        }
        statisticsDirty = true;
        return commit().successful();
    }

    @Override
    public HugeAmount getRestoreAmount(AEKey key) {
        return data.getAmount(key);
    }

    @Override
    public void getRestoreStacks(KeyCounter out) {
        if (data.canExitOrRestore()) data.amounts.visitVisible((key, amount) -> addVisible(out, key, amount));
    }

    private static final class MutableTypeStats {
        private long types;
        private final MutableInfiniteStorageTotal total = new MutableInfiniteStorageTotal();
    }
}
