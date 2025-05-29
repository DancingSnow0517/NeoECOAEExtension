package cn.dancingsnow.neoecoae.blocks;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import cn.dancingsnow.neoecoae.blocks.entity.MachineEnergyCellBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class MachineEnergyCell extends NEBlock<MachineEnergyCellBlockEntity> {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 4);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected MachineEnergyCell(Properties properties) {
        super(properties);
        registerDefaultState(
            getStateDefinition().any()
            .setValue(FORMED, false)
            .setValue(LEVEL, 0)
            .setValue(FACING, Direction.NORTH)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEVEL);
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState currentState, MachineEnergyCellBlockEntity be) {
        int value = (int) Math.floor((be.getAECurrentPower() / be.getAEMaxPower()) * 4);
        return currentState.setValue(LEVEL, value);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.horizontalFacing();
    }
}
