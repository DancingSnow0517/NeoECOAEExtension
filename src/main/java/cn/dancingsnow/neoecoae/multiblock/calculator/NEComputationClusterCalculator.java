package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class NEComputationClusterCalculator extends NEClusterCalculator<NEComputationCluster> {
    public NEComputationClusterCalculator(NEBlockEntity<NEComputationCluster, ?> t) {
        super(t);
    }

    @Override
    protected int maxLength() {
        return 16;
    }

    @Override
    public NEComputationCluster createCluster(ServerLevel level, BlockPos min, BlockPos max) {
        return new NEComputationCluster(min, max);
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        return false;
    }
}
