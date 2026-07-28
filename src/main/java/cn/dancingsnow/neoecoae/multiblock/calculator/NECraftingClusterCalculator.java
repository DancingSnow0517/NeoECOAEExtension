package cn.dancingsnow.neoecoae.multiblock.calculator;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingParallelCore;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingNetworkSwitchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.network.NENetworkSwitchUtil;
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
import java.util.stream.Stream;

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
        if (hasNearbyVerticalController(level, controllerPos, ECOCraftingSystemBlockEntity.class)) {
            networkMode = false;
            highEnergyNetworkMode = false;
            if (target.getCluster() != null) {
                target.getCluster().setNetworkMode(false);
                target.getCluster().setHighEnergyNetworkMode(false);
            }
            NENetworkSwitchUtil.clearFormed(level, controllerPos, controllerState);
            controller.setMirrored(false);
            return false;
        }
        IOrientationStrategy strategy = OrientationStrategies.horizontalFacing();
        Direction back = strategy.getSide(controllerState, RelativeSide.BACK);
        Direction front = back.getOpposite();
        Direction top = strategy.getSide(controllerState, RelativeSide.TOP);
        Direction down = top.getOpposite();
        Direction left = strategy.getSide(controllerState, RelativeSide.LEFT);
        Direction right = left.getOpposite();
        BlockPos normalSwitchPos = NENetworkSwitchUtil.switchPosition(controllerPos, controllerState, false);
        BlockPos mirroredSwitchPos = NENetworkSwitchUtil.switchPosition(controllerPos, controllerState, true);
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, right, left, right)) {
            controller.setMirrored(false);
            applyNetworkMode(level.getBlockState(normalSwitchPos));
            return true;
        }
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, left, right, left)) {
            controller.setMirrored(true);
            applyNetworkMode(level.getBlockState(mirroredSwitchPos));
            return true;
        }
        networkMode = false;
        highEnergyNetworkMode = false;
        if (target.getCluster() != null) {
            target.getCluster().setNetworkMode(false);
            target.getCluster().setHighEnergyNetworkMode(false);
        }
        NENetworkSwitchUtil.clearFormed(level, controllerPos, controllerState);
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
        Direction networkSwitchSide
    ) {
        if (!validateCasingOrNetworkSwitch(level, controllerPos, tier, top, down, networkSwitchSide)) return false;
        Direction ordinarySide = interfaceSide == networkSwitchSide ? expandSide : interfaceSide;
        if (!validateCasing(level, controllerPos, top, down, ordinarySide)) return false;
        if (!validateCasing(level, controllerPos, top, down, back)) return false;
        if (!validateCasing(level, controllerPos.relative(back).relative(expandSide), top, down)) return false;
        BlockPos interfacePos = controllerPos.relative(back).relative(interfaceSide);
        if (!validateHatchAndInterface(level, interfacePos, top, down)) {
            return false;
        }
        BlockPos workerStart = controllerPos.relative(expandSide).relative(expandSide);
        DataResult<BlockPos> workerEndResult = validateBlockLine(
            level,
            expandSide,
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
            expandSide,
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
            expandSide,
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
            expandSide,
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
            expandSide,
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
            expandSide,
            lowerPatternBusStart,
            matchingStateFacing(NEBlocks.CRAFTING_PATTERN_BUS, back)
        );
        if (lowerPatternBusEndResult.isError()) {
            return false;
        }
        BlockPos lowerPatternBusEnd = lowerPatternBusEndResult.getOrThrow();

        List<BlockPos> endCasing = Stream.of(
            workerEnd,
            upperParallelCoreEnd,
            lowerParallelCoreEnd,
            upperPatternBusEnd,
            lowerPatternBusEnd,
            ventEnd
        ).map(it -> it.relative(expandSide)).toList();

        if (!ensureSameSurface(endCasing)) {
            return false;
        }

        return validateBlocks(level, endCasing, BlockState::is, NEBlocks.CRAFTING_CASING);
    }

    @Override
    public boolean isValidBlockEntity(BlockEntity te) {
        return (te instanceof ECOCraftingNetworkSwitchBlockEntity networkSwitch
            && networkSwitch.getLevel() instanceof ServerLevel level
            && isNetworkSwitchAt(level, networkSwitch.getBlockPos()))
            || (te instanceof NEBlockEntity<?, ?> neBlockEntity
                && neBlockEntity.getCalculator() instanceof NECraftingClusterCalculator);
    }

    @Override
    protected boolean isAllowedNonEntityBlock(ServerLevel level, BlockPos pos) {
        return isNetworkSwitchAt(level, pos);
    }

    private static boolean isNetworkSwitchAt(ServerLevel level, BlockPos switchPos) {
        BlockState switchState = level.getBlockState(switchPos);
        if (!switchState.is(NEBlocks.CRAFTING_NETWORK_SWITCH)
            && !switchState.is(NEBlocks.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(switchPos.relative(direction)) instanceof ECOCraftingSystemBlockEntity controller
                && NENetworkSwitchUtil.canUseNetworkSwitch(controller.getTier())
                && NENetworkSwitchUtil.isSwitchPosition(
                    switchPos,
                    controller.getBlockPos(),
                    controller.getBlockState()
                )) {
                return true;
            }
        }
        return false;
    }

    private void applyNetworkMode(boolean networkMode, boolean highEnergyNetworkMode) {
        this.networkMode = networkMode;
        this.highEnergyNetworkMode = highEnergyNetworkMode;
        if (target.getCluster() != null) {
            target.getCluster().setNetworkMode(networkMode);
            target.getCluster().setHighEnergyNetworkMode(highEnergyNetworkMode);
        }
    }

    private void applyNetworkMode(BlockState switchState) {
        boolean highEnergyNetworkMode = switchState.is(NEBlocks.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH);
        applyNetworkMode(
            highEnergyNetworkMode || switchState.is(NEBlocks.CRAFTING_NETWORK_SWITCH),
            highEnergyNetworkMode
        );
    }

    private boolean validateCasingOrNetworkSwitch(
        ServerLevel level,
        BlockPos controllerPos,
        IECOTier tier,
        Direction top,
        Direction down,
        Direction switchSide
    ) {
        BlockPos center = controllerPos.relative(switchSide);
        BlockState centerState = level.getBlockState(center);
        boolean networkSwitch = centerState.is(NEBlocks.CRAFTING_NETWORK_SWITCH)
            || centerState.is(NEBlocks.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH);
        if (!centerState.is(NEBlocks.CRAFTING_CASING) && !networkSwitch) {
            return false;
        }
        if (networkSwitch && !NENetworkSwitchUtil.canUseNetworkSwitch(tier)) {
            return false;
        }
        return validateBlock(level, center.relative(top), BlockState::is, NEBlocks.CRAFTING_CASING)
            && validateBlock(level, center.relative(down), BlockState::is, NEBlocks.CRAFTING_CASING);
    }

    private static boolean validateHatchAndInterface(ServerLevel level, BlockPos interfacePos, Direction top, Direction down) {
        if (!validateBlock(level, interfacePos, BlockState::is, NEBlocks.CRAFTING_INTERFACE)) {
            return false;
        }
        if (!validateBlock(level, interfacePos.relative(top), BlockState::is, NEBlocks.INPUT_HATCH)) {
            return false;
        }
        return validateBlock(level, interfacePos.relative(down), BlockState::is, NEBlocks.OUTPUT_HATCH);
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
