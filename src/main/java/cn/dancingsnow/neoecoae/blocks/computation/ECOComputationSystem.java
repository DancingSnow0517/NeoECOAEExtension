package cn.dancingsnow.neoecoae.blocks.computation;

import cn.dancingsnow.neoecoae.blocks.AbstractECOSystemBlock;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class ECOComputationSystem extends AbstractECOSystemBlock<ECOComputationSystemBlockEntity> {
    public static final BooleanProperty NETWORK_SWITCH = BooleanProperty.create("network_switch");
    public static final BooleanProperty HIGH_ENERGY_NETWORK_SWITCH =
            BooleanProperty.create("high_energy_network_switch");

    public ECOComputationSystem(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition()
                .any()
                .setValue(FORMED, false)
                .setValue(MIRRORED, false)
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(NETWORK_SWITCH, false)
                .setValue(HIGH_ENERGY_NETWORK_SWITCH, false));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NETWORK_SWITCH, HIGH_ENERGY_NETWORK_SWITCH);
    }
}
