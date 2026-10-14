package cn.dancingsnow.neoecoae.blocks.entity.computation;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.ECOParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Binary entry point for addons whose shadow cores extend the original computation core. */
@Deprecated(forRemoval = false)
public class ECOComputationParallelCoreBlockEntity extends ECOParallelCoreBlockEntity<NEComputationCluster> {
    public ECOComputationParallelCoreBlockEntity(
        BlockEntityType<?> type, BlockPos pos, BlockState state, IECOTier tier
    ) {
        super(type, pos, state, tier, NEComputationClusterCalculator::new, 16);
    }
}
