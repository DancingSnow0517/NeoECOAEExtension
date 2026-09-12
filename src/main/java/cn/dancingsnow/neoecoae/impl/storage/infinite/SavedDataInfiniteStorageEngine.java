package cn.dancingsnow.neoecoae.impl.storage.infinite;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.impl.storage.StorageByteAccounting;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;

/** Server-thread facade: primitive quantities, incremental statistics, world-save persistence. */
public final class SavedDataInfiniteStorageEngine implements ECOInfiniteStorageEngine {
    private final ECOInfiniteStorageData data;
    private final HolderLookup.Provider registries;
    private final Path dataFile;
    private final Map<AEKeyType, MutableTypeStats> typeStats = new HashMap<>();
    private List<TypeStats> snapshot = List.of();
    private boolean statisticsDirty = true;

    public SavedDataInfiniteStorageEngine(ECOInfiniteStorageData data, HolderLookup.Provider registries, Path dataFile) {
        this.data = data;
        this.registries = registries;
        this.dataFile = dataFile;
        data.amounts.forEach((key, amount) -> {
            MutableTypeStats stats = typeStats.computeIfAbsent(key.getType(), ignored -> new MutableTypeStats());
            stats.types++;
            stats.total = stats.total.add(amount);
        });
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0 || !data.canWrite(key)) return 0;
        if (mode == Actionable.MODULATE) change(key, amount, true);
        return amount;
    }

    @Override
    public long insertOnce(UUID transaction, AEKey key, long amount) {
        if (key == null || amount <= 0 || !data.canMigrate()) return 0;
        if (transaction == null) return insert(key, amount, Actionable.MODULATE);
        if (data.hasMigrationReceipt(transaction)) return amount;
        if (!data.canWrite(key)) return 0;
        change(key, amount, true);
        // Quantity and receipt share an atomic snapshot; a sealed source can safely retry after a restart.
        data.addMigrationReceipt(transaction);
        return amount;
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode) {
        if (key == null || amount <= 0 || !data.canRead(key)) return 0;
        long extracted = Math.min(amount, data.amounts.visible(key));
        if (mode == Actionable.SIMULATE || extracted == 0) return extracted;
        if (!data.canWrite(key)) return 0;
        change(key, extracted, false);
        return extracted;
    }

    private void change(AEKey key, long amount, boolean added) {
        MutableTypeStats stats = typeStats.computeIfAbsent(key.getType(), ignored -> new MutableTypeStats());
        boolean wasEmpty = data.amounts.visible(key) == 0;
        if (added) data.add(key, amount);
        else data.subtract(key, amount);
        boolean empty = data.amounts.visible(key) == 0;
        stats.types += (wasEmpty ? 0 : -1) + (empty ? 0 : 1);
        stats.total = added ? stats.total.add(amount) : stats.total.subtract(amount);
        if (stats.types == 0) typeStats.remove(key.getType());
        statisticsDirty = true;
    }

    @Override
    public HugeAmount getAmount(AEKey key) { return data.canRead(key) ? data.getAmount(key) : HugeAmount.ZERO; }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!data.canRead()) return;
        data.amounts.visitVisible((key, amount) -> { if (data.canRead(key)) addVisible(out, key, amount); });
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
                .map(e -> new TypeStats(e.getKey(), e.getValue().types, e.getValue().total)).toList();
            statisticsDirty = false;
        }
        return snapshot;
    }

    @Override
    public boolean isEmpty() { return data.isEmpty(); }
    @Override
    public boolean isHealthy() { return data.canWrite(); }
    @Override
    public ECOInfiniteStorageData.DomainStatus status() { return data.status(); }
    @Override
    public boolean canExitOrRestore() { return data.canExitOrRestore(); }
    @Override
    public boolean hasHugeStacks() { return data.amounts.hasOverflow(); }
    @Override
    public boolean hasMigrationReceipt(UUID transaction) { return data.hasMigrationReceipt(transaction); }
    @Override
    public long revision() { return data.revision(); }

    @Override
    public CommitResult commit() {
        data.save(dataFile.toFile(), registries);
        return new CommitResult(data.canWrite() && !data.isDirty(), data.durableRevision(), data.lastFailureReason());
    }

    public void close() {
        if (data.isDirty() && !commit().successful()) {
            throw new IllegalStateException("Infinite domain snapshot is still unsaved: " + data.lastFailureReason());
        }
    }
    public List<String> failures() { return data.failures(); }
    public String persistenceSummary() {
        return "SavedData revision: " + data.revision() + "; saved revision: " + data.durableRevision();
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
            if (!data.canReserveRestore(entry.getKey(), entry.getValue(), targets, plans.getOrDefault(entry.getKey(), Map.of()))) return false;
        }
        for (var entry : transactions.entrySet()) {
            if (!data.reserveRestore(entry.getKey(), entry.getValue(), targets, plans.getOrDefault(entry.getKey(), Map.of()))) return false;
        }
        return commit().successful();
    }

    @Override
    public Set<UUID> restoreTargetIds(AEKey key) { return data.restoreTargetIds(key); }
    @Override
    public UUID restoreTransaction(AEKey key) { return data.restoreTransaction(key); }
    @Override
    public Map<UUID, RestoreTargetAmounts> restorePlan(AEKey key) { return data.restorePlan(key); }
    @Override
    public boolean hasPendingRestore() { return data.hasPendingRestore(); }
    @Override
    public void failRestore(AEKey key, String reason) { data.failRestore(key, reason); commit(); }

    @Override
    public boolean finishRestore(AEKey key, UUID transaction) { return finishRestores(Map.of(key, transaction)); }

    @Override
    public boolean finishRestores(Map<AEKey, UUID> transactions) {
        for (var entry : transactions.entrySet()) {
            if (!data.canFinishRestore(entry.getKey(), entry.getValue())) return false;
        }
        for (var entry : transactions.entrySet()) {
            HugeAmount previous = data.getAmount(entry.getKey());
            if (!data.finishRestore(entry.getKey(), entry.getValue())) return false;
            MutableTypeStats stats = typeStats.get(entry.getKey().getType());
            if (stats != null) {
                stats.total = stats.total.subtract(previous);
                if (--stats.types == 0) typeStats.remove(entry.getKey().getType());
            }
        }
        statisticsDirty = true;
        return commit().successful();
    }

    @Override
    public HugeAmount getRestoreAmount(AEKey key) { return data.getAmount(key); }
    @Override
    public void getRestoreStacks(KeyCounter out) {
        if (data.canExitOrRestore()) data.amounts.visitVisible((key, amount) -> addVisible(out, key, amount));
    }

    private static final class MutableTypeStats {
        private long types;
        private HugeAmount total = HugeAmount.ZERO;
    }
}
