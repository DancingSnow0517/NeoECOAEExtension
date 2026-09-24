package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Element entities delegate fixed-structure checks to the powered controller. */
public class NEIntegratedWorkingStationClusterCalculator extends NEClusterCalculator<NEIntegratedWorkingStationCluster> {
    public NEIntegratedWorkingStationClusterCalculator(NEBlockEntity<NEIntegratedWorkingStationCluster, ?> target) {
        super(target);
    }

    @Override protected int maxLength() { return 3; }

    @Override
    public void calculateMultiblock(ServerLevel level, BlockPos pos) {
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
            if (level.hasChunkAt(candidate)
                    && level.getBlockEntity(candidate) instanceof ECOLargeIntegratedWorkingStationBlockEntity controller) {
                controller.rebuildMultiblock();
            }
        }
    }

    @Override
    public void updateMultiblockAfterNeighborUpdate(ServerLevel level, BlockPos pos, BlockPos changedPos) {
        calculateMultiblock(level, pos);
    }

    @Override
    public NEIntegratedWorkingStationCluster createCluster(ServerLevel level, BlockPos min, BlockPos max) {
        return new NEIntegratedWorkingStationCluster(min, max);
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        int x = max.getX() - min.getX() + 1;
        int y = max.getY() - min.getY() + 1;
        int z = max.getZ() - min.getZ() + 1;
        return y == 2 && ((x == 3 && z == 2) || (x == 2 && z == 3));
    }

    @Override public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) { return false; }

    @Override
    public boolean isValidBlockEntity(BlockEntity blockEntity) {
        return blockEntity instanceof NEBlockEntity<?, ?> member
                && member.getCalculator() instanceof NEIntegratedWorkingStationClusterCalculator;
    }
}
