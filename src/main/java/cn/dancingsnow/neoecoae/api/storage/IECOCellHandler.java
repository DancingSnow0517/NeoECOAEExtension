package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.storage.cells.ISaveProvider;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface IECOCellHandler {

    boolean isCell(ItemStack stack);

    @Nullable IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host);

    /** Releases any runtime ownership held for a drive host that stopped exposing this cell. */
    default void releaseCellInventory(@Nullable ItemStack stack, @Nullable ISaveProvider host) {}

    /** Clears server-lifetime ownership state after the world has flushed. */
    default void clearRuntimeState() {}
}
