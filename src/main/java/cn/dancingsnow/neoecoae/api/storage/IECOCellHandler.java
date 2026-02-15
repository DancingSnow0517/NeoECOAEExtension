package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.storage.cells.ISaveProvider;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface IECOCellHandler {

    boolean isCell(ItemStack stack);

    @Nullable IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host);
}
