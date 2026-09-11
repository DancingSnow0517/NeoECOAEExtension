package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.UUID;

public final class ECOStorageShard {
    private final int index;
    private final ECODriveBlockEntity drive;
    private final ECOStorageCell storage;
    private final String fingerprint;
    private final UUID cellId;
    private final long leaseGeneration;

    ECOStorageShard(int index, ECODriveBlockEntity drive, ECOStorageCell storage, UUID domainId,
                    net.minecraft.nbt.CompoundTag recovery) {
        this.index = index;
        this.drive = drive;
        this.storage = storage;
        ECOFiniteCellMetadata.State metadata;
        if (recovery != null) {
            metadata = ECOFiniteCellMetadata.read(drive.getCellStack());
            if (!recovery.hasUUID("cellId") || !recovery.getUUID("cellId").equals(metadata.cellId())) {
                throw new IllegalStateException("Finite storage cell identity changed");
            }
            this.leaseGeneration = recovery.getLong("generation");
        } else {
            metadata = ECOFiniteCellMetadata.acquire(drive.getCellStack(), domainId, false);
            this.leaseGeneration = metadata.leaseGeneration();
        }
        this.cellId = metadata.cellId();
        this.fingerprint = BuiltInRegistries.ITEM.getKey(drive.getCellStack().getItem()) + ":" + cellId;
        drive.setChanged();
    }

    public int index() {
        return index;
    }

    public long drivePosition() {
        return drive.getBlockPos().asLong();
    }

    public String fingerprint() {
        return fingerprint;
    }

    public UUID cellId() { return cellId; }

    public long leaseGeneration() { return leaseGeneration; }

    public ECOFiniteCellMetadata.State metadata() {
        return ECOFiniteCellMetadata.read(drive.getCellStack());
    }

    public void materialize(UUID domainId) {
        storage.materializeDeferredChanges(domainId, leaseGeneration);
        drive.setChanged();
    }

    ECOStorageCell storage() {
        return storage;
    }

    long stored(AEKey key, IActionSource source) {
        return storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
    }

    long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        return storage.insert(key, amount, mode, source);
    }

    long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        return storage.extract(key, amount, mode, source);
    }
}
