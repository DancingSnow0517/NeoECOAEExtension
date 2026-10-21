package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.platform.NEEntityBlock;
import appeng.blockentity.grid.AENetworkBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class NENetworkSwitchBlock<T extends AENetworkBlockEntity> extends NEEntityBlock<T> {
    // Independent from NEBlock.FORMED: network switches are not in the NEBlock inheritance tree.
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    protected NENetworkSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FORMED, false));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && oldState.getBlock() != this) notifyAdjacentMultiblocks(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && newState.getBlock() != this) notifyAdjacentMultiblocks(level, pos);
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static void notifyAdjacentMultiblocks(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockEntity blockEntity = level.getBlockEntity(pos.relative(direction));
            if (blockEntity instanceof NEBlockEntity<?, ?> multiblock) multiblock.updateMultiBlock(pos);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL; }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) { return false; }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return state.getValue(FORMED) ? 1 : 0.2f; }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) { return state.getValue(FORMED) ? 0 : super.getLightBlock(state, level, pos); }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return state.getValue(FORMED) ? Shapes.empty() : super.getOcclusionShape(state, level, pos); }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return state.getValue(FORMED) ? Shapes.empty() : super.getVisualShape(state, level, pos, context); }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return state.getValue(FORMED); }
}
