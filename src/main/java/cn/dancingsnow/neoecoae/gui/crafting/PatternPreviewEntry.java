package cn.dancingsnow.neoecoae.gui.crafting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Snapshot data only; never used as the authoritative inventory for an operation. */
public record PatternPreviewEntry(long busPosition, int physicalSlot, ItemStack stack,
                                  String inputKeywords, String outputKeywords,
                                  List<PatternPreviewEntry.DiskPattern> diskPatterns,
                                  boolean auxiliaryDisk) {

    /**
     * One recipe held inside the disk standing in this slot.
     *
     * <p>Carried as its own list rather than as further entries because a row here is a physical slot: the
     * terminal's index, its dirty bookkeeping and the actions it sends all speak in slot numbers, so a disk's
     * recipes have no slot of their own to be addressed by. The list rides along with the slot and is expanded
     * for display only.</p>
     */
    public record DiskPattern(ItemStack stack, String inputKeywords, String outputKeywords) {
        public CompoundTag encode(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.put("stack", stack.saveOptional(registries));
            if (!inputKeywords.isEmpty()) tag.putString("inputs", inputKeywords);
            if (!outputKeywords.isEmpty()) tag.putString("output", outputKeywords);
            return tag;
        }

        public static DiskPattern decode(CompoundTag tag, HolderLookup.Provider registries) {
            return new DiskPattern(ItemStack.parseOptional(registries, tag.getCompound("stack")),
                    tag.getString("inputs"), tag.getString("output"));
        }
    }

    public CompoundTag encode(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("bus", busPosition);
        tag.putInt("slot", physicalSlot);
        tag.put("stack", stack.saveOptional(registries));
        if (!inputKeywords.isEmpty()) tag.putString("inputs", inputKeywords);
        if (!outputKeywords.isEmpty()) tag.putString("output", outputKeywords);
        ListTag held = new ListTag();
        for (DiskPattern pattern : diskPatterns) {
            held.add(pattern.encode(registries));
        }
        tag.put("disk", held);
        // Carried separately from the recipe list because an empty disk has no recipes yet still is not a slot a
        // pattern can be placed into - the two are different questions and the actions have to ask the right one.
        tag.putBoolean("diskSlot", auxiliaryDisk);
        return tag;
    }

    public static PatternPreviewEntry decode(CompoundTag tag, HolderLookup.Provider registries) {
        List<DiskPattern> held = new ArrayList<>();
        ListTag encoded = tag.getList("disk", Tag.TAG_COMPOUND);
        for (int index = 0; index < encoded.size(); index++) {
            CompoundTag entry = encoded.getCompound(index);
            held.add(DiskPattern.decode(entry, registries));
        }
        return new PatternPreviewEntry(tag.getLong("bus"), tag.getInt("slot"),
                ItemStack.parseOptional(registries, tag.getCompound("stack")),
                tag.getString("inputs"), tag.getString("output"), held, tag.getBoolean("diskSlot"));
    }
}
