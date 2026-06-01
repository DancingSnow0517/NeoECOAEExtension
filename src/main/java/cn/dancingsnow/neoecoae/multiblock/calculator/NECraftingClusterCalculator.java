package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingParallelCore;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.util.MultiBlockUtil;
import com.mojang.serialization.DataResult;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

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
        return verifyInternalStructure(level, min, max, false) || verifyInternalStructure(level, min, max, true);
    }

    private boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max, boolean mirrored) {
        ECOCraftingSystemBlockEntity controller = null;
        BlockPos controllerPos = null;
        for (BlockPos pos : MultiBlockUtil.allPossibleController(min, max)) {
            if (level.getBlockEntity(pos) instanceof ECOCraftingSystemBlockEntity be) {
                controller = be;
                controllerPos = pos;
                break;
            }
        }
        if (controller == null) {
            logVerifyFailure(level, min, max, "controller", min, "ECOCraftingSystemBlockEntity in allPossibleController(min,max)", null);
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
            logCasingColumn(level, min, max, "controller back right casing", controllerPos.relative(back).relative(right), top, down);
            return false;
        }
        BlockPos interfacePos = controllerPos.relative(back).relative(left);
        if (!validateHatchAndInterface(level, min, max, interfacePos, top, down)) {
            return false;
        }
        BlockPos workerStart = controllerPos.relative(right).relative(right);
        DataResult<BlockPos> workerEndResult = validateBlockLine(
            level,
            right,
            workerStart,
            matchingStateFacing(NEBlocks.CRAFTING_WORKER, front)
        );
        if (workerEndResult.error().isPresent()) {
            logVerifyFailure(level, min, max, "worker line", workerStart, "CRAFTING_WORKER facing " + front, front);
            logLineProbe(level, min, max, "worker line", workerStart, right, 8);
            return false;
        }
        BlockPos workerEnd = workerEndResult.getOrThrow(false, ignored -> {});

        BlockPos upperParallelCoreStart = workerStart.relative(top);
        DataResult<BlockPos> upperParallelCoreEndResult = validateBlockLine(
            level,
            right,
            upperParallelCoreStart,
            matchingParallelCore(level, tier, front)
        );
        if (upperParallelCoreEndResult.error().isPresent()) {
            logVerifyFailure(level, min, max, "upper parallel core line", upperParallelCoreStart, "ECOCraftingParallelCore facing " + front + " tier supported by " + tier, front);
            logLineProbe(level, min, max, "upper parallel core line", upperParallelCoreStart, right, 8);
            return false;
        }
        BlockPos upperParallelCoreEnd = upperParallelCoreEndResult.getOrThrow(false, ignored -> {});

        BlockPos lowerParallelCoreStart = workerStart.relative(down);
        DataResult<BlockPos> lowerParallelCoreEndResult = validateBlockLine(
            level,
            right,
            lowerParallelCoreStart,
            matchingParallelCore(level, tier, front)
        );
        if (lowerParallelCoreEndResult.error().isPresent()) {
            logVerifyFailure(level, min, max, "lower parallel core line", lowerParallelCoreStart, "ECOCraftingParallelCore facing " + front + " tier supported by " + tier, front);
            logLineProbe(level, min, max, "lower parallel core line", lowerParallelCoreStart, right, 8);
            return false;
        }
        BlockPos lowerParallelCoreEnd = lowerParallelCoreEndResult.getOrThrow(false, ignored -> {});

        BlockPos ventStart = workerStart.relative(back);
        DataResult<BlockPos> ventEndResult = validateBlockLine(
            level,
            right,
            ventStart,
            matchingStateFacing(NEBlocks.CRAFTING_VENT, back)
        );
        if (ventEndResult.error().isPresent()) {
            logVerifyFailure(level, min, max, "vent line", ventStart, "CRAFTING_VENT facing " + back, back);
            logLineProbe(level, min, max, "vent line", ventStart, right, 8);
            return false;
        }
        BlockPos ventEnd = ventEndResult.getOrThrow(false, ignored -> {});

        BlockPos upperPatternBusStart = ventStart.relative(top);
        DataResult<BlockPos> upperPatternBusEndResult = validateBlockLine(
            level,
            right,
            upperPatternBusStart,
            matchingStateFacing(NEBlocks.CRAFTING_PATTERN_BUS, back)
        );
        if (upperPatternBusEndResult.error().isPresent()) {
            logVerifyFailure(level, min, max, "upper pattern bus line", upperPatternBusStart, "CRAFTING_PATTERN_BUS facing " + back, back);
            logLineProbe(level, min, max, "upper pattern bus line", upperPatternBusStart, right, 8);
            return false;
        }
        BlockPos upperPatternBusEnd = upperPatternBusEndResult.getOrThrow(false, ignored -> {});

        BlockPos lowerPatternBusStart = ventStart.relative(down);
        DataResult<BlockPos> lowerPatternBusEndResult = validateBlockLine(
            level,
            right,
            lowerPatternBusStart,
            matchingStateFacing(NEBlocks.CRAFTING_PATTERN_BUS, back)
        );
        if (lowerPatternBusEndResult.error().isPresent()) {
            logVerifyFailure(level, min, max, "lower pattern bus line", lowerPatternBusStart, "CRAFTING_PATTERN_BUS facing " + back, back);
            logLineProbe(level, min, max, "lower pattern bus line", lowerPatternBusStart, right, 8);
            return false;
        }
        BlockPos lowerPatternBusEnd = lowerPatternBusEndResult.getOrThrow(false, ignored -> {});

        Direction endCasingDirection = right;
        List<BlockPos> endCasing = Stream.of(
            workerEnd,
            upperParallelCoreEnd,
            lowerParallelCoreEnd,
            upperPatternBusEnd,
            lowerPatternBusEnd,
            ventEnd
        ).map(it -> it.relative(endCasingDirection)).toList();

        if (!ensureSameSurface(endCasing)) {
            for (BlockPos endCasingPos : endCasing) {
                logVerifyFailure(level, min, max, "same surface end casings", endCasingPos, "same x, y, or z surface: " + endCasing, null);
            }
            return false;
        }

        for (BlockPos endCasingPos : endCasing) {
            if (!validateBlock(level, endCasingPos, BlockState::is, NEBlocks.CRAFTING_CASING.get())) {
                logVerifyFailure(level, min, max, "end casing", endCasingPos, "CRAFTING_CASING", null);
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isValidBlockEntity(BlockEntity te) {
        return (te instanceof NEBlockEntity<?,?> neBlockEntity && neBlockEntity.getCalculator() instanceof NECraftingClusterCalculator);
    }

    private boolean validateHatchAndInterface(ServerLevel level, BlockPos min, BlockPos max, BlockPos interfacePos, Direction top, Direction down) {
        if (!validateBlock(level, interfacePos, BlockState::is, NEBlocks.CRAFTING_INTERFACE.get())) {
            logVerifyFailure(level, min, max, "hatch/interface stack interface", interfacePos, "CRAFTING_INTERFACE", null);
            return false;
        }
        if (!validateBlock(level, interfacePos.relative(top), BlockState::is, NEBlocks.INPUT_HATCH.get())) {
            logVerifyFailure(level, min, max, "hatch/interface stack input hatch", interfacePos.relative(top), "INPUT_HATCH", null);
            return false;
        }
        if (!validateBlock(level, interfacePos.relative(down), BlockState::is, NEBlocks.OUTPUT_HATCH.get())) {
            logVerifyFailure(level, min, max, "hatch/interface stack output hatch", interfacePos.relative(down), "OUTPUT_HATCH", null);
            return false;
        }
        return true;
    }

    private boolean validateCasing(ServerLevel level, BlockPos controllerPos, Direction top, Direction down, Direction direction) {
        return validateCasing(level, controllerPos.relative(direction), top, down);
    }

    private boolean validateCasing(ServerLevel level, BlockPos centerPos, Direction top, Direction down) {
        return validateCasing(level, centerPos, top, down, NEBlocks.CRAFTING_CASING);
    }

    private void logCasingColumn(ServerLevel level, BlockPos min, BlockPos max, String step, BlockPos centerPos, Direction top, Direction down) {
        logVerifyFailure(level, min, max, step + " center", centerPos, "CRAFTING_CASING column at center/top/down", null);
        logVerifyFailure(level, min, max, step + " top", centerPos.relative(top), "CRAFTING_CASING column at center/top/down", null);
        logVerifyFailure(level, min, max, step + " down", centerPos.relative(down), "CRAFTING_CASING column at center/top/down", null);
    }

    private BiPredicate<BlockState, BlockPos> matchingParallelCore(
        Level level,
        IECOTier tier,
        Direction facing
    ) {
        return (s, p) -> s.getBlock() instanceof ECOCraftingParallelCore core
            && tier.supportsComponentTier(core.getBlockEntity(level, p).getTier())
            && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

    private BiPredicate<BlockState, BlockPos> matchingStateFacing(
        BlockEntry<? extends Block> block,
        Direction facing
    ) {
        return (s, p) -> s.is(block.get())
            && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }
}
