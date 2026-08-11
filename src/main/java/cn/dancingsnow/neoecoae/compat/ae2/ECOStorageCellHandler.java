package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes every ECO-managed storage cell to AE2 systems that query its storage-cell registry.
 *
 * <p>This is required by the Cell Workbench's partition action, which reads cell contents through
 * {@link appeng.api.storage.StorageCells} instead of ECO's internal cell registry.</p>
 */
public final class ECOStorageCellHandler implements ICellHandler {
    public static final ECOStorageCellHandler INSTANCE = new ECOStorageCellHandler();

    private ECOStorageCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack stack) {
        return ECOStorageCells.isCellHandled(stack);
    }

    @Override
    public @Nullable StorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        return ECOStorageCells.getCellInventory(stack, host);
    }
}
