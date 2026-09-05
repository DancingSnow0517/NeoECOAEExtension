package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.item.ECOUniversalStorageCellItem;
import com.wintercogs.ae2omnicells.common.me.IAEUniversalCell;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ECOUniversalStorageCell implements IECOStorageCell {
    private final StorageCell delegate;
    private final ItemStack stack;
    private final ECOUniversalStorageCellItem item;

    public ECOUniversalStorageCell(StorageCell delegate, ItemStack stack, ECOUniversalStorageCellItem item) {
        this.delegate = delegate;
        this.stack = stack;
        this.item = item;
    }

    @Override
    public IECOTier getTier() {
        return item.getTier();
    }

    @Override
    public ECOCellType getCellType() {
        return item.getCellType();
    }

    @Override
    public long getStoredItemTypes() {
        return IAEUniversalCell.getUsedTypes(stack);
    }

    @Override
    public long getTotalItemTypes() {
        return Math.max(0, item.getTotalTypes());
    }

    @Override
    public boolean hasInfiniteTypeCapacity() {
        return item.getTotalTypes() < 0;
    }

    @Override
    public long getUsedBytes() {
        return IAEUniversalCell.getUsedBytes(stack);
    }

    @Override
    public long getTotalBytes() {
        return item.getECOStorageTotalBytes();
    }

    @Override
    public CellState getStatus() {
        if (item.isExternallyUnlimited() && getUsedBytes() >= getTotalBytes()) {
            return CellState.FULL;
        }
        return delegate.getStatus();
    }

    @Override
    public double getIdleDrain() {
        return delegate.getIdleDrain();
    }

    @Override
    public boolean canFitInsideCell() {
        return !cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(stack)
            && delegate.canFitInsideCell();
    }

    @Override
    public void persist() {
        delegate.persist();
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return delegate.isPreferredStorageFor(what, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(stack)) return 0L;
        return insertForMigration(what, amount, mode, source);
    }

    public long insertForMigration(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0L) return 0L;
        if (!item.isExternallyUnlimited()) {
            return delegate.insert(what, amount, mode, source);
        }

        long amountPerByte = Math.max(1L, what.getType().getAmountPerByte());
        StorageSnapshot current = snapshot();
        long capacityBound = calculateRemainingAmount(
            getTotalBytes(), current.usedBytes(), current.bucketSums().get(amountPerByte), amountPerByte);
        return delegate.insert(what, Math.min(amount, capacityBound), mode, source);
    }

    /**
     * Computes restore capacity against a caller-owned snapshot. The copied ItemStack used for a restore target may
     * still point at OmniCells' SavedData, so using {@code MODULATE} here would write into the live matrix during the
     * preflight and the real restore would write the same amount again.
     */
    public long simulateInsertForMigration(AEKey what, long amount, KeyCounter simulatedContents) {
        if (amount <= 0L || simulatedContents == null) {
            return 0L;
        }

        StorageSnapshot current = snapshot(simulatedContents);
        long currentAmount = simulatedContents.get(what);
        if (currentAmount <= 0L && item.getTotalTypes() > 0
            && current.storedTypes() >= item.getTotalTypes()) {
            return 0L;
        }
        // Preserve partition and nested-cell rules enforced by the actual OmniCells delegate. This is a read-only
        // probe and does not touch the caller-owned snapshot or the backing SavedData.
        if (delegate.insert(what, 1L, Actionable.SIMULATE, IActionSource.empty()) <= 0L) {
            return 0L;
        }

        long amountPerByte = Math.max(1L, what.getType().getAmountPerByte());
        long capacity = calculateRemainingAmount(
            getTotalBytes(), current.usedBytes(), current.bucketSums().get(amountPerByte), amountPerByte);
        return Math.min(amount, capacity);
    }

    public long getUsedBytesForMigration(KeyCounter simulatedContents) {
        return simulatedContents == null ? 0L : snapshot(simulatedContents).usedBytes();
    }

    static long calculateUsedBytes(Long2LongMap bucketSums) {
        long usedBytes = 0L;
        for (Long2LongMap.Entry entry : bucketSums.long2LongEntrySet()) {
            long amountPerByte = Math.max(1L, entry.getLongKey());
            long amount = Math.max(0L, entry.getLongValue());
            usedBytes = saturatingAdd(usedBytes, ceilDivide(amount, amountPerByte));
        }
        return usedBytes;
    }

    static long calculateRemainingAmount(long totalBytes, long usedBytes, long targetBucketAmount, long amountPerByte) {
        if (totalBytes <= 0L || usedBytes > totalBytes) {
            return 0L;
        }

        amountPerByte = Math.max(1L, amountPerByte);
        long unitsToCompleteBucket = targetBucketAmount <= 0L
            ? 0L
            : (amountPerByte - targetBucketAmount % amountPerByte) % amountPerByte;
        long freeBytes = totalBytes - usedBytes;
        return saturatingAdd(unitsToCompleteBucket, saturatingMultiply(freeBytes, amountPerByte));
    }

    private static long ceilDivide(long dividend, long divisor) {
        if (dividend <= 0L) {
            return 0L;
        }
        long quotient = dividend / divisor;
        return dividend % divisor == 0L ? quotient : saturatingAdd(quotient, 1L);
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private StorageSnapshot snapshot() {
        KeyCounter available = new KeyCounter();
        delegate.getAvailableStacks(available);
        return snapshot(available);
    }

    private static StorageSnapshot snapshot(KeyCounter available) {
        Long2LongOpenHashMap bucketSums = new Long2LongOpenHashMap();
        bucketSums.defaultReturnValue(0L);
        long storedTypes = 0L;
        for (var entry : available) {
            long amount = entry.getLongValue();
            if (amount <= 0L) {
                continue;
            }
            storedTypes = saturatingAdd(storedTypes, 1L);
            long amountPerByte = Math.max(1L, entry.getKey().getType().getAmountPerByte());
            bucketSums.put(amountPerByte, saturatingAdd(bucketSums.get(amountPerByte), amount));
        }
        return new StorageSnapshot(bucketSums, storedTypes, calculateUsedBytes(bucketSums));
    }

    private record StorageSnapshot(
        Long2LongOpenHashMap bucketSums,
        long storedTypes,
        long usedBytes
    ) {}

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(stack)) return 0L;
        return delegate.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember.isSealed(stack)) return;
        delegate.getAvailableStacks(out);
    }

    public void getMigrationStacks(KeyCounter out) { delegate.getAvailableStacks(out); }

    public void clearMigrationStacks() {
        KeyCounter contents = new KeyCounter();
        delegate.getAvailableStacks(contents);
        for (var entry : contents) {
            long extracted = delegate.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, IActionSource.empty());
            if (extracted != entry.getLongValue()) throw new IllegalStateException("Incomplete universal cell migration cleanup");
        }
        delegate.persist();
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }
}
