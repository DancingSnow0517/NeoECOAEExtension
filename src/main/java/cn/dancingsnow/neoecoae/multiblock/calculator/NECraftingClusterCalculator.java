package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class NECraftingClusterCalculator extends NEClusterCalculator<NECraftingCluster> {
    public NECraftingClusterCalculator(NEBlockEntity<NECraftingCluster, ?> t) {
        super(t);
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        return false;
    }

    @Override
    public NECraftingCluster createCluster(ServerLevel level, BlockPos min, BlockPos max) {
        return new NECraftingCluster(min, max);
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        return false;
    }
}
