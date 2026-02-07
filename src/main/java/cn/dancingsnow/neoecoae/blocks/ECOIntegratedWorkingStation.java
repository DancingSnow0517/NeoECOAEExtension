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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

public class ECOIntegratedWorkingStation extends AEBaseEntityBlock<ECOIntegratedWorkingStationBlockEntity> implements BlockUIMenuType.BlockUI{
    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final Property<Boolean> WORKING = BooleanProperty.create("working");


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
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        if (holder.player.level().getBlockEntity(holder.pos) instanceof ECOIntegratedWorkingStationBlockEntity be) {
            return be.createUI(holder);
        }
        return null;
    }
}
