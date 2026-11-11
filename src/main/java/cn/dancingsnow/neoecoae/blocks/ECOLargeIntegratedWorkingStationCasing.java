package cn.dancingsnow.neoecoae.blocks;

import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Synchronizes which upper casings accept side connections before the client has a cluster. */
public class ECOLargeIntegratedWorkingStationCasing extends ECOMachineCasing<NEIntegratedWorkingStationCluster> {
    public static final BooleanProperty POWER_PORT = BooleanProperty.create("power_port");

    public ECOLargeIntegratedWorkingStationCasing(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWER_PORT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER_PORT);
    }
}
