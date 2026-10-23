package cn.dancingsnow.neoecoae.platform.nbt;

import cn.dancingsnow.neoecoae.impl.storage.persistence.Legacy2042Storage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Validates a complete old inventory before publishing any slot; unknown items remain in the caller's raw escrow. */
public final class Legacy2042Inventory {
    private Legacy2042Inventory() {}

    public static List<ItemStack> read(CompoundTag tag, String key, int slots, int limit) {
        List<ItemStack> result = new ArrayList<>(java.util.Collections.nCopies(slots, ItemStack.EMPTY));
        var seen = new HashSet<Integer>();
        for (Tag raw : Legacy2042Storage.compounds(tag, key)) {
            CompoundTag entry = (CompoundTag) raw;
            int slot = entry.getInt("Slot");
            if (!entry.contains("Slot", Tag.TAG_ANY_NUMERIC) || slot < 0 || slot >= slots || !seen.add(slot))
                throw new IllegalArgumentException("Invalid, duplicate or out-of-range 20.4.2 inventory slot");
            ItemStack item = ItemStack.of(entry);
            if (item.isEmpty() || item.getCount() > limit)
                throw new IllegalArgumentException("Unknown or invalid 20.4.2 item at slot " + slot);
            result.set(slot, item);
        }
        return List.copyOf(result);
    }

    public static int pages(CompoundTag tag, List<ItemStack> inventory, int slotsPerPage, int maximumPages) {
        if (tag.contains("patternInventoryPages") && !tag.contains("patternInventoryPages", Tag.TAG_INT))
            throw new IllegalArgumentException("Invalid 20.4.2 pattern page count");
        int pages = tag.contains("patternInventoryPages") ? tag.getInt("patternInventoryPages") : 1;
        if (pages < 1 || pages > maximumPages) throw new IllegalArgumentException("20.4.2 pattern pages exceed supported capacity");
        for (int slot = inventory.size() - 1; slot >= 0; slot--) if (!inventory.get(slot).isEmpty())
            return Math.max(pages, slot / slotsPerPage + 1);
        return pages;
    }
}
