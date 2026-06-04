package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationCoolingController;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationParallelCore;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationThreadingCore;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.util.MultiBlockUtil;
import com.mojang.serialization.DataResult;
import com.tterrag.registrate.util.entry.BlockEntry;
import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class NEComputationClusterCalculator extends NEClusterCalculator<NEComputationCluster> {
    public NEComputationClusterCalculator(NEBlockEntity<NEComputationCluster, ?> t) {
        super(t);
    }

    @Override
    protected int maxLength() {
        return NEConfig.computationSystemMaxLength;
    }

    @Override
    public NEComputationCluster createCluster(ServerLevel level, BlockPos min, BlockPos max) {
        return new NEComputationCluster(min, max);
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        if (verifyInternalStructure(level, min, max, false)) {
            setMirroredStructure(false);
            return true;
        }
        boolean mirrored = verifyInternalStructure(level, min, max, true);
        setMirroredStructure(mirrored);
        return mirrored;
    }

    private boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max, boolean mirrored) {
        ECOComputationSystemBlockEntity controller = null;
        BlockPos controllerPos = null;
        for (BlockPos pos : MultiBlockUtil.allPossibleController(min, max)) {
            if (level.getBlockEntity(pos) instanceof ECOComputationSystemBlockEntity be) {
                controller = be;
                controllerPos = pos;
                break;
            }
        }
        if (controller == null) {
            logVerifyFailure(
                    level,
                    min,
                    max,
                    "controller",
                    min,
                    "ECOComputationSystemBlockEntity in allPossibleController(min,max)",
                    null);
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
        if (!validateCasing(level, controllerPos, top, down, right)) {
            logCasingColumn(level, min, max, "controller right casing", controllerPos.relative(right), top, down);
            return false;
        }
        if (!validateCasing(level, controllerPos, top, down, back)) {
            logCasingColumn(level, min, max, "controller back casing", controllerPos.relative(back), top, down);
            return false;
        }
        if (!validateCasing(level, controllerPos.relative(back).relative(right), top, down)) {
            logCasingColumn(
                    level,
                    min,
                    max,
                    "controller back right casing",
                    controllerPos.relative(back).relative(right),
                    top,
                    down);
            return false;
        }
        BlockPos interfacePos = controllerPos.relative(back).relative(left);
        if (!validateInterface(
                level, interfacePos, top, down, NEBlocks.COMPUTATION_INTERFACE, NEBlocks.COMPUTATION_CASING)) {
            logInterfaceStack(
                    level,
                    min,
                    max,
                    "interface stack",
                    interfacePos,
                    top,
                    down,
                    "COMPUTATION_INTERFACE",
                    "COMPUTATION_CASING");
            return false;
        }
        BlockPos connectorStart = controllerPos.relative(right).relative(right);
        DataResult<BlockPos> connectorEndResult = validateBlockLine(
                level, right, connectorStart, matchingStateFacing(NEBlocks.COMPUTATION_TRANSMITTER, front));
        if (connectorEndResult.error().isPresent()) {
            logVerifyFailure(
                    level,
                    min,
                    max,
                    "transmitter line",
                    connectorStart,
                    "COMPUTATION_TRANSMITTER facing " + front,
                    front);
            logLineProbe(level, min, max, "transmitter line", connectorStart, right, 8);
            return false;
        }
        BlockPos connectorEnd = connectorEndResult.getOrThrow(false, ignored -> {});

        BlockPos threadingCoreStart = connectorStart.relative(back);
        DataResult<BlockPos> threadingCoreEndResult =
                validateBlockLine(level, right, threadingCoreStart, matchingThreadingCore(level, tier, back));
        if (threadingCoreEndResult.error().isPresent()) {
            logVerifyFailure(
                    level,
                    min,
                    max,
                    "threading core line",
                    threadingCoreStart,
                    "ECOComputationThreadingCore facing " + back + " tier supported by " + tier,
                    back);
            logLineProbe(level, min, max, "threading core line", threadingCoreStart, right, 8);
            return false;
        }
        BlockPos threadingCoreEnd = threadingCoreEndResult.getOrThrow(false, ignored -> {});

        BlockPos upperParallelCoreStart = threadingCoreStart.relative(top);
        DataResult<BlockPos> upperParallelCoreEndResult =
                validateBlockLine(level, right, upperParallelCoreStart, matchingParallelCore(level, tier, back));
        if (upperParallelCoreEndResult.error().isPresent()) {
            logVerifyFailure(
                    level,
                    min,
                    max,
                    "upper parallel core line",
                    upperParallelCoreStart,
                    "ECOComputationParallelCore facing " + back + " tier supported by " + tier,
                    back);
            logLineProbe(level, min, max, "upper parallel core line", upperParallelCoreStart, right, 8);
            return false;
        }
        BlockPos upperParallelCoreEnd = upperParallelCoreEndResult.getOrThrow(false, ignored -> {});

        BlockPos lowerParallelCoreStart = threadingCoreStart.relative(down);
        DataResult<BlockPos> lowerParallelCoreEndResult =
                validateBlockLine(level, right, lowerParallelCoreStart, matchingParallelCore(level, tier, back));
        if (lowerParallelCoreEndResult.error().isPresent()) {
            logVerifyFailure(
                    level,
                    min,
                    max,
                    "lower parallel core line",
                    lowerParallelCoreStart,
                    "ECOComputationParallelCore facing " + back + " tier supported by " + tier,
                    back);
            logLineProbe(level, min, max, "lower parallel core line", lowerParallelCoreStart, right, 8);
            return false;
        }
        BlockPos lowerParallelCoreEnd = lowerParallelCoreEndResult.getOrThrow(false, ignored -> {});

        BlockPos upperDriveStart = connectorStart.relative(top);
        DataResult<BlockPos> upperDriveEndResult = validateBlockLine(
                level, right, upperDriveStart, matchingStateFacing(NEBlocks.COMPUTATION_DRIVE, front));
        if (upperDriveEndResult.error().isPresent()) {
            logVerifyFailure(
                    level,
                    min,
                    max,
                    "upper computation drive line",
                    upperDriveStart,
                    "COMPUTATION_DRIVE facing " + front,
                    front);
            logLineProbe(level, min, max, "upper computation drive line", upperDriveStart, right, 8);
            return false;
        }
        BlockPos upperDriveEnd = upperDriveEndResult.getOrThrow(false, ignored -> {});

        BlockPos lowerDriveStart = connectorStart.relative(down);
        DataResult<BlockPos> lowerDriveEndResult = validateBlockLine(
                level, right, lowerDriveStart, matchingStateFacing(NEBlocks.COMPUTATION_DRIVE, front));
        if (lowerDriveEndResult.error().isPresent()) {
            logVerifyFailure(
                    level,
                    min,
                    max,
                    "lower computation drive line",
                    lowerDriveStart,
                    "COMPUTATION_DRIVE facing " + front,
                    front);
            logLineProbe(level, min, max, "lower computation drive line", lowerDriveStart, right, 8);
            return false;
        }
        BlockPos lowerDriveEnd = lowerDriveEndResult.getOrThrow(false, ignored -> {});

        List<BlockPos> tails = List.of(
                connectorEnd,
                threadingCoreEnd,
                upperDriveEnd,
                lowerDriveEnd,
                upperParallelCoreEnd,
                lowerParallelCoreEnd);

        if (!ensureSameSurface(tails)) {
            for (BlockPos tail : tails) {
                logVerifyFailure(
                        level, min, max, "same surface tails", tail, "same x, y, or z surface: " + tails, null);
            }
            return false;
        }
        List<BlockPos> tailCasings = List.of(
                threadingCoreEnd.relative(right),
                upperDriveEnd.relative(right),
                lowerDriveEnd.relative(right),
                upperParallelCoreEnd.relative(right),
                lowerParallelCoreEnd.relative(right));
        BlockPos coolerPos = connectorEnd.relative(right);
        if (!validateBlock(level, coolerPos, matchingCoolingController(level, tier, right), coolerPos)) {
            logVerifyFailure(
                    level,
                    min,
                    max,
                    "cooling controller",
                    coolerPos,
                    "ECOComputationCoolingController facing " + right + " tier supported by " + tier,
                    right);
            return false;
        }

        for (BlockPos tailCasing : tailCasings) {
            if (!validateBlock(level, tailCasing, BlockState::is, NEBlocks.COMPUTATION_CASING.get())) {
                logVerifyFailure(level, min, max, "tail casing", tailCasing, "COMPUTATION_CASING", null);
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isValidBlockEntity(BlockEntity te) {
        return (te instanceof NEBlockEntity<?, ?> neBlockEntity
                && neBlockEntity.getCalculator() instanceof NEComputationClusterCalculator);
    }

    private boolean validateCasing(
            ServerLevel level, BlockPos controllerPos, Direction top, Direction down, Direction direction) {
        return validateCasing(level, controllerPos.relative(direction), top, down);
    }

    private boolean validateCasing(ServerLevel level, BlockPos centerPos, Direction top, Direction down) {
        return validateCasing(level, centerPos, top, down, NEBlocks.COMPUTATION_CASING);
    }

    private void logCasingColumn(
            ServerLevel level,
            BlockPos min,
            BlockPos max,
            String step,
            BlockPos centerPos,
            Direction top,
            Direction down) {
        logVerifyFailure(
                level, min, max, step + " center", centerPos, "COMPUTATION_CASING column at center/top/down", null);
        logVerifyFailure(
                level,
                min,
                max,
                step + " top",
                centerPos.relative(top),
                "COMPUTATION_CASING column at center/top/down",
                null);
        logVerifyFailure(
                level,
                min,
                max,
                step + " down",
                centerPos.relative(down),
                "COMPUTATION_CASING column at center/top/down",
                null);
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
            String casingExpected) {
        logVerifyFailure(level, min, max, step + " interface", interfacePos, interfaceExpected, null);
        logVerifyFailure(level, min, max, step + " top", interfacePos.relative(top), casingExpected, null);
        logVerifyFailure(level, min, max, step + " down", interfacePos.relative(down), casingExpected, null);
    }

    private BiPredicate<BlockState, BlockPos> matchingParallelCore(Level level, IECOTier tier, Direction facing) {
        return (s, p) -> s.getBlock() instanceof ECOComputationParallelCore core
                && tier.supportsComponentTier(core.getBlockEntity(level, p).getTier())
                && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    private BiPredicate<BlockState, BlockPos> matchingThreadingCore(Level level, IECOTier tier, Direction facing) {
        return (s, p) -> s.getBlock() instanceof ECOComputationThreadingCore core
                && tier.supportsComponentTier(core.getBlockEntity(level, p).getTier())
                && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    private BiPredicate<BlockState, BlockPos> matchingCoolingController(Level level, IECOTier tier, Direction facing) {
        return (s, p) -> s.getBlock() instanceof ECOComputationCoolingController core
                && tier.supportsComponentTier(core.getBlockEntity(level, p).getTier())
                && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    private BiPredicate<BlockState, BlockPos> matchingStateFacing(BlockEntry<? extends Block> block, Direction facing) {
        return (s, p) -> s.is(block.get()) && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }
}
