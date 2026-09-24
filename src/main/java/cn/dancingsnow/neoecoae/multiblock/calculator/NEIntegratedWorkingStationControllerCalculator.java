package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.me.cluster.MBCalculator;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Validates the fixed structure and publishes its members as one AE2 multiblock. */
public class NEIntegratedWorkingStationControllerCalculator
        extends MBCalculator<ECOLargeIntegratedWorkingStationBlockEntity, NEIntegratedWorkingStationCluster> {
    public NEIntegratedWorkingStationControllerCalculator(ECOLargeIntegratedWorkingStationBlockEntity target) {
        super(target);
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        int x = max.getX() - min.getX() + 1;
        int y = max.getY() - min.getY() + 1;
        int z = max.getZ() - min.getZ() + 1;
        return y == 2 && ((x == 3 && z == 2) || (x == 2 && z == 3));
    }

    @Override
    public NEIntegratedWorkingStationCluster createCluster(ServerLevel level, BlockPos min, BlockPos max) {
        return new NEIntegratedWorkingStationCluster(min, max);
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        BlockPos controller = target.getBlockPos();
        if (controller.getY() != max.getY()) return false;
        Direction front = target.getBlockState().getValue(ECOIntegratedWorkingStation.FACING);
        Direction back = front.getOpposite();
        Direction left = front.getCounterClockWise();
        Direction right = left.getOpposite();
        BlockPos lowerCenter = controller.below();
        BlockPos farCorner = lowerCenter.relative(back).relative(right);
        BlockPos nearCorner = lowerCenter.relative(left);
        if (Math.min(farCorner.getX(), nearCorner.getX()) != min.getX()
                || Math.max(farCorner.getX(), nearCorner.getX()) != max.getX()
                || Math.min(farCorner.getZ(), nearCorner.getZ()) != min.getZ()
                || Math.max(farCorner.getZ(), nearCorner.getZ()) != max.getZ()) return false;
        return casing(level, controller.relative(left))
                && casing(level, controller.relative(right))
                && casing(level, controller.relative(back))
                && casing(level, controller.relative(back).relative(left))
                && casing(level, controller.relative(back).relative(right))
                && casing(level, lowerCenter)
                && casing(level, lowerCenter.relative(left))
                && casing(level, lowerCenter.relative(right))
                && block(level, lowerCenter.relative(back), NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INTERFACE.get())
                && block(level, lowerCenter.relative(back).relative(left),
                        NEBlocks.LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH.get())
                && block(level, lowerCenter.relative(back).relative(right),
                        NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH.get());
    }

    private static boolean casing(ServerLevel level, BlockPos pos) {
        return block(level, pos, NEBlocks.LARGE_INTEGRATED_WORKING_STATION_CASING.get());
    }

    private static boolean block(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block expected) {
        return level.getBlockState(pos).is(expected);
    }

    @Override
    public void updateBlockEntities(NEIntegratedWorkingStationCluster cluster, ServerLevel level, BlockPos min, BlockPos max) {
        Set<NEIntegratedWorkingStationCluster> previous = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (!isValidBlockEntity(entity)) {
                disconnect();
                return;
            }
            if (entity instanceof NEBlockEntity<?, ?> member) {
                @SuppressWarnings("unchecked")
                NEBlockEntity<NEIntegratedWorkingStationCluster, ?> typed =
                        (NEBlockEntity<NEIntegratedWorkingStationCluster, ?>) member;
                cluster.addBlockEntity(typed);
                var old = typed.getCluster();
                if (old != null && old != cluster && !old.isDestroyed()) previous.add(old);
            } else if (entity instanceof ECOLargeIntegratedWorkingStationBlockEntity controller) {
                cluster.setController(controller);
                var old = controller.getCluster();
                if (old != null && old != cluster && !old.isDestroyed()) previous.add(old);
            }
        }
        for (var old : previous) old.destroy();
        cluster.getBlockEntities().forEachRemaining(member -> member.updateCluster(cluster));
        target.updateCluster(cluster);
        cluster.updateFormed(true);
        cluster.updateStatus(true);
    }

    @Override
    public boolean isValidBlockEntity(BlockEntity entity) {
        return entity instanceof ECOLargeIntegratedWorkingStationBlockEntity
                || entity instanceof NEBlockEntity<?, ?> member
                        && member.getCalculator() instanceof NEIntegratedWorkingStationClusterCalculator;
    }
}
