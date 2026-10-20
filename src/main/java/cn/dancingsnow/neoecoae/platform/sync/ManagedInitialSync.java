package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.networking.s2c.SPacketManagedPayload;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoSyncBlockEntity;
import net.minecraft.nbt.CompoundTag;
import java.util.BitSet;

/** Initial chunk data must not consume dirty changes still owed to players already tracking the block. */
public final class ManagedInitialSync {
    private ManagedInitialSync() {}

    public static CompoundTag write(IAutoSyncBlockEntity host) {
        var fields = host.getRootStorage().getSyncFields();
        var dirty = new BitSet(fields.length);
        for (int index = 0; index < fields.length; index++) {
            if (fields[index].isSyncDirty()) dirty.set(index);
        }
        try {
            return SPacketManagedPayload.of(host, true).serializeNBT();
        } finally {
            for (int index = dirty.nextSetBit(0); index >= 0; index = dirty.nextSetBit(index + 1)) {
                fields[index].markAsDirty();
            }
        }
    }
}
