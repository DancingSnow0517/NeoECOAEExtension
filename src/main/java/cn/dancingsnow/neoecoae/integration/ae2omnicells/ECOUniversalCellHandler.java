package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.integration.ae2omnicells.item.ECOUniversalStorageCellItem;
import com.wintercogs.ae2omnicells.common.me.AEUniversalCellHandler;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ECOUniversalCellHandler implements IECOCellHandler {
    public static final ECOUniversalCellHandler INSTANCE = new ECOUniversalCellHandler();

    private ECOUniversalCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack stack) {
        return stack.getItem() instanceof ECOUniversalStorageCellItem && stack.getCount() == 1;
    }

    @Override
    public @Nullable IECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        if (!(stack.getItem() instanceof ECOUniversalStorageCellItem item) || stack.getCount() != 1) {
            return null;
        }
        StorageCell delegate = AEUniversalCellHandler.INSTANCE.getCellInventory(stack, host);
        return delegate == null ? null : new ECOUniversalStorageCell(delegate, stack, item);
    }
}
