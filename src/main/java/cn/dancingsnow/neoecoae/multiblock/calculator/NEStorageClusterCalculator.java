package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageVentBlock;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.util.MultiBlockUtil;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public class NEStorageClusterCalculator extends NEClusterCalculator<NEStorageCluster> {
    public NEStorageClusterCalculator(NEBlockEntity<NEStorageCluster, ?> t) {
        super(t);
    }

    @Override
    public NEStorageCluster createCluster(ServerLevel level, BlockPos min, BlockPos max) {
        return new NEStorageCluster(min, max);
    }

    @Override
    protected int maxLength() {
        return NEConfig.storageSystemMaxLength;
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {

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

        if (!validateCasing(level, controllerPos, top, down, left)) return false;
        if (!validateCasing(level, controllerPos, top, down, back)) return false;
        if (!validateInterface(level, controllerPos.relative(left).relative(back), top, down)) return false;
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
        DataResult<BlockPos> ventEndResult = validateBlockLine(
            level,
            right,
            ventStart,
            (it, pos) -> it.is(NEBlocks.STORAGE_VENT)
                && it.getValue(ECOStorageVentBlock.FACING) == back
        );
        if (ventEndResult.isError()) {
            return false;
        }
        BlockPos ventEnd = ventEndResult.getOrThrow();

        BlockPos upperEnergyCellStart = controllerPos.relative(back).relative(top).relative(right);
        DataResult<BlockPos> upperEnergyCellResult = validateBlockLine(
            level,
            right,
            upperEnergyCellStart,
            (state, pos) -> state.getBlock() instanceof ECOEnergyCellBlock cell
                && cell.getBlockEntity(level, pos).getTier() == tier
                && state.getValue(ECOEnergyCellBlock.FACING) == back
        );
        if (upperEnergyCellResult.isError()) {
            return false;
        }
        BlockPos upperEnergyCellEnd = upperEnergyCellResult.getOrThrow();
        if (upperEnergyCellEnd.equals(upperEnergyCellStart)) {
            return validateBlock(
                level,
                upperEnergyCellStart,
                state -> state.getBlock() instanceof ECOEnergyCellBlock cell
                    && cell.getBlockEntity(level, upperEnergyCellEnd).getTier() == tier
                    && state.getValue(ECOEnergyCellBlock.FACING) == back
            );
        }
        BlockPos lowerEnergyCellStart = controllerPos.relative(back).relative(down).relative(right);
        DataResult<BlockPos> lowerEnergyCellResult = validateBlockLine(
            level,
            right,
            lowerEnergyCellStart,
            (state, pos) -> state.getBlock() instanceof ECOEnergyCellBlock cell
                && cell.getBlockEntity(level, pos).getTier() == tier
                && state.getValue(ECOEnergyCellBlock.FACING) == back
        );
        if (lowerEnergyCellResult.isError()) {
            return false;
        }
        BlockPos lowerEnergyCellEnd = lowerEnergyCellResult.getOrThrow();

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

    private boolean validateCasing(ServerLevel level, BlockPos controllerPos, Direction top, Direction down, Direction direction) {
        return validateCasing(level, controllerPos.relative(direction), top, down);
    }

    private boolean validateCasing(ServerLevel level, BlockPos centerPos, Direction top, Direction down) {
        return validateCasing(level, centerPos, top, down, NEBlocks.STORAGE_CASING);
    }

    private boolean validateInterface(ServerLevel level, BlockPos interfacePos, Direction top, Direction down) {
        return validateInterface(level, interfacePos, top, down, NEBlocks.STORAGE_INTERFACE, NEBlocks.STORAGE_CASING);
    }
}
