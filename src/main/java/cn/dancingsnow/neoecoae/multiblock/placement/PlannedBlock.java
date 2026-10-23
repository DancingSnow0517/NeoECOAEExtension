package cn.dancingsnow.neoecoae.multiblock.placement;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockRequirement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public record PlannedBlock(
    BlockPos relativePos,
    BlockState targetState,
    ItemStack requiredItem,
    ECOMultiblockRequirement requirement
) {
    public PlannedBlock(BlockPos relativePos, BlockState targetState, ItemStack requiredItem) {
        this(relativePos, targetState, requiredItem, ECOMultiblockRequirement.of(targetState));
    }
}