package cn.dancingsnow.neoecoae.multiblock.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public record PlannedBlock(
    BlockPos relativePos,
    BlockState targetState,
    ItemStack requiredItem
) {
}