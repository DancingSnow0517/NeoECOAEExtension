package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Verifies the fixed 3x2x2 large integrated working station footprint. */
public class NEIntegratedWorkingStationClusterCalculator
    extends NEClusterCalculator<NEIntegratedWorkingStationCluster> {

    public NEIntegratedWorkingStationClusterCalculator(NEBlockEntity<NEIntegratedWorkingStationCluster, ?> target) {
        super(target);
    }

    @Override
    protected int maxLength() {
        return 3;
    }

    @Override
    protected Holder<Block> casing() {
        return NEBlocks.LARGE_INTEGRATED_WORKING_STATION_CASING;
    }

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
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;
        return sizeY == 2 && ((sizeX == 3 && sizeZ == 2) || (sizeX == 2 && sizeZ == 3));
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        // Controller validation is performed by the powered controller calculator. These
        // element entities intentionally never form a cluster on their own.
        return false;
    }

    @Override
    public boolean isValidBlockEntity(BlockEntity blockEntity) {
        return blockEntity instanceof NEBlockEntity<?, ?> ne
            && ne.getCalculator() instanceof NEIntegratedWorkingStationClusterCalculator;
    }
}
