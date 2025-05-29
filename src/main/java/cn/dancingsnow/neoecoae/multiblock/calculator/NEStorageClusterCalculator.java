package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.blocks.entity.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class NEStorageClusterCalculator extends NEClusterCalculator<NEStorageCluster> {
    public NEStorageClusterCalculator(NEBlockEntity<NEStorageCluster, ?> t) {
        super(t);
    }

    @Override
    public NEStorageCluster createCluster(ServerLevel level, BlockPos min, BlockPos max) {
        return new NEStorageCluster(min, max);
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        BlockPos controllerBlockPos = null;
        for (BlockPos blockPos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockEntity(blockPos) instanceof ECOStorageSystemBlockEntity) {
                 controllerBlockPos = blockPos;
            }
        }
        if (controllerBlockPos == null) {
            return false;
        }
        return false;
    }
}
