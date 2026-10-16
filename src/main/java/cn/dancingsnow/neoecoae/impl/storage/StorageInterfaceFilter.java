package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.config.IncludeExclude;
import appeng.util.ConfigInventory;
import appeng.util.prioritylist.IPartitionList;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/** A persisted exact-key allow list. An empty list accepts everything. */
public final class StorageInterfaceFilter implements Predicate<AEKey> {
    public static final int SLOTS = 27;
    private final ConfigInventory inventory = ConfigInventory.configTypes(SLOTS).build();
    private final IPartitionList partitionList;
    private final boolean configured;

    public StorageInterfaceFilter(CompoundTag tag, HolderLookup.Provider registries) {
        boolean present = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            String name = Integer.toString(slot);
            if (!tag.contains(name, Tag.TAG_COMPOUND)) continue;
            present = true;
            AEKey key = get(tag, slot, registries);
            if (key != null) inventory.setStack(slot, new GenericStack(key, 0L));
        }
        // A missing add-on's key must not silently turn an existing filter into "allow all".
        configured = present;
        var builder = IPartitionList.builder();
        builder.addAll(inventory.keySet());
        partitionList = builder.build();
    }

    @Override
    public boolean test(AEKey key) { return !configured || !partitionList.isEmpty() && partitionList.matchesFilter(key, IncludeExclude.WHITELIST); }

    public static @Nullable AEKey get(CompoundTag tag, int slot, HolderLookup.Provider registries) {
        if (slot < 0 || slot >= SLOTS || !tag.contains(Integer.toString(slot), Tag.TAG_COMPOUND)) return null;
        return decode(tag.getCompound(Integer.toString(slot)), registries);
    }

    /** Use each registered channel's codec without replacing invalid keys with AE2 missing-content items. */
    public static @Nullable AEKey decode(CompoundTag tag, HolderLookup.Provider registries) {
        Tag typeTag = tag.get(AEKey.TYPE_FIELD);
        if (typeTag == null) return null;
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        return AEKeyType.CODEC.parse(ops, typeTag).result()
            .map(type -> type.loadKeyFromTag(registries, tag)).orElse(null);
    }

    public static CompoundTag encode(AEKey key, HolderLookup.Provider registries) {
        CompoundTag tag = key.toTag(registries);
        tag.putString(AEKey.TYPE_FIELD, key.getType().getId().toString());
        return tag;
    }

    public static CompoundTag with(CompoundTag tag, int slot, @Nullable AEKey key, HolderLookup.Provider registries) {
        if (slot < 0 || slot >= SLOTS) throw new IllegalArgumentException("Invalid storage filter slot: " + slot);
        CompoundTag updated = tag.copy();
        if (key == null) updated.remove(Integer.toString(slot));
        else updated.put(Integer.toString(slot), encode(key, registries));
        return updated;
    }
}
