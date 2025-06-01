package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
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
import com.lowdragmc.lowdraglib.syncdata.IManaged;
import com.lowdragmc.lowdraglib.syncdata.IManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class ECOEnergyCellBlockEntity extends AbstractStorageBlockEntity<ECOEnergyCellBlockEntity>
    implements IExternalPowerSink, IGridTickable, IAsyncAutoSyncBlockEntity, IAutoPersistBlockEntity, IManaged {
    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ECOEnergyCellBlockEntity.class);
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);
    @Getter
    private final IECOTier tier;
    private byte currentDisplayLevel;

    @Persisted
    private final StoredEnergyAmount energyStored;

    @Persisted
    private boolean neighborChangePending = false;


    public ECOEnergyCellBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
        getMainNode()
            .addService(IAEPowerStorage.class, this)
            .addService(IGridTickable.class, this);
        this.energyStored = new StoredEnergyAmount(0, tier.getPowerStorageSize(), this::emitPowerEvent);
        this.tier = tier;
    }

    private void emitPowerEvent(GridPowerStorageStateChanged.PowerEventType type) {
        getMainNode().ifPresent(
            grid -> grid.postEvent(new GridPowerStorageStateChanged(this, type)));
    }

    @Override
    public final double injectAEPower(double amt, Actionable mode) {
        var inserted = this.energyStored.insert(amt, mode == appeng.api.config.Actionable.MODULATE);
        if (mode == Actionable.MODULATE && inserted > 0) {
            this.onEnergyChanged();
        }
        return amt - inserted;
    }

    @Override
    public final double extractAEPower(double amt, Actionable mode, PowerMultiplier pm) {
        double extracted = pm.divide(this.extractAEPower(pm.multiply(amt), mode));
        if (mode == Actionable.MODULATE && extracted > 0) {
            this.onEnergyChanged();
        }
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

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public IManagedStorage getSyncStorage() {
        return syncStorage;
    }

    @Override
    public void onChanged() {
        setChanged();
        markForUpdate();
    }

    private void onEnergyChanged() {
        setChangedNoTicketUpdate();

        if (!neighborChangePending) {
            neighborChangePending = true;
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }
    }

    @Override
    public IManagedStorage getRootStorage() {
        return syncStorage;
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
    public double injectExternalPower(PowerUnit externalUnit, double amount, Actionable mode) {
        return PowerUnit.AE.convertTo(externalUnit, injectAEPower(PowerUnit.AE.convertTo(externalUnit, amount), mode));
    }

    @Override
    public double getExternalPowerDemand(PowerUnit externalUnit, double maxPowerRequired) {
        return PowerUnit.AE.convertTo(externalUnit, Math.max(0.0, getAEMaxPower() - getAECurrentPower()));
    }

    private void setChangedNoTicketUpdate() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            throw new IllegalArgumentException("Expected server level, not " + this.level);
        }

        var pos = getBlockPos();
        var chunk = serverLevel.getChunkSource().getChunk(
            SectionPos.blockToSectionCoord(pos.getX()),
            SectionPos.blockToSectionCoord(pos.getZ()),
            ChunkStatus.FULL,
            false
        );
        if (chunk != null) {
            chunk.setUnsaved(true);
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, !neighborChangePending);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
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

        int storageLevel = getStorageLevelFromFillFactor(this.energyStored.getAmount() / this.energyStored.getMaximum());

        if (this.currentDisplayLevel != storageLevel) {
            this.currentDisplayLevel = (byte) storageLevel;
            this.level.setBlockAndUpdate(
                this.worldPosition,
                this.level.getBlockState(this.worldPosition).setValue(ECOEnergyCellBlock.LEVEL, storageLevel)
            );
        }
    }
}
