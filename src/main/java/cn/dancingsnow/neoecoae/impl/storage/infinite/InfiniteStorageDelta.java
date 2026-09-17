package cn.dancingsnow.neoecoae.impl.storage.infinite;

import cn.dancingsnow.neoecoae.impl.storage.ECOStorageKeyHash;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** A cumulative, atomically replaced NBT overlay, never an append-only per-operation journal. */
final class InfiniteStorageDelta {
    static final String BASE_REVISION = "base_revision";
    static final String DELETED = "deleted";

    private InfiniteStorageDelta() {}

    static Path path(Path base) {
        return base.resolveSibling(base.getFileName() + ".delta.dat");
    }

    static CompoundTag apply(CompoundTag base, CompoundTag delta) throws IOException {
        if (!delta.hasUUID("domain")
                || !base.hasUUID("domain")
                || !base.getUUID("domain").equals(delta.getUUID("domain"))
                || !delta.contains(BASE_REVISION, Tag.TAG_LONG)
                || !delta.contains("revision", Tag.TAG_LONG)) {
            throw new IOException("Invalid infinite-storage delta identity");
        }
        long baseRevision = base.getLong("revision");
        // A crash after installing a compacted base may leave its older overlay in place.
        if (delta.getLong("revision") <= baseRevision) return base;
        if (delta.getLong(BASE_REVISION) != baseRevision) {
            throw new IOException("Infinite-storage delta does not match its base revision");
        }
        if (!(delta.get("entries") instanceof ListTag changes)
                || !changes.isEmpty() && changes.getElementType() != Tag.TAG_COMPOUND) {
            throw new IOException("Invalid infinite-storage delta entries");
        }
        Set<String> replaced = new HashSet<>();
        for (int i = 0; i < changes.size(); i++) {
            String identity = identity(changes.getCompound(i));
            if (identity.isEmpty()) {
                // Without an identity we cannot know which old quantity must be hidden.
                throw new IOException("Infinite-storage delta record has no recoverable identity");
            }
            replaced.add(identity);
        }
        ListTag entries = new ListTag();
        ListTag originals = base.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < originals.size(); i++) {
            CompoundTag entry = originals.getCompound(i);
            if (!replaced.contains(identity(entry))) entries.add(entry);
        }
        for (int i = 0; i < changes.size(); i++) {
            CompoundTag entry = changes.getCompound(i);
            if (!entry.getBoolean(DELETED)) entries.add(entry);
        }
        CompoundTag result = delta.copy();
        result.remove(BASE_REVISION);
        result.put("entries", entries);
        return result;
    }

    private static String identity(CompoundTag entry) {
        return entry.contains("key", Tag.TAG_COMPOUND)
                ? ECOStorageKeyHash.stableFingerprint(entry.getCompound("key"))
                : entry.getString("isolated_key_fingerprint");
    }
}
