package cn.dancingsnow.neoecoae.blocks;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.stream.Stream;

public class ECOIntegratedWorkingStation extends AEBaseEntityBlock<ECOIntegratedWorkingStationBlockEntity> implements BlockUIMenuType.BlockUI {
    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final Property<Boolean> WORKING = BooleanProperty.create("working");

    private static final VoxelShape NORTH = Stream.of(
        Block.box(4, 4, 1, 12, 15, 12),
        Block.box(0, 0, 1, 16, 15, 16),
        Block.box(0, 0, 0, 16, 4, 1),
        Block.box(0, 4, 0, 4, 16, 1),
        Block.box(12, 4, 0, 16, 16, 1),
        Block.box(0, 15, 12, 16, 16, 16),
        Block.box(12, 15, 1, 16, 16, 12),
        Block.box(0, 15, 1, 4, 16, 12)
    ).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();

    private static final VoxelShape EAST = Stream.of(
        Block.box(4, 4, 4, 15, 15, 12),
        Block.box(0, 0, 0, 15, 15, 16),
        Block.box(15, 0, 0, 16, 4, 16),
        Block.box(15, 4, 0, 16, 16, 4),
        Block.box(15, 4, 12, 16, 16, 16),
        Block.box(0, 15, 0, 4, 16, 16),
        Block.box(4, 15, 12, 15, 16, 16),
        Block.box(4, 15, 0, 15, 16, 4)
    ).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();

    private static final VoxelShape SOUTH = Stream.of(
        Block.box(4, 4, 4, 12, 15, 15),
        Block.box(0, 0, 0, 16, 15, 15),
        Block.box(0, 0, 15, 16, 4, 16),
        Block.box(12, 4, 15, 16, 16, 16),
        Block.box(0, 4, 15, 4, 16, 16),
        Block.box(0, 15, 0, 16, 16, 4),
        Block.box(0, 15, 4, 4, 16, 15),
        Block.box(12, 15, 4, 16, 16, 15)
    ).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();

    private static final VoxelShape WEST = Stream.of(
        Block.box(1, 4, 4, 12, 15, 12),
        Block.box(1, 0, 0, 16, 15, 16),
        Block.box(0, 0, 0, 1, 4, 16),
        Block.box(0, 4, 12, 1, 16, 16),
        Block.box(0, 4, 0, 1, 16, 4),
        Block.box(12, 15, 0, 16, 16, 16),
        Block.box(1, 15, 0, 12, 16, 4),
        Block.box(1, 15, 12, 12, 16, 16)
    ).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();
    


    public ECOIntegratedWorkingStation(Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(WORKING, false));
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WORKING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            BlockUIMenuType.openUI(serverPlayer, pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default ->  NORTH;
        };
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        if (holder.player.level().getBlockEntity(holder.pos) instanceof ECOIntegratedWorkingStationBlockEntity be) {
            return be.createUI(holder);
        }
        return null;
    }
}
