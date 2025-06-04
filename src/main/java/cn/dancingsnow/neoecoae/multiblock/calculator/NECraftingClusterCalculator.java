package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingParallelCore;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOFluidInputHatchBlock;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOFluidOutputHatchBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
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

public class NECraftingClusterCalculator extends NEClusterCalculator<NECraftingCluster> {
    public NECraftingClusterCalculator(NEBlockEntity<NECraftingCluster, ?> t) {
        super(t);
    }

    @Override
    protected int maxLength() {
        return NEConfig.craftingSystemMaxLength;
    }

    @Override
    public NECraftingCluster createCluster(ServerLevel level, BlockPos min, BlockPos max) {
        return new NECraftingCluster(min, max);
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        ECOCraftingSystemBlockEntity controller = null;
        BlockPos controllerPos = null;
        for (BlockPos pos : MultiBlockUtil.allPossibleController(min, max)) {
            if (level.getBlockEntity(pos) instanceof ECOCraftingSystemBlockEntity be) {
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
        if (!validateHatchAndInterface(level, interfacePos, top, down, left)) {
            return false;
        }
        BlockPos workerStart = controllerPos.relative(right).relative(right);
        DataResult<BlockPos> workerEndResult = validateBlockLine(
            level,
            right,
            workerStart,
            matchingStateFacing(NEBlocks.CRAFTING_WORKER, front)
        );
        if (workerEndResult.isError()) {
            return false;
        }
        BlockPos workerEnd = workerEndResult.getOrThrow();

        BlockPos upperParallelCoreStart = workerStart.relative(top);
        DataResult<BlockPos> upperParallelCoreEndResult = validateBlockLine(
            level,
            right,
            upperParallelCoreStart,
            matchingParallelCore(level, tier, front)
        );
        if (upperParallelCoreEndResult.isError()) {
            return false;
        }
        BlockPos upperParallelCoreEnd = upperParallelCoreEndResult.getOrThrow();

        BlockPos lowerParallelCoreStart = workerStart.relative(down);
        DataResult<BlockPos> lowerParallelCoreEndResult = validateBlockLine(
            level,
            right,
            lowerParallelCoreStart,
            matchingParallelCore(level, tier, front)
        );
        if (lowerParallelCoreEndResult.isError()) {
            return false;
        }
        BlockPos lowerParallelCoreEnd = lowerParallelCoreEndResult.getOrThrow();

        BlockPos ventStart = workerStart.relative(back);
        DataResult<BlockPos> ventEndResult = validateBlockLine(
            level,
            right,
            ventStart,
            matchingStateFacing(NEBlocks.CRAFTING_VENT, back)
        );
        if (ventEndResult.isError()) {
            return false;
        }
        BlockPos ventEnd = ventEndResult.getOrThrow();

        BlockPos upperPatternBusStart = ventStart.relative(top);
        DataResult<BlockPos> upperPatternBusEndResult = validateBlockLine(
            level,
            right,
            upperPatternBusStart,
            matchingStateFacing(NEBlocks.CRAFTING_PATTERN_BUS, back)
        );
        if (upperPatternBusEndResult.isError()) {
            return false;
        }
        BlockPos upperPatternBusEnd = upperPatternBusEndResult.getOrThrow();

        BlockPos lowerPatternBusStart = ventStart.relative(down);
        DataResult<BlockPos> lowerPatternBusEndResult = validateBlockLine(
            level,
            right,
            lowerPatternBusStart,
            matchingStateFacing(NEBlocks.CRAFTING_PATTERN_BUS, back)
        );
        if (lowerPatternBusEndResult.isError()) {
            return false;
        }
        BlockPos lowerPatternBusEnd = lowerPatternBusEndResult.getOrThrow();

        List<BlockPos> endCasing = List.of(
            workerEnd,
            upperParallelCoreEnd,
            lowerParallelCoreEnd,
            upperPatternBusEnd,
            lowerPatternBusEnd,
            ventEnd
        ).stream().map(it -> it.relative(right)).toList();

        if (!ensureSameSurface(endCasing)) {
            return false;
        }

        return validateBlocks(level, endCasing, BlockState::is, NEBlocks.CRAFTING_CASING);
    }

    private static boolean validateHatchAndInterface(ServerLevel level, BlockPos interfacePos, Direction top, Direction down, Direction left) {
        if (!validateBlock(level, interfacePos, BlockState::is, NEBlocks.CRAFTING_INTERFACE)) {
            return false;
        }
        if (!validateBlock(level, interfacePos.relative(top), BlockState::is, NEBlocks.INPUT_HATCH)
            || level.getBlockState(interfacePos.relative(top)).getValue(ECOFluidInputHatchBlock.FACING) != left
        ) {
            return false;
        }
        return validateBlock(level, interfacePos.relative(down), BlockState::is, NEBlocks.OUTPUT_HATCH) &&
            level.getBlockState(interfacePos.relative(top)).getValue(ECOFluidOutputHatchBlock.FACING) == left;
    }

    private boolean validateCasing(ServerLevel level, BlockPos controllerPos, Direction top, Direction down, Direction direction) {
        return validateCasing(level, controllerPos.relative(direction), top, down);
    }

    private boolean validateCasing(ServerLevel level, BlockPos centerPos, Direction top, Direction down) {
        return validateCasing(level, centerPos, top, down, NEBlocks.CRAFTING_CASING);
    }

    private BiPredicate<BlockState, BlockPos> matchingParallelCore(
        Level level,
        IECOTier tier,
        Direction facing
    ) {
        return (s, p) -> s.getBlock() instanceof ECOCraftingParallelCore core
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
