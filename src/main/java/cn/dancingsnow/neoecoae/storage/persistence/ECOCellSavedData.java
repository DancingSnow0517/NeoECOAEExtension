package cn.dancingsnow.neoecoae.storage.persistence;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.storage.infinite.HugeAmount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** The authoritative state of one finite disk, including its membership and transfer receipts. */
public final class ECOCellSavedData extends StorageSavedData {
    private final UUID id;
    private final StorageAmounts amounts = new StorageAmounts();
    private final List<CompoundTag> unresolved = new ArrayList<>();
    private final Map<AEKey, CompoundTag> encodedKeys = new HashMap<>();
    private CompoundTag metadata = new CompoundTag();
    private String unavailable;
    private boolean legacyMigration;
    private boolean legacy2042;
    private boolean legacy2042Pending;
    private boolean legacy2042Archived;
    private String legacy2042Failure;

    public ECOCellSavedData(UUID id) { this.id = id; }
    public UUID id() { return id; }
    public boolean legacyMigration() { return legacyMigration; }
    public boolean legacy2042() { return legacy2042; }
    public void clearLegacy2042() { if (legacy2042) { legacy2042 = false; setDirty(); } }
    boolean legacy2042Pending() { return legacy2042Pending; }
    boolean legacy2042Archived() { return legacy2042Archived; }
    void beginLegacy2042() { legacy2042 = true; legacy2042Pending = true; }
    boolean legacy2042Decodable() { return canRead() && unresolved.isEmpty(); }
    void prepareLegacy2042(CompoundTag metadata) {
        if (!legacy2042Archived) { metadata(metadata); legacy2042Archived = true; setDirty(); }
    }
    void finishLegacy2042() { legacy2042Pending = false; legacy2042Failure = null; }
    void failLegacy2042(String message) { legacy2042Failure = message; }
    public void legacyMigration(boolean value) { if (legacyMigration != value) { legacyMigration = value; setDirty(); } }
    public StorageAmounts amounts() { return amounts; }
    public CompoundTag encodedKey(AEKey key) { return encodedKeys.getOrDefault(key, key.toTagGeneric()).copy(); }
    public boolean canRead() { return unavailable == null; }
    public boolean canMutate() { return canRead() && unresolved.isEmpty() && !legacy2042Pending
        && !metadata.contains(Legacy2042Storage.RESTORE_BARRIER); }
    public String failure() { return unavailable != null ? unavailable : !unresolved.isEmpty()
        ? "Unresolved storage entries retained: " + unresolved.size() : legacy2042Pending
        ? "20.4.2 import is not durable; original data retained: " + legacy2042Failure
        : metadata.contains(Legacy2042Storage.RESTORE_BARRIER)
        ? "Interrupted 20.4.2 restore requires reconciliation; inventory retained" : writeFailure(); }
    public CompoundTag metadata() { return metadata.copy(); }
    public void metadata(CompoundTag value) {
        if (!metadata.equals(value)) { metadata = value.copy(); setDirty(); }
    }
    public void unavailable(String reason) { unavailable = reason; }
    @Override protected boolean canSaveSnapshot() { return canRead() && (!legacy2042Pending || legacy2042Archived); }

    public static ECOCellSavedData load(CompoundTag tag, UUID id) {
        ECOCellSavedData data = new ECOCellSavedData(id);
        if (Legacy2042Storage.isCell(tag)) {
            try { tag = Legacy2042Storage.cell(tag, id); data.legacy2042Pending = true; }
            catch (RuntimeException invalid) { data.unavailable("Invalid 20.4.2 cell; original retained: " + invalid); return data; }
        }
        data.loadPersistence(tag);
        data.legacy2042 = tag.getBoolean(Legacy2042Storage.ORIGIN);
        data.legacyMigration = tag.getBoolean("legacyMigration");
        if (tag.getInt("format") < 1 || tag.getInt("format") > 2 || !tag.hasUUID("id") || !id.equals(tag.getUUID("id"))
            || !tag.contains("entries", Tag.TAG_LIST)) {
            data.unavailable("Invalid ECO disk format or identity; original file retained");
            return data;
        }
        ListTag entries = (ListTag) tag.get("entries");
        if (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            data.unavailable("Malformed ECO disk inventory; original file retained"); return data;
        }
        data.metadata = tag.getCompound("metadata").copy();
        for (Tag raw : entries) {
            CompoundTag entry = (CompoundTag) raw;
            try {
                HugeAmount amount;
                if (entry.contains("amount", Tag.TAG_LONG)) amount = HugeAmount.of(entry.getLong("amount"));
                else {
                    CompoundTag encodedAmount = entry.getCompound("amount");
                    if (!encodedAmount.contains("long", Tag.TAG_LONG) && !encodedAmount.contains("big", Tag.TAG_STRING))
                        throw new IllegalArgumentException("Invalid exact disk amount");
                    amount = HugeAmount.read(encodedAmount);
                }
                if (amount.isZero()) throw new IllegalArgumentException("Stored amounts must be positive");
                AEKey key = AEKey.fromTagGeneric(entry.getCompound("key"));
                if (!Legacy2042Storage.resolved(key) || data.amounts.visible(key) != 0) throw new IllegalArgumentException("Unknown or duplicate key");
                data.amounts.set(key, amount);
                data.encodedKeys.put(key, entry.getCompound("key").copy());
            } catch (RuntimeException invalid) { data.unresolved.add(entry.copy()); }
        }
        return data;
    }
    public void importLegacy(List<GenericStack> contents) {
        if (!amounts.isEmpty()) throw new IllegalStateException("Import requires an empty disk");
        for (GenericStack stack : contents) {
            amounts.add(stack.what(), stack.amount());
        }
        setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag) {
        if (!canRead()) throw new IllegalStateException(unavailable);
        tag.putInt("format", 2);
        tag.putUUID("id", id);
        if (legacy2042) tag.putBoolean(Legacy2042Storage.ORIGIN, true);
        if (legacyMigration) tag.putBoolean("legacyMigration", true);
        ListTag entries = new ListTag();
        encodedKeys.keySet().removeIf(key -> amounts.visible(key) == 0);
        amounts.forEach((key, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("key", encodedKeys.computeIfAbsent(key, AEKey::toTagGeneric).copy());
            entry.put("amount", amount.write());
            entries.add(entry);
        });
        unresolved.forEach(entry -> entries.add(entry.copy()));
        tag.put("entries", entries);
        tag.put("metadata", metadata.copy());
        return tag;
    }
    /** A bounded preview; serializing a tooltip never walks the entire disk. */
    public ListTag preview() {
        ListTag result = new ListTag();
        var iterator = amounts.visibleAmounts().object2LongEntrySet().iterator();
        while (iterator.hasNext() && result.size() < 5) {
            var entry = iterator.next();
            result.add(GenericStack.writeTag(new GenericStack(entry.getKey(), entry.getLongValue())));
        }
        return result;
    }
}
