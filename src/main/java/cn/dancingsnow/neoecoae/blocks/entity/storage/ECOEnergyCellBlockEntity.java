package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnits;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.events.GridPowerStorageStateChanged;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.blockentity.powersink.IExternalPowerSink;
import appeng.me.energy.StoredEnergyAmount;
import appeng.util.Platform;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.slf4j.Logger;

public class ECOEnergyCellBlockEntity extends AbstractStorageBlockEntity<ECOEnergyCellBlockEntity>
        implements IExternalPowerSink, IGridTickable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> LOGGED_POWER_CHANGES = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_ENERGY_TICKS = ConcurrentHashMap.newKeySet();

    @Getter
    private final IECOTier tier;

    private byte currentDisplayLevel;

    private final StoredEnergyAmount energyStored;

    private boolean neighborChangePending = false;

    public ECOEnergyCellBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, IECOTier tier) {
        super(type, pos, blockState);
        getMainNode().addService(IAEPowerStorage.class, this).addService(IGridTickable.class, this);
        this.energyStored = new StoredEnergyAmount(0, tier.getPowerStorageSize(), this::emitPowerEvent);
        this.tier = tier;
    }

    private void emitPowerEvent(GridPowerStorageStateChanged.PowerEventType type) {
        getMainNode().ifPresent(grid -> grid.postEvent(new GridPowerStorageStateChanged(this, type)));
    }

    @Override
    public final double injectAEPower(double amt, Actionable mode) {
        double before = this.energyStored.getAmount();
        var inserted = this.energyStored.insert(amt, mode == appeng.api.config.Actionable.MODULATE);
        if (mode == Actionable.MODULATE && inserted > 0) {
            this.onEnergyChanged();
        }
        logPowerChange("injectAEPower", amt, inserted, mode, before, this.energyStored.getAmount());
        return amt - inserted;
    }

    @Override
    public final double extractAEPower(double amt, Actionable mode, PowerMultiplier pm) {
        double before = this.energyStored.getAmount();
        double extracted = pm.divide(this.extractAEPower(pm.multiply(amt), mode));
        if (mode == Actionable.MODULATE && extracted > 0) {
            this.onEnergyChanged();
        }
        logPowerChange("extractAEPower", amt, extracted, mode, before, this.energyStored.getAmount());
        return extracted;
    }

    private double extractAEPower(double amt, Actionable mode) {
        return this.energyStored.extract(amt, mode == appeng.api.config.Actionable.MODULATE);
    }

    @Override
    public double getAEMaxPower() {
        return this.energyStored.getMaximum();
    }

    @Override
    public double getAECurrentPower() {
        return this.energyStored.getAmount();
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return true;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return formed ? AccessRestriction.READ_WRITE : AccessRestriction.NO_ACCESS;
    }

    public void notifyPersistence() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getServer().executeIfPossible(() -> {
                setChanged();
                markForUpdate();
            });
        }
    }

    private void onEnergyChanged() {
        setChangedNoTicketUpdate();

        if (!neighborChangePending) {
            neighborChangePending = true;
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }
        logEnergyTick("onEnergyChanged");
    }

    @Override
    public void onReady() {
        super.onReady();
        final int value = this.level.getBlockState(this.worldPosition).getValue(ECOEnergyCellBlock.LEVEL);
        this.currentDisplayLevel = (byte) value;
        this.updateStateForPowerLevel();
        getMainNode().setIdlePowerUsage(0);
    }

    @Override
    public double injectExternalPower(PowerUnits externalUnit, double amount, Actionable mode) {
        double aeAmount = externalUnit.convertTo(PowerUnits.AE, amount);
        return PowerUnits.AE.convertTo(externalUnit, injectAEPower(aeAmount, mode));
    }

    @Override
    public double getExternalPowerDemand(PowerUnits externalUnit, double maxPowerRequired) {
        return PowerUnits.AE.convertTo(externalUnit, Math.max(0.0, getAEMaxPower() - getAECurrentPower()));
    }

    private void setChangedNoTicketUpdate() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            throw new IllegalArgumentException("Expected server level, not " + this.level);
        }

        var pos = getBlockPos();
        var chunk = serverLevel
                .getChunkSource()
                .getChunk(
                        SectionPos.blockToSectionCoord(pos.getX()),
                        SectionPos.blockToSectionCoord(pos.getZ()),
                        ChunkStatus.FULL,
                        false);
        if (chunk != null) {
            chunk.setUnsaved(true);
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, !neighborChangePending, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        logEnergyTick("tickingRequest:" + ticksSinceLastCall);
        if (Platform.areBlockEntitiesTicking(getLevel(), getBlockPos())) {
            if (neighborChangePending) {
                neighborChangePending = false;
                setChanged(); // update comparators
                updateStateForPowerLevel(); // and update block state
            }
            return TickRateModulation.SLEEP;
        } else {
            return TickRateModulation.IDLE;
        }
    }

    public static int getStorageLevelFromFillFactor(double fillFactor) {
        return (int) Math.floor(4 * Mth.clamp(fillFactor + 0.01, 0, 1));
    }

    private void updateStateForPowerLevel() {
        if (this.isRemoved()) {
            return;
        }

        int storageLevel =
                getStorageLevelFromFillFactor(this.energyStored.getAmount() / this.energyStored.getMaximum());
        logEnergyTick("updateStateForPowerLevel:" + storageLevel);

        if (this.currentDisplayLevel != storageLevel) {
            BlockState oldState = this.level.getBlockState(this.worldPosition);
            BlockState newState = oldState.setValue(ECOEnergyCellBlock.LEVEL, storageLevel);
            logDisplayLevelUpdate(oldState, newState, storageLevel);
            this.currentDisplayLevel = (byte) storageLevel;
            this.level.setBlockAndUpdate(this.worldPosition, newState);
        }
    }

    private void logDisplayLevelUpdate(BlockState oldState, BlockState newState, int newDisplayLevel) {
        // No-op: verbose debug logging removed.
    }

    private void logPowerChange(
            String source, double requested, double moved, Actionable mode, double before, double after) {
        // No-op: verbose debug logging removed.
    }

    private void logEnergyTick(String source) {
        // No-op: per-tick verbose debug logging removed.
    }
}
