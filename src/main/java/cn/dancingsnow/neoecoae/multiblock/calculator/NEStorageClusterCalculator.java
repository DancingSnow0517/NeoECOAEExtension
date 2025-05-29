package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.blocks.entity.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.util.MultiBlockUtil;
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
        int xSize = max.getX() - min.getX();
        int ySize = max.getY() - min.getY();
        int zSize = max.getZ() - min.getZ();

        if (ySize != 3) {
            return false;
        }
        ECOStorageSystemBlockEntity controller;
        for (BlockPos pos : MultiBlockUtil.allPossibleController(min, max)) {
            if (level.getBlockEntity(pos) instanceof ECOStorageSystemBlockEntity be) {
                controller = be;
                break;
            }
        }

        return false;
    }
}
