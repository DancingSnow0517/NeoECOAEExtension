package cn.dancingsnow.neoecoae.platform.sync;

import com.lowdragmc.lowdraglib.networking.s2c.SPacketManagedPayload;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoSyncBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import net.minecraft.nbt.CompoundTag;
import java.util.BitSet;

/** A full snapshot must neither consume pending changes nor trigger persistence callbacks during chunk broadcasts. */
public final class ManagedInitialSync {
    private ManagedInitialSync() {}

    public static CompoundTag write(IAutoSyncBlockEntity host) {
        var fields = host.getRootStorage().getSyncFields();
        var included = new BitSet(fields.length);
        var payloads = new ITypedPayload<?>[fields.length];
        for (int index = 0; index < fields.length; index++) {
            included.set(index);
            payloads[index] = fields[index].getKey().readSyncedField(fields[index], true);
        }
        var extra = new CompoundTag();
        host.writeCustomSyncData(extra);
        return new SPacketManagedPayload(host.getBlockEntityType(), host.getCurrentPos(), included, payloads, extra)
            .serializeNBT();
    }
}
