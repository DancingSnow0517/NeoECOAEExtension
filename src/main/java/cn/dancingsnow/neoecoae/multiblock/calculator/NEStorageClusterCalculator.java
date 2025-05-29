package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.util.MultiBlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

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
        int xSize = max.getX() - min.getX() + 1;
        int ySize = max.getY() - min.getY() + 1;
        int zSize = max.getZ() - min.getZ() + 1;

        if (ySize != 3) {
            return false;
        }
        ECOStorageSystemBlockEntity controller = null;
        BlockPos controllerPos = null;
        for (BlockPos pos : MultiBlockUtil.allPossibleController(min, max)) {
            if (level.getBlockEntity(pos) instanceof ECOStorageSystemBlockEntity be) {
                controller = be;
                controllerPos = pos;
                break;
            }
        }
        if (controller == null) return false;
        BlockState controllerState = controller.getBlockState();
        IOrientationStrategy strategy = OrientationStrategies.horizontalFacing();
        Direction back = strategy.getSide(controllerState, RelativeSide.BACK);
        Direction top = strategy.getSide(controllerState, RelativeSide.TOP);
        Direction down = top.getOpposite();
        Direction left = strategy.getSide(controllerState, RelativeSide.LEFT);
        Direction right = left.getOpposite();
        if (validateCasing(level, controllerPos, top, down, left)) return false;
        if (validateCasing(level, controllerPos, top, down, back)) return false;
        if (validateInterface(level, controllerPos.relative(left).relative(back), top, down)) return false;
        if (!validateBlock(level, controllerPos.relative(top), BlockState::is, NEBlocks.STORAGE_CASING)) {
            return false;
        }
        if (!validateBlock(level, controllerPos.relative(down), BlockState::is, NEBlocks.STORAGE_CASING)) {
            return false;
        }
        BlockPos storageBlocksStart = controllerPos.relative(right).relative(top);
        BlockPos storageBlocksEnd = expandTowards(
            level,
            right,
            controllerPos.relative(right).relative(down),
            NEBlocks.ECO_DRIVE
        );
        if (!validateBlocks(level, storageBlocksStart, storageBlocksEnd, BlockState::is, NEBlocks.ECO_DRIVE)) {
            return false;
        }
        BlockPos ventStart = controllerPos.relative(right).relative(back);
        BlockPos ventEnd = expandTowards(
            level,
            right,
            ventStart,
            NEBlocks.STORAGE_VENT
        );
        if (ventStart.equals(ventEnd)) {
            if (validateBlock(level, ventStart, BlockState::is, NEBlocks.ECO_DRIVE)) {

            }
        }
        return false;
    }

    private boolean validateCasing(ServerLevel level, BlockPos controllerPos, Direction top, Direction down, Direction direction) {
        if (!validateBlock(level, controllerPos.relative(direction), BlockState::is, NEBlocks.STORAGE_CASING)) {
            return true;
        }
        if (!validateBlock(level, controllerPos.relative(direction).relative(top), BlockState::is, NEBlocks.STORAGE_CASING)) {
            return true;
        }
        return !validateBlock(level, controllerPos.relative(direction).relative(down), BlockState::is, NEBlocks.STORAGE_CASING);
    }

    private boolean validateInterface(ServerLevel level, BlockPos interfacePos, Direction top, Direction down) {
        if (!validateBlock(level, interfacePos, BlockState::is, NEBlocks.STORAGE_CASING)) {
            return true;
        }
        if (!validateBlock(level, interfacePos.relative(top), BlockState::is, NEBlocks.STORAGE_INTERFACE)) {
            return true;
        }
        return !validateBlock(level, interfacePos.relative(down), BlockState::is, NEBlocks.STORAGE_CASING);
    }
}
