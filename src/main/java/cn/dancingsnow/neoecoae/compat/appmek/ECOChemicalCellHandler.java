package cn.dancingsnow.neoecoae.compat.appmek;

import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.compat.appmek.item.ECOChemicalStorageCellItem;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Applied Mekanistics-only storage cell handler.
 */
public final class ECOChemicalCellHandler implements IECOCellHandler {
    public static final ECOChemicalCellHandler INSTANCE = new ECOChemicalCellHandler();

    private ECOChemicalCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack stack) {
        if (stack.getItem() instanceof ECOStorageCellItem item) {
            var cellType = item.getCellType();
            boolean chemicalCellType = cellType == NEAppMekCellTypes.CHEMICAL.get()
                    || cellType.id().equals(NeoECOAE.id("chemicals"));
            boolean chemicalItem = item instanceof ECOChemicalStorageCellItem;
            return (chemicalItem || chemicalCellType)
                    && item.getKeyType() == AppMekCompat.getChemicalKeyType();
        }
        return false;
    }

    @Override
    public @Nullable IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
        return ECOStorageCellItem.getCellInventory(is, host);
    }
}
