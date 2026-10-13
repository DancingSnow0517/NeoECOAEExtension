package cn.dancingsnow.neoecoae.blocks.entity;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class ECOParallelCoreBlockEntity<C extends NECluster<C>> extends NEBlockEntity<C, ECOParallelCoreBlockEntity<C>> {

    @Getter
    private final IECOTier tier;

    public ECOParallelCoreBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier,
        NEClusterCalculator.Factory<C> calculator,
        double idlePowerUsage
    ) {
        super(type, pos, blockState, calculator, idlePowerUsage);
        this.tier = tier;
    }
}
