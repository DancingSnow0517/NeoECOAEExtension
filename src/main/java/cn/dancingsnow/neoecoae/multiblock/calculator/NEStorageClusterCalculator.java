package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageVent;
import cn.dancingsnow.neoecoae.blocks.storage.MachineEnergyCell;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.util.MultiBlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

public class NEStorageClusterCalculator extends NEClusterCalculator<NEStorageCluster> {
    public NEStorageClusterCalculator(NEBlockEntity<NEStorageCluster, ?> t) {
        super(t);
    }

    @Override
    public NEStorageCluster createCluster(ServerLevel level, BlockPos min, BlockPos max) {
        return new NEStorageCluster(min, max);
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        if (sizeX > sizeZ) {
            return sizeX <= 15 && sizeY == 3 && sizeZ == 2;
        } else {
            return sizeZ <= 15 && sizeY == 3 && sizeX == 2;
        }
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        int ySize = max.getY() - min.getY() + 1;

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
        IECOTier tier = controller.getTier();
        BlockState controllerState = controller.getBlockState();
        IOrientationStrategy strategy = OrientationStrategies.horizontalFacing();
        Direction back = strategy.getSide(controllerState, RelativeSide.BACK);
        Direction front = back.getOpposite();
        Direction top = strategy.getSide(controllerState, RelativeSide.TOP);
        Direction down = top.getOpposite();
        Direction left = strategy.getSide(controllerState, RelativeSide.RIGHT);
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
            ((state, pos) -> state.is(NEBlocks.ECO_DRIVE)
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == front
            )
        );
        if (!validateBlocks(
            level,
            storageBlocksStart,
            storageBlocksEnd,
            state -> state.is(NEBlocks.ECO_DRIVE)
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == front
        )) {
            return false;
        }
        BlockPos ventStart = controllerPos.relative(right).relative(back);
        if (!validateBlock(
            level,
            ventStart,
            it -> it.is(NEBlocks.STORAGE_VENT)
                && it.getValue(ECOStorageVent.FACING) == back
        )) {
            return false;
        }
        BlockPos ventEnd = expandTowards(
            level,
            right,
            ventStart,
            it -> it.is(NEBlocks.STORAGE_VENT) &&it.getValue(ECOStorageVent.FACING) == back
        );
        if (ventStart.equals(ventEnd)) {
            if (!validateBlock(level, ventStart, BlockState::is, NEBlocks.STORAGE_VENT)
                && level.getBlockState(ventStart).getValue(ECOStorageVent.FACING) == back
            ) {
                return false;
            }
        }

        BlockPos upperEnergyCellStart = controllerPos.relative(back).relative(top).relative(right);
        if (!validateBlock(
            level,
            upperEnergyCellStart,
            state -> state.getBlock() instanceof MachineEnergyCell cell
                && cell.getBlockEntity(level, upperEnergyCellStart).getTier() == tier
                && state.getValue(MachineEnergyCell.FACING) == back
        )) {
            return false;
        }
        BlockPos upperEnergyCellEnd = expandTowards(
            level,
            right,
            upperEnergyCellStart,
            (state, pos) -> state.getBlock() instanceof MachineEnergyCell cell
                && cell.getBlockEntity(level, pos).getTier() == tier
                && state.getValue(MachineEnergyCell.FACING) == back
        );
        if (upperEnergyCellEnd.equals(upperEnergyCellStart)) {
            return validateBlock(
                level,
                upperEnergyCellStart,
                state -> state.getBlock() instanceof MachineEnergyCell cell
                    && cell.getBlockEntity(level, upperEnergyCellEnd).getTier() == tier
                    && state.getValue(MachineEnergyCell.FACING) == back
            );
        }

        BlockPos lowerEnergyCellStart = controllerPos.relative(back).relative(down).relative(right);
        if (!validateBlock(
            level,
            lowerEnergyCellStart,
            state -> state.getBlock() instanceof MachineEnergyCell cell
                && cell.getBlockEntity(level, lowerEnergyCellStart).getTier() == tier
                && state.getValue(MachineEnergyCell.FACING) == back
        )) {
            return false;
        }
        BlockPos lowerEnergyCellEnd = expandTowards(
            level,
            right,
            lowerEnergyCellStart,
            (state, pos) -> state.getBlock() instanceof MachineEnergyCell cell
                && cell.getBlockEntity(level, pos).getTier() == tier
                && state.getValue(MachineEnergyCell.FACING) == back
        );
        if (lowerEnergyCellEnd.equals(lowerEnergyCellStart)) {
            return validateBlock(
                level,
                lowerEnergyCellEnd,
                it -> it.getBlock() instanceof MachineEnergyCell cell
                    && cell.getBlockEntity(level, lowerEnergyCellEnd).getTier() == tier
                    && it.getValue(MachineEnergyCell.FACING) == back
            );
        }

        BlockPos.MutableBlockPos tailCasing = storageBlocksEnd.mutable().move(right).move(top);
        List<BlockPos> tailCasingPoses = List.of(
            upperEnergyCellEnd.relative(right),
            lowerEnergyCellEnd.relative(right),
            ventEnd.relative(right),
            tailCasing.immutable(),
            tailCasing.relative(top),
            tailCasing.relative(down)
        );
        if (!ensureSameSurface(tailCasingPoses)) {
            return false;
        }
        return validateBlocks(level, tailCasingPoses, BlockState::is, NEBlocks.STORAGE_CASING);
    }

    private boolean ensureSameSurface(List<BlockPos> list) {
        int x = list.getFirst().getX();
        int y = list.getFirst().getY();
        int z = list.getFirst().getZ();
        boolean sameX = true;
        boolean sameY = true;
        boolean sameZ = true;
        for (BlockPos blockPos : list) {
            if (blockPos.getX() != x) {
                sameX = false;
            }
            if (blockPos.getY() != y) {
                sameY = false;
            }
            if (blockPos.getZ() != z) {
                sameZ = false;
            }
            x = blockPos.getX();
            y = blockPos.getY();
            z = blockPos.getZ();
        }
        return sameX || sameY || sameZ;
    }

    private boolean validateEnergyCell(Level level, Direction direction, BlockPos start) {
        BlockPos end = expandTowards(
            level,
            direction,
            start,
            it -> it.getBlock() instanceof MachineEnergyCell
        );
        if (start.equals(end)) {
            return validateBlock(level, start, it -> it.getBlock() instanceof MachineEnergyCell);
        }
        return true;
    }


    private boolean validateCasing(ServerLevel level, BlockPos controllerPos, Direction top, Direction down, Direction direction) {
        return validateCasing(level, controllerPos.relative(direction), top, down);
    }

    private boolean validateCasing(ServerLevel level, BlockPos centerPos, Direction top, Direction down) {
        if (!validateBlock(level, centerPos, BlockState::is, NEBlocks.STORAGE_CASING)) {
            return true;
        }
        if (!validateBlock(level, centerPos.relative(top), BlockState::is, NEBlocks.STORAGE_CASING)) {
            return true;
        }
        return !validateBlock(level, centerPos.relative(down), BlockState::is, NEBlocks.STORAGE_CASING);
    }

    private boolean validateInterface(ServerLevel level, BlockPos interfacePos, Direction top, Direction down) {
        if (!validateBlock(level, interfacePos, BlockState::is, NEBlocks.STORAGE_INTERFACE)) {
            return true;
        }
        if (!validateBlock(level, interfacePos.relative(top), BlockState::is, NEBlocks.STORAGE_CASING)) {
            return true;
        }
        return !validateBlock(level, interfacePos.relative(down), BlockState::is, NEBlocks.STORAGE_CASING);
    }
}
