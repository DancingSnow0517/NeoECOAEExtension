package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationCoolingController;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationParallelCore;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationThreadingCore;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import cn.dancingsnow.neoecoae.multiblock.network.NENetworkSwitchUtil;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
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

public class NEComputationClusterCalculator extends NEClusterCalculator<NEComputationCluster> {
    public NEComputationClusterCalculator(NEBlockEntity<NEComputationCluster, ?> t) {
        super(t);
    }

    @Override
    protected int maxLength() {
        return NEConfig.computationSystemMaxLength;
    }

    @Override
    protected Holder<Block> casing() {
        return NEBlocks.COMPUTATION_CASING;
    }

    @Override
    public NEComputationCluster createCluster(ServerLevel level, BlockPos min, BlockPos max) {
        return new NEComputationCluster(min, max);
    }

    @Override
    protected void onClusterAttached(NEComputationCluster cluster) {
        NELogicalNetworkManager.attach(cluster);
    }

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        Optional<ControllerContext<ECOComputationSystemBlockEntity>> contextResult = findUniqueController(
            level, min, max, ECOComputationSystemBlockEntity.class
        );
        if (contextResult.isEmpty()) return false;
        ControllerContext<ECOComputationSystemBlockEntity> context = contextResult.orElseThrow();
        ECOComputationSystemBlockEntity controller = context.controller();
        BlockPos controllerPos = context.position();
        IECOTier tier = controller.getTier();
        BlockState controllerState = context.state();
        Direction front = context.front();
        Direction back = context.back();
        Direction top = context.top();
        Direction down = context.down();
        Direction left = context.left();
        Direction right = context.right();
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, right, left, right, false)) {
            controller.setMirrored(false);
            syncNetworkSwitchState(level, controllerPos, controllerState, false);
            return true;
        }
        if (verifyStructure(level, controllerPos, tier, front, back, top, down, left, right, left, true)) {
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
        Direction networkSwitchSide,
        boolean mirrored
    ) {
        if (!validateCasingOrNetworkSwitch(level, controllerPos, tier, top, down, networkSwitchSide)) return false;
        if (!validateCasing(level, controllerPos, top, down, interfaceSide == networkSwitchSide ? expandSide : interfaceSide)) return false;
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
        Optional<BlockPos> connectorEndResult = validateBlockLine(
            level,
            expandSide,
            connectorStart,
            matchingStateFacing(
                NEBlocks.COMPUTATION_TRANSMITTER,
                front
            )
        );
        if (connectorEndResult.isEmpty()) {
            return false;
        }
        BlockPos connectorEnd = connectorEndResult.orElseThrow();

        BlockPos threadingCoreStart = connectorStart.relative(back);
        Optional<BlockPos> threadingCoreEndResult = validateBlockLine(
            level,
            expandSide,
            threadingCoreStart,
            matchingThreadingCore(level, tier, back)
        );
        if (threadingCoreEndResult.isEmpty()) {
            return false;
        }
        BlockPos threadingCoreEnd = threadingCoreEndResult.orElseThrow();

        BlockPos upperParallelCoreStart = threadingCoreStart.relative(top);
        Optional<BlockPos> upperParallelCoreEndResult = validateBlockLine(
            level,
            expandSide,
            upperParallelCoreStart,
            matchingParallelCore(level, tier, back)
        );
        if (upperParallelCoreEndResult.isEmpty()) {
            return false;
        }
        BlockPos upperParallelCoreEnd = upperParallelCoreEndResult.orElseThrow();

        BlockPos lowerParallelCoreStart = threadingCoreStart.relative(down);
        Optional<BlockPos> lowerParallelCoreEndResult = validateBlockLine(
            level,
            expandSide,
            lowerParallelCoreStart,
            matchingParallelCore(level, tier, back)
        );
        if (lowerParallelCoreEndResult.isEmpty()) {
            return false;
        }
        BlockPos lowerParallelCoreEnd = lowerParallelCoreEndResult.orElseThrow();

        BlockPos upperDriveStart = connectorStart.relative(top);
        Optional<BlockPos> upperDriveEndResult = validateBlockLine(
            level,
            expandSide,
            upperDriveStart,
            matchingStateFacing(NEBlocks.COMPUTATION_DRIVE, front)
        );
        if (upperDriveEndResult.isEmpty()) {
            return false;
        }
        BlockPos upperDriveEnd = upperDriveEndResult.orElseThrow();

        BlockPos lowerDriveStart = connectorStart.relative(down);
        Optional<BlockPos> lowerDriveEndResult = validateBlockLine(
            level,
            expandSide,
            lowerDriveStart,
            matchingStateFacing(NEBlocks.COMPUTATION_DRIVE, front)
        );
        if (lowerDriveEndResult.isEmpty()) {
            return false;
        }
        BlockPos lowerDriveEnd = lowerDriveEndResult.orElseThrow();

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
        if (te instanceof cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationNetworkSwitchBlockEntity sw) {
            return sw.getLevel() instanceof ServerLevel level && isNetworkSwitchAt(level, sw.getBlockPos());
        }
        return (te instanceof NEBlockEntity<?,?> neBlockEntity && neBlockEntity.getCalculator() instanceof NEComputationClusterCalculator);
    }

    private static boolean isNetworkSwitchAt(ServerLevel level, BlockPos switchPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (level.getBlockEntity(switchPos.relative(direction)) instanceof ECOComputationSystemBlockEntity controller
                && NENetworkSwitchUtil.canUseNetworkSwitch(controller.getTier())
                && NENetworkSwitchUtil.isSwitchPosition(switchPos, controller.getBlockPos(), controller.getBlockState())) return true;
        }
        return false;
    }

    private static void syncNetworkSwitchState(ServerLevel level, BlockPos controllerPos, BlockState controllerState, boolean mirrored) {
        BlockPos switchPos = NENetworkSwitchUtil.switchPosition(controllerPos, controllerState, mirrored);
        BlockState switchState = level.getBlockState(switchPos);
        boolean normal = switchState.is(NEBlocks.COMPUTATION_NETWORK_SWITCH);
        boolean highEnergy = switchState.is(NEBlocks.COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH);
        NENetworkSwitchUtil.syncFormed(level, controllerPos, controllerState, mirrored);
        level.setBlock(controllerPos, level.getBlockState(controllerPos)
            .setValue(cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem.NETWORK_SWITCH, normal)
            .setValue(cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem.HIGH_ENERGY_NETWORK_SWITCH, highEnergy), Block.UPDATE_CLIENTS);
    }

    private static void clearNetworkSwitchState(ServerLevel level, BlockPos controllerPos, BlockState controllerState) {
        NENetworkSwitchUtil.clearFormed(level, controllerPos, controllerState);
        BlockState current = level.getBlockState(controllerPos);
        level.setBlock(controllerPos, current
            .setValue(cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem.NETWORK_SWITCH, false)
            .setValue(cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem.HIGH_ENERGY_NETWORK_SWITCH, false), Block.UPDATE_CLIENTS);
    }

    private boolean validateCasingOrNetworkSwitch(ServerLevel level, BlockPos controllerPos, IECOTier tier, Direction top, Direction down, Direction side) {
        BlockPos center = controllerPos.relative(side);
        BlockState state = level.getBlockState(center);
        boolean sw = state.is(NEBlocks.COMPUTATION_NETWORK_SWITCH) || state.is(NEBlocks.COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH);
        if (!state.is(NEBlocks.COMPUTATION_CASING) && !sw) return false;
        if (sw && !NENetworkSwitchUtil.canUseNetworkSwitch(tier)) return false;
        return validateBlock(level, center.relative(top), BlockState::is, NEBlocks.COMPUTATION_CASING)
            && validateBlock(level, center.relative(down), BlockState::is, NEBlocks.COMPUTATION_CASING);
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

}
