package cn.dancingsnow.neoecoae.compat.appbot;

import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.all.NECellTypes;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ECOManaCellHandler implements IECOCellHandler {
    public static final ECOManaCellHandler INSTANCE = new ECOManaCellHandler();

    private ECOManaCellHandler() {}

    @Override
    public boolean isCell(ItemStack stack) {
        if (stack.getItem() instanceof ECOStorageCellItem item) {
            return item.getCellType() == NECellTypes.MANA.get() && item.getKeyType() == AppBotCompat.getManaKeyType();
        }
        return false;
    }

    @Override
    public @Nullable IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
        return ECOStorageCellItem.getCellInventory(is, host);
    }
}
