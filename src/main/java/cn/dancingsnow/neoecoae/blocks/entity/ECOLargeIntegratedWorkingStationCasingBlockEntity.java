package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.powersink.IExternalPowerSink;
import appeng.helpers.ForgeEnergyAdapter;
import cn.dancingsnow.neoecoae.blocks.ECOLargeIntegratedWorkingStationCasing;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** Upper casing side connections share the controller's existing power buffer. */
public class ECOLargeIntegratedWorkingStationCasingBlockEntity
    extends ECOMachineCasingBlockEntity<NEIntegratedWorkingStationCluster> implements IExternalPowerSink {
    private final IEnergyStorage energyAdapter = new ForgeEnergyAdapter(this);

    public ECOLargeIntegratedWorkingStationCasingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
        NEClusterCalculator.Factory<NEIntegratedWorkingStationCluster> factory) {
        super(type, pos, state, factory);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        if (!formed || cluster == null || isServerStopping()) return EnumSet.noneOf(Direction.class);
        var sides = cluster.getInternalConnections(this);
        if (cluster.isTopPowerPort(worldPosition)) {
            for (Direction side : Direction.Plane.HORIZONTAL) sides.add(side);
        }
        return sides;
    }

    @Override
    public AECableType getCableConnectionType(Direction side) {
        return side != null && side.getAxis().isHorizontal()
            && getBlockState().getOptionalValue(ECOLargeIntegratedWorkingStationCasing.POWER_PORT).orElse(false)
            ? AECableType.COVERED : AECableType.NONE;
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (level == null || level.isClientSide || notLoaded() || isRemoved() || isServerStopping()) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(ECOLargeIntegratedWorkingStationCasing.POWER_PORT)) return;
        boolean port = formed && cluster != null && cluster.isTopPowerPort(worldPosition);
        if (state.getValue(ECOLargeIntegratedWorkingStationCasing.POWER_PORT) != port) {
            level.setBlock(worldPosition, state.setValue(ECOLargeIntegratedWorkingStationCasing.POWER_PORT, port), Block.UPDATE_CLIENTS);
            cn.dancingsnow.neoecoae.platform.ForgeBlockCapabilities.refresh(this);
        }
    }

    public @Nullable IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return side != null && side.getAxis().isHorizontal() && powerController() != null ? energyAdapter : null;
    }

    private @Nullable ECOLargeIntegratedWorkingStationBlockEntity powerController() {
        if (isRemoved() || !formed || cluster == null || !cluster.isTopPowerPort(worldPosition)) return null;
        var controller = cluster.getController();
        return controller != null && !controller.isRemoved() && controller.isFormed() ? controller : null;
    }

    @Override
    public double injectExternalPower(PowerUnits input, double amount, Actionable mode) {
        return PowerUnits.AE.convertTo(input, injectAEPower(input.convertTo(PowerUnits.AE, amount), mode));
    }

    @Override
    public double injectAEPower(double amount, Actionable mode) {
        var controller = powerController();
        if (controller == null || !Double.isFinite(amount) || amount <= 0) return amount;
        double remainder = controller.injectAEPower(amount, mode);
        if (mode == Actionable.MODULATE && remainder < amount) controller.setChanged();
        return remainder;
    }

    @Override
    public double getExternalPowerDemand(PowerUnits unit, double maximum) {
        return Math.max(0, Math.min(maximum, PowerUnits.AE.convertTo(unit, getAEMaxPower() - getAECurrentPower())));
    }

    @Override public double getAECurrentPower() {
        var controller = powerController();
        return controller == null ? 0 : controller.getAECurrentPower();
    }

    @Override public double getAEMaxPower() {
        var controller = powerController();
        return controller == null ? 0 : controller.getAEMaxPower();
    }

    @Override public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) { return 0; }
    @Override public boolean isAEPublicPowerStorage() { return false; }
    @Override public AccessRestriction getPowerFlow() { return AccessRestriction.WRITE; }
}
