package cn.dancingsnow.neoecoae.multiblock.placement;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockRequirement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public record WorldPlannedBlock(
    BlockPos worldPos,
    BlockState targetState,
    ItemStack requiredItem,
    ECOMultiblockRequirement requirement
) {
    public WorldPlannedBlock(BlockPos worldPos, BlockState targetState, ItemStack requiredItem) {
        this(worldPos, targetState, requiredItem, ECOMultiblockRequirement.of(targetState));
    }
}