package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingParallelCore;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import cn.dancingsnow.neoecoae.multiblock.network.NENetworkSwitchUtil;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import java.util.Optional;
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
    public NECraftingClusterCalculator(NEBlockEntity<NECraftingCluster, ?> t) {
        super(t);
    }

    @Override
    protected int maxLength() {
        return NEConfig.craftingSystemMaxLength;
    }

    @Override
    protected Holder<Block> casing() {
        return NEBlocks.CRAFTING_CASING;
    }

    @Override
    public NECraftingCluster createCluster(ServerLevel level, BlockPos min, BlockPos max) {
        return new NECraftingCluster(min, max);
    }

    @Override
    protected void onClusterAttached(NECraftingCluster cluster) {
        NELogicalNetworkManager.attach(cluster);
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        Optional<ControllerContext<ECOCraftingSystemBlockEntity>> contextResult = findUniqueController(
            level, min, max, ECOCraftingSystemBlockEntity.class
        );
        if (contextResult.isEmpty()) return false;
        ControllerContext<ECOCraftingSystemBlockEntity> context = contextResult.orElseThrow();
        ECOCraftingSystemBlockEntity controller = context.controller();
        BlockPos controllerPos = context.position();
        IECOTier tier = controller.getTier();
        BlockState controllerState = context.state();
        Direction front = context.front();
        Direction back = context.back();
        Direction top = context.top();
        Direction down = context.down();
        Direction left = context.left();
        Direction right = context.right();
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, right, left, right)) {
            controller.setMirrored(false);
            syncNetworkSwitchState(level, controllerPos, controllerState, false);
            return true;
        }
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, left, right, left)) {
            controller.setMirrored(true);
            syncNetworkSwitchState(level, controllerPos, controllerState, true);
            return true;
        }
        clearNetworkSwitchState(level, controllerPos, controllerState);
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
        if (!validateCasing(level, controllerPos, top, down, interfaceSide == networkSwitchSide ? expandSide : interfaceSide)) return false;
        if (!validateCasing(level, controllerPos, top, down, back)) return false;
        if (!validateCasing(level, controllerPos.relative(back).relative(expandSide), top, down)) return false;
        BlockPos interfacePos = controllerPos.relative(back).relative(interfaceSide);
        if (!validateHatchAndInterface(level, interfacePos, top, down)) {
            return false;
        }
        BlockPos workerStart = controllerPos.relative(expandSide).relative(expandSide);
        Optional<BlockPos> workerEndResult = validateBlockLine(
            level,
            expandSide,
            workerStart,
            matchingStateFacing(NEBlocks.CRAFTING_WORKER, front)
                .or(matchingStateFacing(NEBlocks.FX_MONITOR_CORE, front))
        );
        if (workerEndResult.isEmpty()) {
            return false;
        }
        BlockPos workerEnd = workerEndResult.orElseThrow();

        BlockPos upperParallelCoreStart = workerStart.relative(top);
        Optional<BlockPos> upperParallelCoreEndResult = validateBlockLine(
            level,
            expandSide,
            upperParallelCoreStart,
            matchingParallelCore(level, tier, front)
        );
        if (upperParallelCoreEndResult.isEmpty()) {
            return false;
        }
        BlockPos upperParallelCoreEnd = upperParallelCoreEndResult.orElseThrow();

        BlockPos lowerParallelCoreStart = workerStart.relative(down);
        Optional<BlockPos> lowerParallelCoreEndResult = validateBlockLine(
            level,
            expandSide,
            lowerParallelCoreStart,
            matchingParallelCore(level, tier, front)
        );
        if (lowerParallelCoreEndResult.isEmpty()) {
            return false;
        }
        BlockPos lowerParallelCoreEnd = lowerParallelCoreEndResult.orElseThrow();

        BlockPos ventStart = workerStart.relative(back);
        Optional<BlockPos> ventEndResult = validateBlockLine(
            level,
            expandSide,
            ventStart,
            matchingStateFacing(NEBlocks.CRAFTING_VENT, back)
        );
        if (ventEndResult.isEmpty()) {
            return false;
        }
        BlockPos ventEnd = ventEndResult.orElseThrow();

        BlockPos upperPatternBusStart = ventStart.relative(top);
        Optional<BlockPos> upperPatternBusEndResult = validateBlockLine(
            level,
            expandSide,
            upperPatternBusStart,
            matchingStateFacing(NEBlocks.CRAFTING_PATTERN_BUS, back)
        );
        if (upperPatternBusEndResult.isEmpty()) {
            return false;
        }
        BlockPos upperPatternBusEnd = upperPatternBusEndResult.orElseThrow();

        BlockPos lowerPatternBusStart = ventStart.relative(down);
        Optional<BlockPos> lowerPatternBusEndResult = validateBlockLine(
            level,
            expandSide,
            lowerPatternBusStart,
            matchingStateFacing(NEBlocks.CRAFTING_PATTERN_BUS, back)
        );
        if (lowerPatternBusEndResult.isEmpty()) {
            return false;
        }
        BlockPos lowerPatternBusEnd = lowerPatternBusEndResult.orElseThrow();

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
        if (te instanceof cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingNetworkSwitchBlockEntity sw) {
            return sw.getLevel() instanceof ServerLevel level && isNetworkSwitchAt(level, sw.getBlockPos());
        }
        return (te instanceof NEBlockEntity<?,?> neBlockEntity && neBlockEntity.getCalculator() instanceof NECraftingClusterCalculator);
    }

    private static boolean isNetworkSwitchAt(ServerLevel level, BlockPos switchPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockEntity(switchPos.relative(direction)) instanceof ECOCraftingSystemBlockEntity controller
                && NENetworkSwitchUtil.canUseNetworkSwitch(controller.getTier())
                && NENetworkSwitchUtil.isSwitchPosition(switchPos, controller.getBlockPos(), controller.getBlockState())) return true;
        }
        return false;
    }

    private static void syncNetworkSwitchState(ServerLevel level, BlockPos controllerPos, BlockState controllerState, boolean mirrored) {
        BlockPos switchPos = NENetworkSwitchUtil.switchPosition(controllerPos, controllerState, mirrored);
        BlockState switchState = level.getBlockState(switchPos);
        boolean normal = switchState.is(NEBlocks.CRAFTING_NETWORK_SWITCH);
        boolean highEnergy = switchState.is(NEBlocks.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH);
        NENetworkSwitchUtil.syncFormed(level, controllerPos, controllerState, mirrored);
        level.setBlock(controllerPos, level.getBlockState(controllerPos)
            .setValue(cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem.NETWORK_SWITCH, normal)
            .setValue(cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem.HIGH_ENERGY_NETWORK_SWITCH, highEnergy), Block.UPDATE_CLIENTS);
    }

    private static void clearNetworkSwitchState(ServerLevel level, BlockPos controllerPos, BlockState controllerState) {
        NENetworkSwitchUtil.clearFormed(level, controllerPos, controllerState);
        BlockState current = level.getBlockState(controllerPos);
        level.setBlock(controllerPos, current
            .setValue(cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem.NETWORK_SWITCH, false)
            .setValue(cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem.HIGH_ENERGY_NETWORK_SWITCH, false), Block.UPDATE_CLIENTS);
    }

    private boolean validateCasingOrNetworkSwitch(ServerLevel level, BlockPos controllerPos, IECOTier tier, Direction top, Direction down, Direction side) {
        BlockPos center = controllerPos.relative(side);
        BlockState state = level.getBlockState(center);
        boolean sw = state.is(NEBlocks.CRAFTING_NETWORK_SWITCH) || state.is(NEBlocks.CRAFTING_HIGH_ENERGY_NETWORK_SWITCH);
        if (!state.is(NEBlocks.CRAFTING_CASING) && !sw) return false;
        if (sw && !NENetworkSwitchUtil.canUseNetworkSwitch(tier)) return false;
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

    private BiPredicate<BlockState, BlockPos> matchingParallelCore(
        Level level,
        IECOTier tier,
        Direction facing
    ) {
        return (s, p) -> s.getBlock() instanceof ECOCraftingParallelCore core
            && tier.supportsComponentTier(core.getBlockEntity(level, p).getTier())
            && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing;
    }

}
