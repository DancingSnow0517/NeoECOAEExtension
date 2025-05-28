package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MachineInterfaceBlockEntity<C extends NECluster<C>> extends NEBlockEntity<C, MachineInterfaceBlockEntity<C>> {
    public MachineInterfaceBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        NEClusterCalculator.Factory<MachineInterfaceBlockEntity<C>, C> calculator
    ) {
        super(type, pos, blockState, calculator.create());
        onGridConnectableSidesChanged();
    }

}
