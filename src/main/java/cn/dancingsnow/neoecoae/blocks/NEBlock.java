package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.platform.NEEntityBlock;
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

public abstract class NEBlock<T extends NEBlockEntity<?, T>> extends NEEntityBlock<T> {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    protected NEBlock(Properties properties) {
        // Emitting even one level of light disables the default model's ambient occlusion.
        // Formation alone must not change how the machine body receives environmental light.
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FORMED, false));
    }

    protected boolean hideWhenFormed() {
        return false;
    }

    private boolean isVisuallyHidden(BlockState state) {
        return hideWhenFormed() && state.getValue(FORMED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        if (!hideWhenFormed()) {
            return super.getRenderShape(state);
        }
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return hideWhenFormed() ? state.getValue(FORMED) : super.skipRendering(state, adjacentState, direction);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        if (!hideWhenFormed()) {
            return super.getShadeBrightness(state, level, pos);
        }
        return state.getValue(FORMED) ? 1.0F : 0.2F;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isVisuallyHidden(state) ? Shapes.empty() : super.getVisualShape(state, level, pos, context);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return hideWhenFormed()
            ? state.getValue(FORMED)
            : super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return getRenderShape(state) == RenderShape.INVISIBLE ? 0 : super.getLightBlock(state, level, pos);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getRenderShape(state) == RenderShape.INVISIBLE
            ? Shapes.empty()
            : super.getOcclusionShape(state, level, pos);
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
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && serverLevel.getServer().isStopped()) {
            return;
        }
        final NEBlockEntity<?, T> be = this.getBlockEntity(level, pos);
        if (be != null) {
            be.updateMultiBlock(neighborPos);
        }
    }
}
