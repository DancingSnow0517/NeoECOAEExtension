package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationCoolingController;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationParallelCore;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationThreadingCore;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationCoolingControllerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationNetworkSwitchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.List;
import java.util.function.BiPredicate;

public class NEComputationClusterCalculator extends NENetworkSwitchClusterCalculator<NEComputationCluster, ECOComputationSystemBlockEntity> {
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
        // The AE2 crafting service may have already rebuilt its CPU list before the
        // multiblock calculator finished attaching all members. Publish the custom
        // CPU now so it cannot remain absent until a world reload.
        cluster.notifyGridCpuChange();
    }

    @Override
    protected Class<ECOComputationSystemBlockEntity> systemBlockEntityType() {
        return ECOComputationSystemBlockEntity.class;
    }

    @Override
    protected Class<?> calculatorType() {
        return NEComputationClusterCalculator.class;
    }

    @Override
    protected Class<? extends BlockEntity> networkSwitchBlockEntityType() {
        return ECOComputationNetworkSwitchBlockEntity.class;
    }

    @Override
    protected Holder<Block> normalNetworkSwitch() {
        return NEBlocks.COMPUTATION_NETWORK_SWITCH;
    }

    @Override
    protected Holder<Block> highEnergyNetworkSwitch() {
        return NEBlocks.COMPUTATION_HIGH_ENERGY_NETWORK_SWITCH;
    }

    @Override
    protected BooleanProperty normalNetworkSwitchProperty() {
        return ECOComputationSystem.NETWORK_SWITCH;
    }

    @Override
    protected BooleanProperty highEnergyNetworkSwitchProperty() {
        return ECOComputationSystem.HIGH_ENERGY_NETWORK_SWITCH;
    }

    @Override
    protected IECOTier tierOf(ECOComputationSystemBlockEntity controller) {
        return controller.getTier();
    }

    @Override
    protected boolean verifyControllerStructure(
        ServerLevel level,
        BlockPos min,
        BlockPos max,
        ControllerContext<ECOComputationSystemBlockEntity> context
    ) {
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
        return verifyEitherMirror(
            mirrored -> verifyStructure(
                level, controllerPos, tier, front, back, top, down,
                mirrored ? left : right, mirrored ? right : left, mirrored ? left : right, mirrored
            ),
            controller::setMirrored,
            mirrored -> {
                setCoolingControllersMirrored(level, min, max, mirrored);
                syncNetworkSwitchState(level, controllerPos, controllerState, mirrored);
            },
            () -> clearNetworkSwitchState(level, controllerPos, controllerState)
        );
    }

    private static void setCoolingControllersMirrored(ServerLevel level, BlockPos min, BlockPos max, boolean mirrored) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockEntity(pos) instanceof ECOComputationCoolingControllerBlockEntity cooler) {
                cooler.setMirrored(mirrored);
            }
        }
    }

    protected boolean verifyStructure(
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
        return validateBlocks(level, tailCasings, BlockState::is, NEBlocks.COMPUTATION_CASING);
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
