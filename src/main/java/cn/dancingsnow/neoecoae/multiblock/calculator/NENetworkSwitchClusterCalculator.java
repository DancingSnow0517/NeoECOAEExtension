package cn.dancingsnow.neoecoae.multiblock.calculator;

import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import cn.dancingsnow.neoecoae.multiblock.network.NENetworkSwitchUtil;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Multiblock logic shared by the systems that host a network switch (crafting and computation). Subclasses supply
 * the block entities, blocks and properties that differ between them.
 */
public abstract class NENetworkSwitchClusterCalculator<C extends NECluster<C>, S extends NEBlockEntity<C, S>>
    extends NEClusterCalculator<C> {

    public NENetworkSwitchClusterCalculator(NEBlockEntity<C, ?> t) {
        super(t);
    }

    protected abstract Class<S> systemBlockEntityType();

    protected abstract Class<?> calculatorType();

    protected abstract Class<? extends BlockEntity> networkSwitchBlockEntityType();

    protected abstract Holder<Block> normalNetworkSwitch();

    protected abstract Holder<Block> highEnergyNetworkSwitch();

    protected abstract BooleanProperty normalNetworkSwitchProperty();

    protected abstract BooleanProperty highEnergyNetworkSwitchProperty();

    protected abstract IECOTier tierOf(S controller);

    protected abstract boolean verifyControllerStructure(
        ServerLevel level,
        BlockPos min,
        BlockPos max,
        ControllerContext<S> context
    );

    @Override
    public boolean verifyInternalStructure(ServerLevel level, BlockPos min, BlockPos max) {
        Optional<ControllerContext<S>> contextResult = findUniqueController(
            level, min, max, systemBlockEntityType()
        );
        if (contextResult.isEmpty()) return false;
        ControllerContext<S> context = contextResult.orElseThrow();
        NEClusterCalculator<C> controllerCalculator = target.getLevel() instanceof ServerLevel
            ? calculatorOf(context.controller())
            : null;
        if (controllerCalculator != null && controllerCalculator != this) {
            return controllerCalculator.verifyInternalStructure(level, min, max);
        }
        return verifyControllerStructure(level, min, max, context);
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        NEClusterCalculator<C> controller = candidateControllerCalculator(min, max);
        return controller != null && controller != this
            ? controller.checkMultiblockScale(min, max)
            : super.checkMultiblockScale(min, max);
    }

    private NEClusterCalculator<C> candidateControllerCalculator(BlockPos min, BlockPos max) {
        if (!(target.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        return findCandidateController(level, min, max, systemBlockEntityType())
            .map(this::calculatorOf)
            .orElse(null);
    }

    private NEClusterCalculator<C> calculatorOf(S controller) {
        NEClusterCalculator<C> calculator = controller.getCalculator();
        return calculatorType().isInstance(calculator) ? calculator : null;
    }

    @Override
    public boolean isValidBlockEntity(BlockEntity te) {
        if (networkSwitchBlockEntityType().isInstance(te)) {
            return te.getLevel() instanceof ServerLevel level && isNetworkSwitchAt(level, te.getBlockPos());
        }
        return te instanceof NEBlockEntity<?, ?> neBlockEntity
            && calculatorType().isInstance(neBlockEntity.getCalculator());
    }

    private boolean isNetworkSwitchAt(ServerLevel level, BlockPos switchPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockEntity candidate = level.getBlockEntity(switchPos.relative(direction));
            if (!systemBlockEntityType().isInstance(candidate)) continue;
            S controller = systemBlockEntityType().cast(candidate);
            if (NENetworkSwitchUtil.canUseNetworkSwitch(tierOf(controller))
                && NENetworkSwitchUtil.isSwitchPosition(switchPos, controller.getBlockPos(), controller.getBlockState())) {
                return true;
            }
        }
        return false;
    }

    protected final void syncNetworkSwitchState(ServerLevel level, BlockPos controllerPos, BlockState controllerState, boolean mirrored) {
        BlockPos switchPos = NENetworkSwitchUtil.switchPosition(controllerPos, controllerState, mirrored);
        BlockState switchState = level.getBlockState(switchPos);
        boolean normal = switchState.is(normalNetworkSwitch());
        boolean highEnergy = switchState.is(highEnergyNetworkSwitch());
        NENetworkSwitchUtil.syncFormed(level, controllerPos, controllerState, mirrored);
        level.setBlock(controllerPos, level.getBlockState(controllerPos)
            .setValue(normalNetworkSwitchProperty(), normal)
            .setValue(highEnergyNetworkSwitchProperty(), highEnergy), Block.UPDATE_CLIENTS);
    }

    protected final void clearNetworkSwitchState(ServerLevel level, BlockPos controllerPos, BlockState controllerState) {
        NENetworkSwitchUtil.clearFormed(level, controllerPos, controllerState);
        BlockState current = level.getBlockState(controllerPos);
        level.setBlock(controllerPos, current
            .setValue(normalNetworkSwitchProperty(), false)
            .setValue(highEnergyNetworkSwitchProperty(), false), Block.UPDATE_CLIENTS);
    }

    protected final boolean validateCasingOrNetworkSwitch(
        ServerLevel level,
        BlockPos controllerPos,
        IECOTier tier,
        Direction top,
        Direction down,
        Direction side
    ) {
        BlockPos center = controllerPos.relative(side);
        BlockState state = level.getBlockState(center);
        boolean sw = state.is(normalNetworkSwitch()) || state.is(highEnergyNetworkSwitch());
        if (!state.is(casing()) && !sw) return false;
        if (sw && !NENetworkSwitchUtil.canUseNetworkSwitch(tier)) return false;
        return validateBlock(level, center.relative(top), BlockState::is, casing())
            && validateBlock(level, center.relative(down), BlockState::is, casing());
    }
}
