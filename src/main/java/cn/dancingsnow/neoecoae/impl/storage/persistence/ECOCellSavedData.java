package cn.dancingsnow.neoecoae.impl.storage.persistence;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
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

    public ECOCellSavedData(UUID id) { this.id = id; }
    public UUID id() { return id; }
    public boolean legacyMigration() { return legacyMigration; }
    public void legacyMigration(boolean value) { if (legacyMigration != value) { legacyMigration = value; setDirty(); } }
    public StorageAmounts amounts() { return amounts; }
    public boolean canRead() { return unavailable == null; }
    public boolean canMutate() { return canRead() && unresolved.isEmpty(); }
    public String failure() { return unavailable != null ? unavailable : !unresolved.isEmpty()
        ? "Unresolved storage entries retained: " + unresolved.size() : writeFailure(); }
    public CompoundTag metadata() { return metadata.copy(); }
    public void metadata(CompoundTag value) {
        if (!metadata.equals(value)) { metadata = value.copy(); setDirty(); }
    }
    public void unavailable(String reason) { unavailable = reason; }
    @Override protected boolean canSaveSnapshot() { return canRead(); }

    public static ECOCellSavedData load(CompoundTag tag, UUID id) {
        ECOCellSavedData data = new ECOCellSavedData(id);
        data.loadPersistence(tag);
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
                if (key == null || data.amounts.visible(key) != 0) throw new IllegalArgumentException("Unknown or duplicate key");
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
