package cn.dancingsnow.neoecoae.gui.computation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** One physical computation drive shown in a controller slot; position is null where no drive exists. */
public record ComputationDriveEntry(@Nullable BlockPos position, ItemStack stack) {
    public static final ComputationDriveEntry EMPTY = new ComputationDriveEntry(null, ItemStack.EMPTY);

    public ComputationDriveEntry {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }
}
