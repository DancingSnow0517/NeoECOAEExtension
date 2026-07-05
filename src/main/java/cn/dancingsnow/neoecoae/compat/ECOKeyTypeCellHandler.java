package cn.dancingsnow.neoecoae.compat;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public abstract class ECOKeyTypeCellHandler implements IECOCellHandler {
    private final Supplier<? extends ECOCellType> cellType;
    private final Supplier<? extends AEKeyType> keyType;

    protected ECOKeyTypeCellHandler(Supplier<? extends ECOCellType> cellType, Supplier<? extends AEKeyType> keyType) {
        this.cellType = cellType;
        this.keyType = keyType;
    }

    @Override
    public boolean isCell(ItemStack stack) {
        if (stack.getItem() instanceof ECOStorageCellItem item) {
            return item.getCellType() == cellType.get() && item.getKeyType() == keyType.get();
        }
        return false;
    }

    @Override
    public @Nullable IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
        return ECOStorageCellItem.getCellInventory(is, host);
    }
}
