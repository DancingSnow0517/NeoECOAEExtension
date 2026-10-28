package cn.dancingsnow.neoecoae.storage;

import appeng.api.stacks.AEKey;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/** A persisted exact-key allow list. An empty list accepts everything. */
public final class StorageInterfaceFilter implements Predicate<AEKey> {
    public static final int SLOTS = 27;
    private final Set<AEKey> keys = new HashSet<>();
    private final boolean configured;

    public StorageInterfaceFilter(CompoundTag tag) {
        boolean present = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            String name = Integer.toString(slot);
            if (!tag.contains(name, Tag.TAG_COMPOUND)) continue;
            present = true;
            AEKey key = get(tag, slot);
            if (key != null) keys.add(key);
        }
        // A missing add-on's key must not silently turn an existing filter into "allow all".
        configured = present;
    }

    @Override
    public boolean test(AEKey key) { return !configured || keys.contains(key); }

    public static @Nullable AEKey get(CompoundTag tag, int slot) {
        if (slot < 0 || slot >= SLOTS || !tag.contains(Integer.toString(slot), Tag.TAG_COMPOUND)) return null;
        return decode(tag.getCompound(Integer.toString(slot)));
    }

    /** Use each registered channel's codec without replacing invalid keys with AE2 missing-content items. */
    public static @Nullable AEKey decode(CompoundTag tag) {
        try {
            return AEKey.fromTagGeneric(tag);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    public static CompoundTag encode(AEKey key) {
        return key.toTagGeneric();
    }

    public static CompoundTag with(CompoundTag tag, int slot, @Nullable AEKey key) {
        if (slot < 0 || slot >= SLOTS) throw new IllegalArgumentException("Invalid storage filter slot: " + slot);
        CompoundTag updated = tag.copy();
        if (key == null) updated.remove(Integer.toString(slot));
        else updated.put(Integer.toString(slot), encode(key));
        return updated;
    }
}
