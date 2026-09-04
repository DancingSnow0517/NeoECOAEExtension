package cn.dancingsnow.neoecoae.impl.storage.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ECOStorageShard {
    private final int index;
    private final ECODriveBlockEntity drive;
    private final ECOStorageCell storage;
    private final String fingerprint;

    ECOStorageShard(int index, ECODriveBlockEntity drive, ECOStorageCell storage) {
        this.index = index;
        this.drive = drive;
        this.storage = storage;
        this.fingerprint = BuiltInRegistries.ITEM.getKey(drive.getCellStack().getItem()).toString();
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
