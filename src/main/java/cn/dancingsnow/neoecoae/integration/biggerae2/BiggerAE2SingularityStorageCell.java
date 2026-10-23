package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.storage.ECOBigIntegerStorage;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import cn.dancingsnow.neoecoae.crafting.display.terminal.CombinedExactAmountSource;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Quantity is unbounded, type count is finite. This is stored inventory, never a resource source. */
final class BiggerAE2SingularityStorageCell extends ECOStorageCell implements ECOBigIntegerStorage, CombinedExactAmountSource {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private final Map<AEKey, BigInteger> amounts = new LinkedHashMap<>();
    private final BiggerAE2StorageCellItem item;
    private final ISaveProvider host;
    private boolean dirty;

    BiggerAE2SingularityStorageCell(ItemStack stack, @Nullable ISaveProvider host) {
        super(stack, host);
        item = (BiggerAE2StorageCellItem) stack.getItem();
        this.host = host;
        for (var entry : stack.getOrDefault(BiggerAE2SingularityContents.TYPE,
                BiggerAE2SingularityContents.EMPTY).entries()) {
            amounts.put(entry.key(), entry.amount());
        }
    }

    @Override
    public boolean isInfiniteStorageEligible() { return false; }

    @Override
    public long getStoredItemTypes() { return amounts.size(); }

    @Override
    public long getRemainingItemTypes() { return Math.max(0, getTotalItemTypes() - amounts.size()); }

    @Override
    public long getStoredItemCount() { return saturated(total()); }

    @Override
    public long getRemainingItemCount() { return Long.MAX_VALUE; }

    @Override
    public long getFreeBytes() { return Long.MAX_VALUE; }

    @Override
    public int getUnusedItemCount() { return 0; }

    @Override
    public long getUsedBytes() {
        BigInteger divisor = BigInteger.valueOf(Math.max(1, getKeyType().getAmountPerByte()));
        return saturated(total().add(divisor).subtract(BigInteger.ONE).divide(divisor));
    }

    @Override
    public CellState getStatus() {
        return amounts.isEmpty() ? CellState.EMPTY
            : amounts.size() < getTotalItemTypes() ? CellState.NOT_EMPTY : CellState.TYPES_FULL;
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0) return 0;
        return insertBigInteger(key, BigInteger.valueOf(amount), mode, source).longValueExact();
    }

    @Override
    public BigInteger insertBigInteger(AEKey key, BigInteger amount, Actionable mode, IActionSource source) {
        if (amount.signum() <= 0 || ECOInfiniteStorageMember.isSealed(cellStack) || !acceptsKey(key)
            || !getPartitionList().matchesFilter(key, getPartitionListMode()) || item.isBlackListed(cellStack, key)) {
            return BigInteger.ZERO;
        }
        if (!amounts.containsKey(key) && (amounts.size() >= getTotalItemTypes()
            || !canStoreKeyInsideStorageCell(key))) return BigInteger.ZERO;
        if (mode == Actionable.MODULATE) {
            amounts.merge(key, amount, BigInteger::add);
            saveChanges();
        }
        return amount;
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || ECOInfiniteStorageMember.isSealed(cellStack)) return 0;
        BigInteger current = amounts.getOrDefault(key, BigInteger.ZERO);
        long extracted = current.min(BigInteger.valueOf(amount)).longValueExact();
        if (mode == Actionable.MODULATE && extracted > 0) {
            BigInteger next = current.subtract(BigInteger.valueOf(extracted));
            if (next.signum() == 0) amounts.remove(key);
            else amounts.put(key, next);
            saveChanges();
        }
        return extracted;
    }

    @Override
    public boolean canFitInsideCell() { return amounts.isEmpty() && !ECOInfiniteStorageMember.isSealed(cellStack); }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!ECOInfiniteStorageMember.isSealed(cellStack)) amounts.forEach((key, amount) -> addVisible(out, key, amount));
    }

    @Override
    public boolean neoecoae$hasSaturatingListing() { return true; }

    /** Shares the ECO infinite storage terminal path without reading each cell's contents twice. */
    @Override
    public void neoecoae$listWithExactAmounts(KeyCounter out, BiConsumer<AEKey, ExactAmount> visitor) {
        if (ECOInfiniteStorageMember.isSealed(cellStack)) return;
        amounts.forEach((key, amount) -> {
            addVisible(out, key, amount);
            visitor.accept(key, ExactAmount.finite(amount));
        });
    }

    @Override
    public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
        return (amounts.containsKey(key) || getPartitionList().isListed(key))
            && insert(key, 1, Actionable.SIMULATE, source) == 1;
    }

    @Override
    public void persist() {
        if (!dirty) return;
        if (amounts.isEmpty()) cellStack.remove(BiggerAE2SingularityContents.TYPE);
        else cellStack.set(BiggerAE2SingularityContents.TYPE, new BiggerAE2SingularityContents(amounts.entrySet().stream()
            .map(entry -> new BiggerAE2SingularityContents.Entry(entry.getKey(), entry.getValue())).toList()));
        dirty = false;
    }

    @Override
    protected void saveChanges() {
        markContentChanged();
        dirty = true;
        if (deferMutationBatch()) return;
        persist();
        if (host != null) host.saveChanges();
    }

    @Override
    public void clearAllStoredStacks() {
        amounts.clear();
        saveChanges();
    }

    // Do not expose saturated amounts to the finite-cell migration protocol.
    @Override
    public long insertForMigration(AEKey key, long amount, Actionable mode) { return 0; }

    @Override
    public long simulateInsertForMigration(AEKey key, long amount, KeyCounter contents) { return 0; }

    @Override
    public long simulateInsertForMigration(AEKey key, long amount, long current, long types, long count) { return 0; }

    @Override
    public void getMigrationStacks(KeyCounter out) { getAvailableStacks(out); }

    @Override
    public long getMigrationAmount(AEKey key) { return saturated(amounts.getOrDefault(key, BigInteger.ZERO)); }

    @Override
    public Iterator<Object2LongMap.Entry<AEKey>> migrationEntries() {
        Object2LongMap<AEKey> snapshot = new Object2LongOpenHashMap<>();
        amounts.forEach((key, amount) -> snapshot.put(key, saturated(amount)));
        return snapshot.object2LongEntrySet().iterator();
    }

    @Override
    public void neoecoae$visitExactAmounts(BiConsumer<AEKey, ExactAmount> visitor) {
        if (!ECOInfiniteStorageMember.isSealed(cellStack))
            amounts.forEach((key, amount) -> visitor.accept(key, ExactAmount.finite(amount)));
    }

    private BigInteger total() { return amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add); }

    private static void addVisible(KeyCounter out, AEKey key, BigInteger amount) {
        out.set(key, NEMath.saturatingAdd(out.get(key), saturated(amount)));
    }

    private static long saturated(BigInteger amount) { return amount.min(MAX_LONG).longValueExact(); }
}
