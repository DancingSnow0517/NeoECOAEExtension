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
import cn.dancingsnow.neoecoae.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.storage.infinite.HugeAmount;
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
    // long, then U126, then BigInteger only past 2^126: ordinary transfers never allocate a BigInteger.
    private final Map<AEKey, HugeAmount> amounts = new LinkedHashMap<>();
    private final BiggerAE2StorageCellItem item;
    private final ISaveProvider host;
    private boolean dirty;

    BiggerAE2SingularityStorageCell(ItemStack stack, @Nullable ISaveProvider host) {
        super(stack, host);
        item = (BiggerAE2StorageCellItem) stack.getItem();
        this.host = host;
        for (var entry : stack.getOrDefault(BiggerAE2SingularityContents.TYPE,
                BiggerAE2SingularityContents.EMPTY).entries()) {
            amounts.put(entry.key(), HugeAmount.of(entry.amount()));
        }
    }

    @Override
    public boolean isInfiniteStorageEligible() { return false; }

    @Override
    public long getStoredItemTypes() { return amounts.size(); }

    @Override
    public long getRemainingItemTypes() { return Math.max(0, getTotalItemTypes() - amounts.size()); }

    @Override
    public long getStoredItemCount() { return total().toLongSaturated(); }

    @Override
    public long getRemainingItemCount() { return Long.MAX_VALUE; }

    @Override
    public long getFreeBytes() { return Long.MAX_VALUE; }

    @Override
    public int getUnusedItemCount() { return 0; }

    @Override
    public long getUsedBytes() {
        BigInteger divisor = BigInteger.valueOf(Math.max(1, getKeyType().getAmountPerByte()));
        return saturated(total().toBigInteger().add(divisor).subtract(BigInteger.ONE).divide(divisor));
    }

    @Override
    public CellState getStatus() {
        return amounts.isEmpty() ? CellState.EMPTY
            : amounts.size() < getTotalItemTypes() ? CellState.NOT_EMPTY : CellState.TYPES_FULL;
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !canAccept(key)) return 0;
        if (mode == Actionable.MODULATE) store(key, HugeAmount.of(amount));
        return amount;
    }

    @Override
    public BigInteger insertBigInteger(AEKey key, BigInteger amount, Actionable mode, IActionSource source) {
        if (amount.signum() <= 0 || !canAccept(key)) return BigInteger.ZERO;
        if (mode == Actionable.MODULATE) store(key, HugeAmount.of(amount));
        return amount;
    }

    private boolean canAccept(AEKey key) {
        if (ECOInfiniteStorageMember.isSealed(cellStack) || !acceptsKey(key)
            || !getPartitionList().matchesFilter(key, getPartitionListMode()) || item.isBlackListed(cellStack, key)) {
            return false;
        }
        return amounts.containsKey(key) || amounts.size() < getTotalItemTypes() && canStoreKeyInsideStorageCell(key);
    }

    private void store(AEKey key, HugeAmount amount) {
        amounts.merge(key, amount, HugeAmount::add);
        saveChanges();
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || ECOInfiniteStorageMember.isSealed(cellStack)) return 0;
        HugeAmount current = amounts.getOrDefault(key, HugeAmount.ZERO);
        long extracted = Math.min(current.toLongSaturated(), amount);
        if (mode == Actionable.MODULATE && extracted > 0) {
            HugeAmount next = current.subtract(extracted);
            if (next.isZero()) amounts.remove(key);
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

    /** The listing is the seal state plus {@link #amounts}, which change only through {@link #saveChanges}. */
    @Override
    public long neoecoae$listingVersion() { return contentListingVersion(); }

    /** Shares the ECO infinite storage terminal path without reading each cell's contents twice. */
    @Override
    public void neoecoae$listWithExactAmounts(KeyCounter out, BiConsumer<AEKey, ExactAmount> visitor) {
        if (ECOInfiniteStorageMember.isSealed(cellStack)) return;
        amounts.forEach((key, amount) -> {
            addVisible(out, key, amount);
            visitor.accept(key, ExactAmount.finite(amount.toBigInteger()));
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
            .map(entry -> new BiggerAE2SingularityContents.Entry(entry.getKey(), entry.getValue().toBigInteger())).toList()));
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
    public long getMigrationAmount(AEKey key) { return amounts.getOrDefault(key, HugeAmount.ZERO).toLongSaturated(); }

    @Override
    public Iterator<Object2LongMap.Entry<AEKey>> migrationEntries() {
        Object2LongMap<AEKey> snapshot = new Object2LongOpenHashMap<>();
        amounts.forEach((key, amount) -> snapshot.put(key, amount.toLongSaturated()));
        return snapshot.object2LongEntrySet().iterator();
    }

    @Override
    public void neoecoae$visitExactAmounts(BiConsumer<AEKey, ExactAmount> visitor) {
        if (!ECOInfiniteStorageMember.isSealed(cellStack))
            amounts.forEach((key, amount) -> visitor.accept(key, ExactAmount.finite(amount.toBigInteger())));
    }

    private HugeAmount total() {
        HugeAmount total = HugeAmount.ZERO;
        for (HugeAmount amount : amounts.values()) total = total.add(amount);
        return total;
    }

    private static void addVisible(KeyCounter out, AEKey key, HugeAmount amount) {
        out.set(key, NEMath.saturatingAdd(out.get(key), amount.toLongSaturated()));
    }

    private static long saturated(BigInteger amount) { return amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact(); }
}
