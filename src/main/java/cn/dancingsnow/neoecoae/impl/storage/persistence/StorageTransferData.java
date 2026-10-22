package cn.dancingsnow.neoecoae.impl.storage.persistence;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** One reusable transaction record per source disk, containing a replayable, sealed source snapshot. */
public final class StorageTransferData extends StorageSavedData {
    private final UUID source;
    private UUID domain;
    private UUID transaction;
    private CompoundTag sourceSnapshot = new CompoundTag();
    private boolean completed;
    private String failure;

    public StorageTransferData(UUID source) { this.source = source; }
    public UUID source() { return source; }
    public UUID domain() { return domain; }
    public UUID transaction() { return transaction; }
    public boolean completed() { return completed; }
    public boolean readable() { return failure == null; }
    public CompoundTag sourceSnapshot() { return sourceSnapshot.copy(); }
    public void prepare(UUID transaction, UUID domain, ECOCellSavedData source) {
        prepare(transaction, domain, source, java.util.Map.of());
    }
    public void prepare(UUID transaction, UUID domain, ECOCellSavedData source,
            java.util.Map<appeng.api.stacks.AEKey, java.util.Set<UUID>> compatibility) {
        if (this.transaction != null && !completed) throw new IllegalStateException("Disk already has a pending transfer");
        this.transaction = transaction; this.domain = domain; this.sourceSnapshot = source.snapshot();
        if (!compatibility.isEmpty()) for (var raw : sourceSnapshot.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            var ids = compatibility.get(appeng.api.stacks.AEKey.fromTagGeneric(entry.getCompound("key")));
            if (ids != null) {
                var list = new net.minecraft.nbt.ListTag(); ids.forEach(id -> list.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
                entry.put("compatibilityReceipts", list);
            }
        }
        completed = false; setDirty();
    }
    public void complete() {
        if (!completed || !sourceSnapshot.isEmpty()) {
            completed = true;
            // Both participant snapshots are durable. Keep the receipt, release the replay payload.
            sourceSnapshot = new CompoundTag();
            setDirty();
        }
    }
    public java.util.Map<appeng.api.stacks.AEKey, java.util.Set<UUID>> compatibilityReceipts() {
        var result = new java.util.HashMap<appeng.api.stacks.AEKey, java.util.Set<UUID>>();
        for (var raw : sourceSnapshot.getList("entries", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            if (!entry.contains("compatibilityReceipts")) continue;
            var ids = new java.util.HashSet<UUID>();
            if (!entry.contains("compatibilityReceipts", Tag.TAG_LIST)) throw new IllegalStateException("Invalid legacy receipt list");
            var rawIds = (net.minecraft.nbt.ListTag) entry.get("compatibilityReceipts");
            if (rawIds.isEmpty() || rawIds.getElementType() != Tag.TAG_INT_ARRAY)
                throw new IllegalStateException("Invalid legacy receipt identities");
            var list = entry.getList("compatibilityReceipts", Tag.TAG_INT_ARRAY);
            for (var id : list) ids.add(net.minecraft.nbt.NbtUtils.loadUUID(id));
            var key = appeng.api.stacks.AEKey.fromTagGeneric(entry.getCompound("key"));
            if (key == null) throw new IllegalStateException("Unresolved compatibility receipt key");
            result.put(key, java.util.Set.copyOf(ids));
        }
        return result;
    }
    public void unavailable(String reason) { failure = reason; }
    @Override protected boolean canSaveSnapshot() { return readable() && transaction != null; }
    @Override public CompoundTag save(CompoundTag tag) {
        if (!canSaveSnapshot()) throw new IllegalStateException("Unavailable storage transfer: " + failure);
        tag.putInt("format", 1); tag.putUUID("source", source); tag.putUUID("domain", domain);
        tag.putUUID("transaction", transaction); tag.putBoolean("completed", completed);
        tag.put("snapshot", sourceSnapshot.copy());
        return tag;
    }
    public static StorageTransferData load(CompoundTag tag, UUID source) {
        StorageTransferData data = new StorageTransferData(source);
        data.loadPersistence(tag);
        if (tag.getInt("format") != 1 || !tag.hasUUID("source") || !source.equals(tag.getUUID("source"))
            || !tag.hasUUID("domain") || !tag.hasUUID("transaction") || !tag.contains("snapshot", Tag.TAG_COMPOUND)) {
            data.unavailable("Invalid storage transfer identity or format"); return data;
        }
        data.domain = tag.getUUID("domain"); data.transaction = tag.getUUID("transaction");
        data.completed = tag.getBoolean("completed"); data.sourceSnapshot = tag.getCompound("snapshot").copy();
        if (data.completed && !data.sourceSnapshot.isEmpty()) data.complete();
        return data;
    }
}
