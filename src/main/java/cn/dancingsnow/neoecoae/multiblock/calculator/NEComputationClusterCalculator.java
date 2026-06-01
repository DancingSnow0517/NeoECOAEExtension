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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import java.util.function.BiPredicate;

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
        ECOComputationSystemBlockEntity controller = null;
        BlockPos controllerPos = null;
        for (BlockPos pos : MultiBlockUtil.allPossibleController(min, max)) {
            if (level.getBlockEntity(pos) instanceof ECOComputationSystemBlockEntity be) {
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
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, left, right, false)) {
            controller.setMirrored(false);
            return true;
        }
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, right, left, true)) {
            controller.setMirrored(true);
            return true;
        }
        controller.setMirrored(false);
        return false;
    }

    private boolean verifyStructure(
        ServerLevel level,
        BlockPos controllerPos,
        IECOTier tier,
        Direction front,
        Direction back,
        Direction top,
        Direction down,
        Direction interfaceSide,
        Direction expandSide,
        boolean mirrored
    ) {
        if (!validateCasing(level, controllerPos, top, down, interfaceSide)) return false;
        if (!validateCasing(level, controllerPos, top, down, expandSide)) return false;
        if (!validateCasing(level, controllerPos, top, down, back)) return false;
        if (!validateCasing(level, controllerPos.relative(back).relative(expandSide), top, down)) return false;
        BlockPos interfacePos = controllerPos.relative(back).relative(interfaceSide);
        if (!validateInterface(
            level,
            interfacePos,
            top,
            down,
            NEBlocks.COMPUTATION_INTERFACE,
            NEBlocks.COMPUTATION_CASING
        )) return false;
        BlockPos connectorStart = controllerPos.relative(expandSide).relative(expandSide);
        DataResult<BlockPos> connectorEndResult = validateBlockLine(
            level,
            expandSide,
            connectorStart,
            matchingStateFacing(
                NEBlocks.COMPUTATION_TRANSMITTER,
                front
            )
        );
        if (connectorEndResult.isError()) {
            return false;
        }
        BlockPos connectorEnd = connectorEndResult.getOrThrow();

        BlockPos threadingCoreStart = connectorStart.relative(back);
        DataResult<BlockPos> threadingCoreEndResult = validateBlockLine(
            level,
            expandSide,
            threadingCoreStart,
            matchingThreadingCore(level, tier, back)
        );
        if (threadingCoreEndResult.isError()) {
            return false;
        }
        BlockPos threadingCoreEnd = threadingCoreEndResult.getOrThrow();

        BlockPos upperParallelCoreStart = threadingCoreStart.relative(top);
        DataResult<BlockPos> upperParallelCoreEndResult = validateBlockLine(
            level,
            expandSide,
            upperParallelCoreStart,
            matchingParallelCore(level, tier, back)
        );
        if (upperParallelCoreEndResult.isError()) {
            return false;
        }
        BlockPos upperParallelCoreEnd = upperParallelCoreEndResult.getOrThrow();

        BlockPos lowerParallelCoreStart = threadingCoreStart.relative(down);
        DataResult<BlockPos> lowerParallelCoreEndResult = validateBlockLine(
            level,
            expandSide,
            lowerParallelCoreStart,
            matchingParallelCore(level, tier, back)
        );
        if (lowerParallelCoreEndResult.isError()) {
            return false;
        }
        BlockPos lowerParallelCoreEnd = lowerParallelCoreEndResult.getOrThrow();

        BlockPos upperDriveStart = connectorStart.relative(top);
        DataResult<BlockPos> upperDriveEndResult = validateBlockLine(
            level,
            expandSide,
            upperDriveStart,
            matchingStateFacing(NEBlocks.COMPUTATION_DRIVE, front)
        );
        if (upperDriveEndResult.isError()) {
            return false;
        }
        BlockPos upperDriveEnd = upperDriveEndResult.getOrThrow();

        BlockPos lowerDriveStart = connectorStart.relative(down);
        DataResult<BlockPos> lowerDriveEndResult = validateBlockLine(
            level,
            expandSide,
            lowerDriveStart,
            matchingStateFacing(NEBlocks.COMPUTATION_DRIVE, front)
        );
        if (lowerDriveEndResult.isError()) {
            return false;
        }
        BlockPos lowerDriveEnd = lowerDriveEndResult.getOrThrow();

        List<BlockPos> tails = List.of(
            connectorEnd,
            threadingCoreEnd,
            upperDriveEnd,
            lowerDriveEnd,
            upperParallelCoreEnd,
            lowerParallelCoreEnd
        );

        if (!ensureSameSurface(tails)) {
            return false;
        }
        List<BlockPos> tailCasings = List.of(
            threadingCoreEnd.relative(expandSide),
            upperDriveEnd.relative(expandSide),
            lowerDriveEnd.relative(expandSide),
            upperParallelCoreEnd.relative(expandSide),
            lowerParallelCoreEnd.relative(expandSide)
        );
        BlockPos coolerPos = connectorEnd.relative(expandSide);
        if (!validateBlock(
            level,
            coolerPos,
            matchingCoolingController(level, tier, expandSide),
            coolerPos
        )) {
            return false;
        }
        if (level.getBlockEntity(coolerPos) instanceof cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationCoolingControllerBlockEntity cooler) {
            cooler.setMirrored(mirrored);
        }

        return validateBlocks(level, tailCasings, BlockState::is, NEBlocks.COMPUTATION_CASING);
    }

    @Override
    public boolean isValidBlockEntity(BlockEntity te) {
        return (te instanceof NEBlockEntity<?,?> neBlockEntity && neBlockEntity.getCalculator() instanceof NEComputationClusterCalculator);
    }

    private boolean validateCasing(ServerLevel level, BlockPos controllerPos, Direction top, Direction down, Direction direction) {
        return validateCasing(level, controllerPos.relative(direction), top, down);
    }

    private boolean validateCasing(ServerLevel level, BlockPos centerPos, Direction top, Direction down) {
        return validateCasing(level, centerPos, top, down, NEBlocks.COMPUTATION_CASING);
    }

    private BiPredicate<BlockState, BlockPos> matchingParallelCore(
        Level level,
        IECOTier tier,
        Direction facing
    ) {
        return (s, p) -> s.getBlock() instanceof ECOComputationParallelCore core
            && tier.supportsComponentTier(core.getBlockEntity(level, p).getTier())
            && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    private BiPredicate<BlockState, BlockPos> matchingThreadingCore(
        Level level,
        IECOTier tier,
        Direction facing
    ) {
        return (s, p) -> s.getBlock() instanceof ECOComputationThreadingCore core
            && tier.supportsComponentTier(core.getBlockEntity(level, p).getTier())
            && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    private BiPredicate<BlockState, BlockPos> matchingCoolingController(
        Level level,
        IECOTier tier,
        Direction facing
    ) {
        return (s, p) -> s.getBlock() instanceof ECOComputationCoolingController core
            && tier.supportsComponentTier(core.getBlockEntity(level, p).getTier())
            && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    private BiPredicate<BlockState, BlockPos> matchingStateFacing(
        Holder<Block> block,
        Direction facing
    ) {
        return (s, p) -> s.is(block)
            && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }
}
