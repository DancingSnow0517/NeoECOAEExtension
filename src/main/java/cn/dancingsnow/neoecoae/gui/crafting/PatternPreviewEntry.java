package cn.dancingsnow.neoecoae.gui.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Snapshot data only; never used as the authoritative inventory for an operation. */
public record PatternPreviewEntry(long busPosition, int physicalSlot, ItemStack stack,
                                  String keywords, byte flags) {
    public CompoundTag encode(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("bus", busPosition);
        tag.putInt("slot", physicalSlot);
        tag.put("stack", stack.saveOptional(registries));
        tag.putString("keywords", keywords);
        tag.putByte("flags", flags);
        return tag;
    }

    public static PatternPreviewEntry decode(CompoundTag tag, HolderLookup.Provider registries) {
        return new PatternPreviewEntry(tag.getLong("bus"), tag.getInt("slot"),
                ItemStack.parseOptional(registries, tag.getCompound("stack")),
                tag.getString("keywords"), tag.getByte("flags"));
    }
}
