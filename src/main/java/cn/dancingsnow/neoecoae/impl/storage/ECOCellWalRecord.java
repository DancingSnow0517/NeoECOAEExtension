package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEKey;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * One cell mutation in the shared cell WAL.
 *
 * <p>{@code revision} is the revision the owning cell assigned to <em>this</em> mutation, never the cell's current
 * revision at the time the record is drained. Recovery compares it against the revision stamped into {@code cell.dat},
 * so a record that a checkpoint already contains must not appear to be newer than that checkpoint.
 */
record ECOCellWalRecord(UUID cell, AEKey key, long delta, long revision) {
    CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("cell", cell);
        tag.put("key", key.toTagGeneric());
        tag.putLong("delta", delta);
        tag.putLong("revision", revision);
        return tag;
    }

    @Nullable static ECOCellWalRecord read(CompoundTag tag) {
        if (!tag.hasUUID("cell")) {
            return null;
        }
        AEKey key = AEKey.fromTagGeneric(tag.getCompound("key"));
        if (key == null) {
            return null;
        }
        return new ECOCellWalRecord(tag.getUUID("cell"), key, tag.getLong("delta"), tag.getLong("revision"));
    }
}
