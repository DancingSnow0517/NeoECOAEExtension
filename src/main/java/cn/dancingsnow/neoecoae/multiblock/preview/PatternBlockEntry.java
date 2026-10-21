package cn.dancingsnow.neoecoae.multiblock.preview;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public record PatternBlockEntry(
        BlockPos relativePos, BlockState blockState, ItemStack requiredItem, boolean controller, int layerY) {
    public PatternBlockEntry {
        relativePos = relativePos.immutable();
        requiredItem = requiredItem.copy();
    }
}
