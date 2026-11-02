package cn.dancingsnow.neoecoae.blocks;

import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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

public abstract class NENetworkSwitchBlock<T extends AENetworkedBlockEntity> extends AEBaseEntityBlock<T> {
    // Independent from NEBlock.FORMED: network switches are not in the NEBlock inheritance tree.
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    protected NENetworkSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FORMED, false));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide() && oldState.getBlock() != this) notifyAdjacentMultiblocks(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        // 26.1: only invoked once the block at pos has actually been replaced by a different block.
        notifyAdjacentMultiblocks(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
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
    protected RenderShape getRenderShape(BlockState state) { return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL; }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) { return false; }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return state.getValue(FORMED) ? 1 : 0.2f; }

    @Override
    protected int getLightDampening(BlockState state) { return state.getValue(FORMED) ? 0 : super.getLightDampening(state); }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) { return state.getValue(FORMED) ? Shapes.empty() : super.getOcclusionShape(state); }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return state.getValue(FORMED) ? Shapes.empty() : super.getVisualShape(state, level, pos, context); }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) { return state.getValue(FORMED); }
}
