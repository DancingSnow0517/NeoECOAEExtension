package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingParallelCore;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingNetworkSwitchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.network.NENetworkSwitchUtil;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class NECraftingClusterCalculator extends NEClusterCalculator<NECraftingCluster> {
    private boolean networkMode;
    private boolean highEnergyNetworkMode;

    public NECraftingClusterCalculator(NEBlockEntity<NECraftingCluster, ?> t) {
        super(t);
    }

    @Override
    protected int maxLength() {
        return NEConfig.craftingSystemMaxLength;
    }

    @Override
    public NECraftingCluster createCluster(ServerLevel level, BlockPos min, BlockPos max) {
        NECraftingCluster cluster = new NECraftingCluster(min, max);
        cluster.setNetworkMode(networkMode);
        cluster.setHighEnergyNetworkMode(highEnergyNetworkMode);
        return cluster;
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        networkMode = false;
        highEnergyNetworkMode = false;
        return verifyMirroredStructure(level, min, max, this::verifyInternalStructure);
    }

    private boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max, boolean mirrored) {
        var controllerCandidate = findSoleController(level, min, max, ECOCraftingSystemBlockEntity.class);
        if (controllerCandidate.isEmpty()) {
            return false;
        }
        ECOCraftingSystemBlockEntity controller = controllerCandidate.get().blockEntity();
        BlockPos controllerPos = controllerCandidate.get().pos();
        IECOTier tier = controller.getTier();
        ControllerOrientation orientation = controllerOrientation(controller.getBlockState(), mirrored);
        Direction back = orientation.back();
        Direction front = orientation.front();
        Direction top = orientation.top();
        Direction down = orientation.down();
        Direction left = orientation.left();
        Direction right = orientation.right();
        if (!validateCasing(level, controllerPos, top, down, left)) {
            return false;
        }
        if (!validateCasingOrNetworkSwitch(level, controllerPos, tier, top, down, right)) {
            return false;
        }
        if (!validateCasing(level, controllerPos, top, down, back)) {
            return false;
        }
        if (!validateCasing(level, controllerPos.relative(back).relative(right), top, down)) {
            return false;
        }
        BlockPos interfacePos = controllerPos.relative(back).relative(left);
        if (!validateHatchAndInterface(level, min, max, interfacePos, top, down)) {
            return false;
        }
        BlockPos workerStart = controllerPos.relative(right).relative(right);
        DataResult<BlockPos> workerEndResult =
                validateBlockLine(level, right, workerStart, matchingStateFacing(NEBlocks.CRAFTING_WORKER, front));
        if (workerEndResult.error().isPresent()) {
            return false;
        }
        BlockPos workerEnd = workerEndResult.getOrThrow(false, ignored -> {});

        BlockPos upperParallelCoreStart = workerStart.relative(top);
        DataResult<BlockPos> upperParallelCoreEndResult =
                validateBlockLine(level, right, upperParallelCoreStart, matchingParallelCore(level, tier, front));
        if (upperParallelCoreEndResult.error().isPresent()) {
            return false;
        }
        BlockPos upperParallelCoreEnd = upperParallelCoreEndResult.getOrThrow(false, ignored -> {});

        BlockPos lowerParallelCoreStart = workerStart.relative(down);
        DataResult<BlockPos> lowerParallelCoreEndResult =
                validateBlockLine(level, right, lowerParallelCoreStart, matchingParallelCore(level, tier, front));
        if (lowerParallelCoreEndResult.error().isPresent()) {
            return false;
        }
        BlockPos lowerParallelCoreEnd = lowerParallelCoreEndResult.getOrThrow(false, ignored -> {});

        BlockPos ventStart = workerStart.relative(back);
        DataResult<BlockPos> ventEndResult =
                validateBlockLine(level, right, ventStart, matchingStateFacing(NEBlocks.CRAFTING_VENT, back));
        if (ventEndResult.error().isPresent()) {
            return false;
        }
        BlockPos ventEnd = ventEndResult.getOrThrow(false, ignored -> {});

        BlockPos upperPatternBusStart = ventStart.relative(top);
        DataResult<BlockPos> upperPatternBusEndResult = validateBlockLine(
                level, right, upperPatternBusStart, matchingStateFacing(NEBlocks.CRAFTING_PATTERN_BUS, back));
        if (upperPatternBusEndResult.error().isPresent()) {
            return false;
        }
        BlockPos upperPatternBusEnd = upperPatternBusEndResult.getOrThrow(false, ignored -> {});

        BlockPos lowerPatternBusStart = ventStart.relative(down);
        DataResult<BlockPos> lowerPatternBusEndResult = validateBlockLine(
                level, right, lowerPatternBusStart, matchingStateFacing(NEBlocks.CRAFTING_PATTERN_BUS, back));
        if (lowerPatternBusEndResult.error().isPresent()) {
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
                        ventEnd)
                .map(it -> it.relative(endCasingDirection))
                .toList();

        if (!ensureSameSurface(endCasing)) {
            return false;
        }

        for (BlockPos endCasingPos : endCasing) {
            if (!validateBlock(level, endCasingPos, BlockState::is, NEBlocks.CRAFTING_CASING.get())) {
                return false;
            }
        }
        applyNetworkMode(level.getBlockState(controllerPos.relative(right)));
        return true;
    }

    @Override
    public boolean isValidBlockEntity(BlockEntity te) {
        if (te instanceof ECOCraftingNetworkSwitchBlockEntity networkSwitch) {
            return networkSwitch.getLevel() instanceof ServerLevel level
                    && isNetworkSwitchAt(level, networkSwitch.getBlockPos());
        }
        return (te instanceof NEBlockEntity<?, ?> neBlockEntity
                && neBlockEntity.getCalculator() instanceof NECraftingClusterCalculator);
    }

    private static boolean isNetworkSwitchAt(ServerLevel level, BlockPos switchPos) {
        BlockState state = level.getBlockState(switchPos);
        if (!state.is(NEBlocks.CRAFTING_NETWORK_SWITCH.get())
                && !state.is(NEBlocks.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH.get())) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(switchPos.relative(direction)) instanceof ECOCraftingSystemBlockEntity controller
                    && NENetworkSwitchUtil.canUseNetworkSwitch(controller.getTier())
                    && NENetworkSwitchUtil.isSwitchPosition(
                            switchPos, controller.getBlockPos(), controller.getBlockState())) {
                return true;
            }
        }
        return false;
    }

    private void applyNetworkMode(BlockState switchState) {
        highEnergyNetworkMode = switchState.is(NEBlocks.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH.get());
        networkMode = highEnergyNetworkMode || switchState.is(NEBlocks.CRAFTING_NETWORK_SWITCH.get());
    }

    private boolean validateCasingOrNetworkSwitch(
            ServerLevel level,
            BlockPos controllerPos,
            IECOTier tier,
            Direction top,
            Direction down,
            Direction direction) {
        BlockPos center = controllerPos.relative(direction);
        BlockState state = level.getBlockState(center);
        boolean switchBlock = state.is(NEBlocks.CRAFTING_NETWORK_SWITCH.get())
                || state.is(NEBlocks.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH.get());
        if (!state.is(NEBlocks.CRAFTING_CASING.get()) && !switchBlock) {
            return false;
        }
        if (switchBlock && !NENetworkSwitchUtil.canUseNetworkSwitch(tier)) {
            return false;
        }
        return validateBlock(level, center.relative(top), BlockState::is, NEBlocks.CRAFTING_CASING.get())
                && validateBlock(level, center.relative(down), BlockState::is, NEBlocks.CRAFTING_CASING.get());
    }

    private boolean validateHatchAndInterface(
            ServerLevel level, BlockPos min, BlockPos max, BlockPos interfacePos, Direction top, Direction down) {
        if (!validateBlock(level, interfacePos, BlockState::is, NEBlocks.CRAFTING_INTERFACE.get())) {
            return false;
        }
        if (!validateBlock(level, interfacePos.relative(top), BlockState::is, NEBlocks.INPUT_HATCH.get())) {
            return false;
        }
        if (!validateBlock(level, interfacePos.relative(down), BlockState::is, NEBlocks.OUTPUT_HATCH.get())) {
            return false;
        }
        return true;
    }

    private boolean validateCasing(
            ServerLevel level, BlockPos controllerPos, Direction top, Direction down, Direction direction) {
        return validateCasing(level, controllerPos.relative(direction), top, down);
    }

    private boolean validateCasing(ServerLevel level, BlockPos centerPos, Direction top, Direction down) {
        return validateCasing(level, centerPos, top, down, NEBlocks.CRAFTING_CASING);
    }

    private BiPredicate<BlockState, BlockPos> matchingParallelCore(Level level, IECOTier tier, Direction facing) {
        return (s, p) -> s.getBlock() instanceof ECOCraftingParallelCore core
                && tier.supportsComponentTier(core.getBlockEntity(level, p).getTier())
                && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }
}
