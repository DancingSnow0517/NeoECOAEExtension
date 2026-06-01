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
import net.minecraft.world.level.block.entity.BlockEntity;
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
        return verifyInternalStructure(level, min, max, false) || verifyInternalStructure(level, min, max, true);
    }

    private boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max, boolean mirrored) {
        ECOStorageSystemBlockEntity controller = null;
        BlockPos controllerPos = null;
        for (BlockPos pos : MultiBlockUtil.allPossibleController(min, max)) {
            if (level.getBlockEntity(pos) instanceof ECOStorageSystemBlockEntity be) {
                controller = be;
                controllerPos = pos;
                break;
            }
        }
        if (controller == null) {
            logVerifyFailure(level, min, max, "controller", min, "ECOStorageSystemBlockEntity in allPossibleController(min,max)", null);
            return false;
        }
        IECOTier tier = controller.getTier();
        BlockState controllerState = controller.getBlockState();
        IOrientationStrategy strategy = OrientationStrategies.horizontalFacing();
        Direction back = strategy.getSide(controllerState, RelativeSide.BACK);
        Direction front = back.getOpposite();
        Direction top = strategy.getSide(controllerState, RelativeSide.TOP);
        Direction down = top.getOpposite();
        Direction left = strategy.getSide(controllerState, RelativeSide.RIGHT);
        Direction right = left.getOpposite();
        if (mirrored) {
            Direction tmp = left;
            left = right;
            right = tmp;
        }
        logVerifyContext(level, min, max, controllerPos, controllerState, front, back, left, right, top, down);

        if (!validateCasing(level, controllerPos, top, down, left)) {
            logCasingColumn(level, min, max, "controller left casing", controllerPos.relative(left), top, down);
            return false;
        }
        if (!validateCasing(level, controllerPos, top, down, back)) {
            logCasingColumn(level, min, max, "controller back casing", controllerPos.relative(back), top, down);
            return false;
        }
        if (!validateInterface(level, controllerPos.relative(left).relative(back), top, down)) {
            logInterfaceStack(level, min, max, "interface stack", controllerPos.relative(left).relative(back), top, down, "STORAGE_INTERFACE", "STORAGE_CASING");
            return false;
        }
        if (!validateBlock(level, controllerPos.relative(top), BlockState::is, NEBlocks.STORAGE_CASING.get())) {
            logVerifyFailure(level, min, max, "controller top casing", controllerPos.relative(top), "STORAGE_CASING", null);
            return false;
        }
        if (!validateBlock(level, controllerPos.relative(down), BlockState::is, NEBlocks.STORAGE_CASING.get())) {
            logVerifyFailure(level, min, max, "controller down casing", controllerPos.relative(down), "STORAGE_CASING", null);
            return false;
        }
        BlockPos storageBlocksStart = controllerPos.relative(right).relative(top);
        BlockPos storageBlocksEnd = expandTowards(
            level,
            right,
            controllerPos.relative(right).relative(down),
            ((state, pos) -> state.is(NEBlocks.ECO_DRIVE.get())
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == front
            )
        );
        if (!validateBlocks(
            level,
            storageBlocksStart,
            storageBlocksEnd,
            state -> state.is(NEBlocks.ECO_DRIVE.get())
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == front
        )) {
            logBlockVolumeFailure(level, min, max, "storage drive rectangle", storageBlocksStart, storageBlocksEnd, "ECO_DRIVE facing " + front, front);
            return false;
        }
        BlockPos ventStart = controllerPos.relative(right).relative(back);
        DataResult<BlockPos> ventEndResult = validateBlockLine(
            level,
            right,
            ventStart,
            (it, pos) -> it.is(NEBlocks.STORAGE_VENT.get())
                && it.getValue(ECOStorageVentBlock.FACING) == back
        );
        if (ventEndResult.error().isPresent()) {
            logVerifyFailure(level, min, max, "storage vent line", ventStart, "STORAGE_VENT facing " + back, back);
            logLineProbe(level, min, max, "storage vent line", ventStart, right, 8);
            return false;
        }
        BlockPos ventEnd = ventEndResult.getOrThrow(false, ignored -> {});

        BlockPos upperEnergyCellStart = controllerPos.relative(back).relative(top).relative(right);
        DataResult<BlockPos> upperEnergyCellResult = validateBlockLine(
            level,
            right,
            upperEnergyCellStart,
            (state, pos) -> state.getBlock() instanceof ECOEnergyCellBlock cell
                && tier.supportsComponentTier(cell.getBlockEntity(level, pos).getTier())
                && state.getValue(ECOEnergyCellBlock.FACING) == back
        );
        if (upperEnergyCellResult.error().isPresent()) {
            logVerifyFailure(level, min, max, "upper energy cell line", upperEnergyCellStart, "ECOEnergyCellBlock facing " + back + " tier supported by " + tier, back);
            logLineProbe(level, min, max, "upper energy cell line", upperEnergyCellStart, right, 8);
            return false;
        }
        BlockPos upperEnergyCellEnd = upperEnergyCellResult.getOrThrow(false, ignored -> {});
        if (upperEnergyCellEnd.equals(upperEnergyCellStart)) {
            boolean validSingleUpperCell = validateBlock(
                level,
                upperEnergyCellStart,
                state -> state.getBlock() instanceof ECOEnergyCellBlock cell
                    && tier.supportsComponentTier(cell.getBlockEntity(level, upperEnergyCellEnd).getTier())
                    && state.getValue(ECOEnergyCellBlock.FACING) == back
            );
            if (!validSingleUpperCell) {
                logVerifyFailure(level, min, max, "upper energy cell single", upperEnergyCellStart, "ECOEnergyCellBlock facing " + back + " tier supported by " + tier, back);
            }
            return validSingleUpperCell;
        }
        BlockPos lowerEnergyCellStart = controllerPos.relative(back).relative(down).relative(right);
        DataResult<BlockPos> lowerEnergyCellResult = validateBlockLine(
            level,
            right,
            lowerEnergyCellStart,
            (state, pos) -> state.getBlock() instanceof ECOEnergyCellBlock cell
                && tier.supportsComponentTier(cell.getBlockEntity(level, pos).getTier())
                && state.getValue(ECOEnergyCellBlock.FACING) == back
        );
        if (lowerEnergyCellResult.error().isPresent()) {
            logVerifyFailure(level, min, max, "lower energy cell line", lowerEnergyCellStart, "ECOEnergyCellBlock facing " + back + " tier supported by " + tier, back);
            logLineProbe(level, min, max, "lower energy cell line", lowerEnergyCellStart, right, 8);
            return false;
        }
        BlockPos lowerEnergyCellEnd = lowerEnergyCellResult.getOrThrow(false, ignored -> {});

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
            for (BlockPos tailCasingPos : tailCasingPoses) {
                logVerifyFailure(level, min, max, "same surface tail casings", tailCasingPos, "same x, y, or z surface: " + tailCasingPoses, null);
            }
            return false;
        }
        for (BlockPos tailCasingPos : tailCasingPoses) {
            if (!validateBlock(level, tailCasingPos, BlockState::is, NEBlocks.STORAGE_CASING.get())) {
                logVerifyFailure(level, min, max, "tail casing", tailCasingPos, "STORAGE_CASING", null);
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isValidBlockEntity(BlockEntity te) {
        return (te instanceof NEBlockEntity<?,?> neBlockEntity && neBlockEntity.getCalculator() instanceof NEStorageClusterCalculator);
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

    private void logCasingColumn(ServerLevel level, BlockPos min, BlockPos max, String step, BlockPos centerPos, Direction top, Direction down) {
        logVerifyFailure(level, min, max, step + " center", centerPos, "STORAGE_CASING column at center/top/down", null);
        logVerifyFailure(level, min, max, step + " top", centerPos.relative(top), "STORAGE_CASING column at center/top/down", null);
        logVerifyFailure(level, min, max, step + " down", centerPos.relative(down), "STORAGE_CASING column at center/top/down", null);
    }

    private void logInterfaceStack(
        ServerLevel level,
        BlockPos min,
        BlockPos max,
        String step,
        BlockPos interfacePos,
        Direction top,
        Direction down,
        String interfaceExpected,
        String casingExpected
    ) {
        logVerifyFailure(level, min, max, step + " interface", interfacePos, interfaceExpected, null);
        logVerifyFailure(level, min, max, step + " top", interfacePos.relative(top), casingExpected, null);
        logVerifyFailure(level, min, max, step + " down", interfacePos.relative(down), casingExpected, null);
    }

    private void logBlockVolumeFailure(
        ServerLevel level,
        BlockPos min,
        BlockPos max,
        String step,
        BlockPos from,
        BlockPos to,
        String expected,
        Direction expectedFacing
    ) {
        for (BlockPos blockPos : BlockPos.betweenClosed(from, to)) {
            BlockState state = level.getBlockState(blockPos);
            if (!state.is(NEBlocks.ECO_DRIVE.get()) || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) || state.getValue(BlockStateProperties.HORIZONTAL_FACING) != expectedFacing) {
                logVerifyFailure(level, min, max, step, blockPos, expected, expectedFacing);
                return;
            }
        }
        logVerifyFailure(level, min, max, step, from, expected, expectedFacing);
    }
}
