package cn.dancingsnow.neoecoae.all;

import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class NEMultiBlocks {
    public static final List<MultiBlockDefinition> DEFINITIONS = new ArrayList<>();

    public static final MultiBlockDefinition TEST = MultiBlockDefinition.builder(NEBlocks.COMPUTATION_SYSTEM_L4)
        .setBlock(BlockPos.ZERO, NEBlocks.COMPUTATION_DRIVE.getDefaultState())
        .setBlock(BlockPos.ZERO.relative(Direction.UP), NEBlocks.COMPUTATION_SYSTEM_L4.getDefaultState())
        .setBlockRepeatable(new BlockPos(0, 2, 0), Direction.EAST, Blocks.GOLD_BLOCK.defaultBlockState())
        .setBlockWithRepeatShifted(new BlockPos(0,2,0), Direction.EAST, 0, Blocks.DIAMOND_BLOCK.defaultBlockState())
        .expandMax(5)
        .expandMin(1)
        .create(DEFINITIONS::add);
}
