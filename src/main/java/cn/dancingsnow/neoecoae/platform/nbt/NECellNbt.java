package cn.dancingsnow.neoecoae.platform.nbt;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.GenericStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** ECO-owned 1.20 storage format; does not overwrite vanilla or another cell provider's tags. */
public final class NECellNbt {
    public static final String CONTENTS = "neoecoae_cell_contents";
    private static final String FUZZY = "neoecoae_fuzzy_mode";
    private NECellNbt() {}

    public static List<GenericStack> readStacks(ItemStack cell) {
        CompoundTag root = NEItemData.read(cell);
        if (!root.contains(CONTENTS)) return List.of();
        if (!root.contains(CONTENTS, Tag.TAG_COMPOUND)) throw new IllegalStateException("Malformed ECO cell contents");
        CompoundTag data = root.getCompound(CONTENTS);
        if (data.getInt("format") != 1 || !data.contains("stacks", Tag.TAG_LIST)) {
            throw new IllegalStateException("Unsupported ECO cell format; contents preserved");
        }
        ListTag entries = (ListTag) data.get("stacks");
        if (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalStateException("Malformed ECO cell stacks; contents preserved");
        }
        List<GenericStack> stacks = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            GenericStack stack = GenericStack.readTag(entries.getCompound(i));
            if (stack == null || stack.amount() <= 0) {
                throw new IllegalStateException("Unknown or invalid ECO storage key at " + i + "; contents preserved");
            }
            stacks.add(stack);
        }
        return List.copyOf(stacks);
    }

    public static void writeStacks(ItemStack cell, List<GenericStack> stacks) {
        if (stacks.isEmpty()) { clear(cell); return; }
        ListTag entries = new ListTag();
        for (var stack : stacks) {
            if (stack == null || stack.amount() <= 0) throw new IllegalArgumentException("Stored amounts must be positive");
            entries.add(GenericStack.writeTag(stack));
        }
        CompoundTag data = new CompoundTag();
        data.putInt("format", 1);
        data.put("stacks", entries);
        NEItemData.update(cell, root -> root.put(CONTENTS, data));
    }

    public static void clear(ItemStack cell) { NEItemData.remove(cell, CONTENTS); }

    public static FuzzyMode fuzzyMode(ItemStack cell) {
        String value = NEItemData.read(cell).getString(FUZZY);
        if (value.isEmpty()) return FuzzyMode.IGNORE_ALL;
        try { return FuzzyMode.valueOf(value); }
        catch (IllegalArgumentException unknown) { return FuzzyMode.IGNORE_ALL; }
    }

    public static void fuzzyMode(ItemStack cell, FuzzyMode mode) {
        NEItemData.update(cell, root -> root.putString(FUZZY, mode.name()));
    }
}
