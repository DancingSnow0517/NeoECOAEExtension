package cn.dancingsnow.neoecoae.storage.bulk;

import appeng.api.stacks.AEItemKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Stable NBT-aware keys, also used to read historical ECO MEGA bulk data. */
public final class BulkItemKeys {
    private static final String ITEM = "item";
    private static final String TAG = "tag";
    private BulkItemKeys() {}
    public static CompoundTag write(AEItemKey key) {
        // Same layout as the 1.20.1 MEGA bulk cell, so its saved keys stay readable.
        CompoundTag tag = key.toTag();
        tag.putString(ITEM, key.getId().toString());
        tag.remove("id");
        return tag;
    }
    public static @Nullable AEItemKey read(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(ITEM));
        var item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) return null;
        ItemStack restored = new ItemStack(item);
        if (tag.contains(TAG, Tag.TAG_COMPOUND)) restored.setTag(tag.getCompound(TAG).copy());
        return AEItemKey.of(restored);
    }
}
