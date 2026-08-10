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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Delegates universal cell storage to AE2 OmniCells while exposing ECO drive metadata. */
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
        return delegate.canFitInsideCell();
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
        if (!item.isExternallyUnlimited()) {
            return delegate.insert(what, amount, mode, source);
        }

        long amountPerByte = Math.max(1L, what.getType().getAmountPerByte());
        long freeBytes = Math.max(0L, getTotalBytes() - getUsedBytes());
        long capacityBound = saturatingMultiply(freeBytes, amountPerByte);
        return delegate.insert(what, Math.min(amount, capacityBound), mode, source);
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

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
