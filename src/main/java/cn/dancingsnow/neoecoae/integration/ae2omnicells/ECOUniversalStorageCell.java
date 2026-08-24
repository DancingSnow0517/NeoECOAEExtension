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
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Delegates universal cell storage to AE2 OmniCells while exposing ECO drive metadata. */
public final class ECOUniversalStorageCell implements IECOStorageCell {
    private final StorageCell delegate;
    private final ECOUniversalStorageCellItem item;

    public ECOUniversalStorageCell(StorageCell delegate, ItemStack stack, ECOUniversalStorageCellItem item) {
        this.delegate = delegate;
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
        return snapshot().storedTypes();
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
        return snapshot().usedBytes();
    }

    @Override
    public long getTotalBytes() {
        return item.getECOStorageTotalBytes();
    }

    @Override
    public CellState getStatus() {
        if (!item.isExternallyUnlimited()) {
            return delegate.getStatus();
        }

        StorageSnapshot current = snapshot();
        if (current.storedTypes() == 0L) {
            return CellState.EMPTY;
        }

        if (current.usedBytes() > getTotalBytes()) {
            return CellState.FULL;
        }

        boolean hasPartialBucket = false;
        for (Long2LongMap.Entry entry : current.bucketSums().long2LongEntrySet()) {
            if (entry.getLongValue() > 0L && entry.getLongValue() % entry.getLongKey() != 0L) {
                hasPartialBucket = true;
                break;
            }
        }
        boolean hasFreeBytes = current.usedBytes() < getTotalBytes();
        boolean canHoldNewType = item.getTotalTypes() < 0 || current.storedTypes() < item.getTotalTypes();
        if (canHoldNewType && (hasFreeBytes || hasPartialBucket)) {
            return CellState.NOT_EMPTY;
        }
        return hasFreeBytes || hasPartialBucket ? CellState.TYPES_FULL : CellState.FULL;
    }

    @Override
    public double getIdleDrain() {
        return delegate.getIdleDrain();
    }

    @Override
    public boolean canFitInsideCell() {
        // OmniCells' delegate reports true unconditionally, which would let a fully loaded disk be disassembled
        // (stranding its SavedData contents behind a consumed UUID) or nested inside another cell. Mirror the
        // native ECO cell semantics instead: only an empty universal disk may be dismantled or nested.
        KeyCounter available = new KeyCounter();
        delegate.getAvailableStacks(available);
        return isEmptyStorage(available.size());
    }

    static boolean isEmptyStorage(long storedTypes) {
        return storedTypes == 0L;
    }

    /** Clears the SavedData-backed source only after its contents were durably copied into an infinite domain. */
    public boolean clearAllStoredStacksForMigration() {
        KeyCounter available = new KeyCounter();
        delegate.getAvailableStacks(available);
        for (var entry : available) {
            long amount = entry.getLongValue();
            if (amount > 0L
                    && delegate.extract(entry.getKey(), amount, Actionable.MODULATE, IActionSource.empty()) != amount) {
                return false;
            }
        }
        delegate.persist();
        return true;
    }

    public long simulateInsertForMigration(AEKey what, long amount, KeyCounter simulatedContents) {
        if (amount <= 0L) {
            return 0L;
        }
        StorageSnapshot current = snapshot(simulatedContents);
        long currentAmount = current.amounts().get(what);
        if (currentAmount <= 0L && item.getTotalTypes() >= 0 && current.storedTypes() >= item.getTotalTypes()) {
            return 0L;
        }
        long amountPerByte = Math.max(1L, what.getType().getAmountPerByte());
        long capacity = calculateRemainingAmount(
                getTotalBytes(), current.usedBytes(), current.bucketSums().get(amountPerByte), amountPerByte);
        return Math.min(amount, capacity);
    }

    public long getUsedBytesForMigration(KeyCounter simulatedContents) {
        return snapshot(simulatedContents).usedBytes();
    }

    @Override
    public void persist() {
        delegate.persist();
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        if (!item.isExternallyUnlimited()) {
            return delegate.isPreferredStorageFor(what, source);
        }
        return insert(what, 1L, Actionable.SIMULATE, source) > 0L
                || extract(what, 1L, Actionable.SIMULATE, source) > 0L;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!item.isExternallyUnlimited()) {
            return delegate.insert(what, amount, mode, source);
        }

        if (amount <= 0L) {
            return 0L;
        }

        StorageSnapshot current = snapshot();
        long currentAmount = current.amounts().get(what);
        if (currentAmount <= 0L && item.getTotalTypes() >= 0 && current.storedTypes() >= item.getTotalTypes()) {
            return 0L;
        }

        long amountPerByte = Math.max(1L, what.getType().getAmountPerByte());
        long targetBucketAmount = current.bucketSums().get(amountPerByte);
        long capacityBound =
                calculateRemainingAmount(getTotalBytes(), current.usedBytes(), targetBucketAmount, amountPerByte);
        return delegate.insert(what, Math.min(amount, capacityBound), mode, source);
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
        long unitsToCompleteBucket =
                targetBucketAmount <= 0L ? 0L : (amountPerByte - targetBucketAmount % amountPerByte) % amountPerByte;
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

    /** Rebuilds OmniCells' bucket accounting from the live SavedData-backed inventory. */
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
        return new StorageSnapshot(available, bucketSums, storedTypes, calculateUsedBytes(bucketSums));
    }

    private record StorageSnapshot(
            KeyCounter amounts, Long2LongOpenHashMap bucketSums, long storedTypes, long usedBytes) {}

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return delegate.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        delegate.getAvailableStacks(out);
    }

    @Override
    public Component getDescription() {
        return delegate.getDescription();
    }
}
