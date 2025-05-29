package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IAEPowerStorage;
import cn.dancingsnow.neoecoae.api.IECOTier;
import com.lowdragmc.lowdraglib.syncdata.IManaged;
import com.lowdragmc.lowdraglib.syncdata.IManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAsyncAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoPersistBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.FieldManagedStorage;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MachineEnergyCellBlockEntity extends AbstractStorageBlockEntity<MachineEnergyCellBlockEntity>
    implements IAEPowerStorage, IAsyncAutoSyncBlockEntity, IAutoPersistBlockEntity, IManaged {
    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(MachineEnergyCellBlockEntity.class);
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    private final IECOTier tier;

    @DescSynced
    @Persisted
    private double powerStorage = 0;

    public MachineEnergyCellBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
        getMainNode().addService(IAEPowerStorage.class, this);
        this.tier = tier;
    }

    @Override
    public double injectAEPower(double amt, Actionable mode) {
        if (amt + powerStorage > tier.getPowerStorageSize()) {
            double oldValue = powerStorage;
            if (!mode.isSimulate()) {
                powerStorage = tier.getPowerStorageSize();
            }
            return amt - (tier.getPowerStorageSize() - oldValue);
        }
        if (!mode.isSimulate()) {
            powerStorage += amt;
        }
        return 0;
    }

    @Override
    public double extractAEPower(double amt, Actionable mode, PowerMultiplier usePowerMultiplier) {
        return usePowerMultiplier.divide(extractAEPower(usePowerMultiplier.multiply(amt), mode));
    }

    public double extractAEPower(double amt, Actionable mode) {
        if (amt > powerStorage) {
            if (!mode.isSimulate()) {
                powerStorage = 0;
            }
            return powerStorage;
        }
        if (!mode.isSimulate()) {
            powerStorage -= amt;
        }
        return amt;
    }

    @Override
    public double getAEMaxPower() {
        return tier.getPowerStorageSize();
    }

    @Override
    public double getAECurrentPower() {
        return powerStorage;
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return false;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return formed ? AccessRestriction.WRITE : AccessRestriction.NO_ACCESS;
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

    @Override
    public IManagedStorage getRootStorage() {
        return syncStorage;
    }
}
