package cn.dancingsnow.neoecoae.blocks;

import appeng.block.AEBaseEntityBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class NEBlock<T extends NEBlockEntity<?, T>> extends AEBaseEntityBlock<T> {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    protected NEBlock(Properties properties) {
        super(properties.lightLevel(state -> state.getValue(FORMED) ? 1 : 0));
        registerDefaultState(getStateDefinition().any().setValue(FORMED, false));
    }

    protected boolean hideWhenFormed() {
        return false;
    }

    private boolean isVisuallyHidden(BlockState state) {
        return hideWhenFormed() && state.getValue(FORMED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        if (!hideWhenFormed()) {
            return super.getRenderShape(state);
        }
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return hideWhenFormed() ? state.getValue(FORMED) : super.skipRendering(state, adjacentState, direction);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        if (!hideWhenFormed()) {
            return super.getShadeBrightness(state, level, pos);
        }
        return state.getValue(FORMED) ? 1.0F : 0.2F;
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isVisuallyHidden(state) ? Shapes.empty() : super.getVisualShape(state, level, pos, context);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return hideWhenFormed()
            ? state.getValue(FORMED)
            : super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (newState.getBlock() == state.getBlock()) {
            return; // Just a block state change
        }

        final T cp = this.getBlockEntity(level, pos);
        if (cp != null) {
            cp.breakCluster();
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && serverLevel.getServer().isStopped()) {
            return;
        }
        final NEBlockEntity<?, T> be = this.getBlockEntity(level, pos);
        if (be != null) {
            be.updateMultiBlock(neighborPos);
        }
    }
}
