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
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.util.MultiBlockUtil;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
        return 16;
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
        if (!validateCasing(level, controllerPos, top, down, left)) return false;
        if (!validateCasing(level, controllerPos, top, down, right)) return false;
        if (!validateCasing(level, controllerPos, top, down, back)) return false;
        if (!validateCasing(level, controllerPos.relative(back).relative(right), top, down)) return false;
        BlockPos interfacePos = controllerPos.relative(back).relative(left);
        if (!validateInterface(
            level,
            interfacePos,
            top,
            down,
            NEBlocks.COMPUTATION_INTERFACE,
            NEBlocks.COMPUTATION_CASING
        )) return false;
        BlockPos connectorStart = controllerPos.relative(right).relative(right);
        DataResult<BlockPos> connectorEndResult = validateBlockLine(
            level,
            right,
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
            right,
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
            right,
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
            right,
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
            right,
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
            right,
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
            threadingCoreEnd.relative(right),
            upperDriveEnd.relative(right),
            lowerDriveEnd.relative(right),
            upperParallelCoreEnd.relative(right),
            lowerParallelCoreEnd.relative(right)
        );
        BlockPos coolerPos = connectorEnd.relative(right);
        if (!validateBlock(
            level,
            coolerPos,
            matchingCoolingController(level, tier, right),
            coolerPos
        )) {
            return false;
        }

        return validateBlocks(level, tailCasings, BlockState::is, NEBlocks.COMPUTATION_CASING);
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
            && core.getBlockEntity(level, p).getTier() == tier
            && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    private BiPredicate<BlockState, BlockPos> matchingThreadingCore(
        Level level,
        IECOTier tier,
        Direction facing
    ) {
        return (s, p) -> s.getBlock() instanceof ECOComputationThreadingCore core
            && core.getBlockEntity(level, p).getTier() == tier
            && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    private BiPredicate<BlockState, BlockPos> matchingCoolingController(
        Level level,
        IECOTier tier,
        Direction facing
    ) {
        return (s, p) -> s.getBlock() instanceof ECOComputationCoolingController core
            && core.getBlockEntity(level, p).getTier() == tier
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
