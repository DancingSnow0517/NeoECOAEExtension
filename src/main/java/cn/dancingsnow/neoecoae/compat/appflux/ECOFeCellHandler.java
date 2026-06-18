package cn.dancingsnow.neoecoae.compat.appflux;

import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.all.NECellTypes;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ECOFeCellHandler implements IECOCellHandler {
    public static final ECOFeCellHandler INSTANCE = new ECOFeCellHandler();

    private ECOFeCellHandler() {}

    @Override
    public boolean isCell(ItemStack stack) {
        if (stack.getItem() instanceof ECOStorageCellItem item) {
            return item.getCellType() == NECellTypes.FE.get() && item.getKeyType() == AppFluxCompat.getFluxKeyType();
        }
        return false;
    }

    @Override
    public @Nullable IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
        return ECOStorageCellItem.getCellInventory(is, host);
    }
}
