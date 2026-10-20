package cn.dancingsnow.neoecoae.blocks;

import appeng.block.AEBaseBlock;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;

public class BuddingEnergizedCrystalBlock extends AEBaseBlock {
    public static final int GROWTH_CHANCE = 5;
    public static final int DECAY_CHANCE = 12;
    private static final Direction[] DIRECTIONS = Direction.values();

    public BuddingEnergizedCrystalBlock(Properties props) {
        super(props);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.DESTROY;
    }

    public static boolean canClusterGrowAtState(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) && state.getFluidState().getAmount() == 8;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(GROWTH_CHANCE) != 0) {
            return;
        }

        // Try to grow cluster
        Direction direction = Util.getRandom(DIRECTIONS, random);
        BlockPos targetPos = pos.relative(direction);
        BlockState targetState = level.getBlockState(targetPos);
        Block newCluster = null;
        if (canClusterGrowAtState(targetState)) {
            newCluster = NEBlocks.SMALL_ENERGIZED_CRYSTAL_BUD.get();
        } else if (targetState.is(NEBlocks.SMALL_ENERGIZED_CRYSTAL_BUD)
            && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            newCluster = NEBlocks.MEDIUM_ENERGIZED_CRYSTAL_BUD.get();
        } else if (targetState.is(NEBlocks.MEDIUM_ENERGIZED_CRYSTAL_BUD)
            && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            newCluster = NEBlocks.LARGE_ENERGIZED_CRYSTAL_BUD.get();
        } else if (targetState.is(NEBlocks.LARGE_ENERGIZED_CRYSTAL_BUD)
            && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            newCluster = NEBlocks.ENERGIZED_CRYSTAL_CLUSTER.get();
        }

        if (newCluster == null) {
            return;
        }

        // Grow certus crystal
        BlockState newClusterState = newCluster.defaultBlockState()
            .setValue(AmethystClusterBlock.FACING, direction)
            .setValue(AmethystClusterBlock.WATERLOGGED, targetState.getFluidState().getType() == Fluids.WATER);
        level.setBlockAndUpdate(targetPos, newClusterState);

        // Damage the budding certus block after a successful growth
        if (this == NEBlocks.FLAWLESS_BUDDING_ENERGIZED_CRYSTAL.get() || random.nextInt(DECAY_CHANCE) != 0) {
            return;
        }
        Block newBlock;
        if (this == NEBlocks.FLAWED_BUDDING_ENERGIZED_CRYSTAL.get()) {
            newBlock = NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL.get();
        } else if (this == NEBlocks.CHIPPED_BUDDING_ENERGIZED_CRYSTAL.get()) {
            newBlock = NEBlocks.DAMAGED_BUDDING_ENERGIZED_CRYSTAL.get();
        } else if (this == NEBlocks.DAMAGED_BUDDING_ENERGIZED_CRYSTAL.get()) {
            newBlock = NEBlocks.ENERGIZED_CRYSTAL_BLOCK.get();
        } else {
            throw new IllegalStateException("Unexpected block: " + this);
        }
        level.setBlockAndUpdate(pos, newBlock.defaultBlockState());
    }
}
