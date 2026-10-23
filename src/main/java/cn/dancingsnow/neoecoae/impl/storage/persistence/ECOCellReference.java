package cn.dancingsnow.neoecoae.impl.storage.persistence;

import cn.dancingsnow.neoecoae.platform.nbt.NECellNbt;
import cn.dancingsnow.neoecoae.platform.nbt.NEItemData;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Small item-owned reference and display cache; it never contains the authoritative inventory. */
public final class ECOCellReference {
    public static final String TAG = "neoecoae_storage_ref";
    public static final List<String> METADATA_KEYS = List.of("neoecoae_infinite_member", "neoecoae_infinite_domain",
        "neoecoae_migration_id", "neoecoae_migration_domain", "neoecoae_member_identity", "neoecoae_restore_receipts",
        Legacy2042Storage.RESTORE_BARRIER);
    private ECOCellReference() {}
    public static UUID id(ItemStack stack) {
        CompoundTag root = NEItemData.read(stack), ref = root.getCompound(TAG);
        if (root.contains(TAG)) return ref.hasUUID("id") ? ref.getUUID("id") : null;
        return root.hasUUID(Legacy2042Storage.CELL_ID) ? root.getUUID(Legacy2042Storage.CELL_ID) : null;
    }
    public static CompoundTag metadata(ItemStack stack) {
        return Legacy2042Storage.metadata(NEItemData.read(stack));
    }
    public static void applyMetadata(ItemStack stack, CompoundTag metadata) {
        if (metadata(stack).equals(metadata)) return;
        NEItemData.update(stack, root -> {
            for (String key : METADATA_KEYS) {
                if (metadata.contains(key)) root.put(key, metadata.get(key).copy()); else root.remove(key);
            }
        });
    }
    public static void update(ItemStack stack, ECOCellSavedData data) {
        if (!data.canRead()) {
            if (!NEItemData.read(stack).contains(TAG)) return;
            NEItemData.update(stack, root -> {
                CompoundTag ref = root.getCompound(TAG).copy();
                ref.putString("failure", data.failure()); root.put(TAG, ref);
            });
            return;
        }
        CompoundTag ref = new CompoundTag();
        ref.putInt("format", 1); ref.putUUID("id", data.id());
        ref.putLong("revision", data.contentRevision());
        ref.putInt("types", data.amounts().size()); ref.putLong("amount", data.amounts().totalVisible());
        if (data.amounts().totalVisible() == Long.MAX_VALUE)
            ref.putString("exactAmount", data.amounts().totalExact().toString());
        ref.put("preview", data.preview());
        if (data.failure() != null) ref.putString("failure", data.failure());
        NEItemData.update(stack, root -> {
            root.put(TAG, ref);
            root.remove(NECellNbt.CONTENTS);
            root.remove(Legacy2042Storage.CONTENTS);
        });
    }
}
