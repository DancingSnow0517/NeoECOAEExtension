package cn.dancingsnow.neoecoae.platform.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Keeps an unavailable optional disk's registry name and complete NBT across ordinary world saves. */
public final class DriveCellPersistence {
    private DriveCellPersistence() {}

    public static CompoundTag unresolved(CompoundTag data, ItemStack decoded) {
        CompoundTag previous = data.getCompound("unresolvedCellStack");
        if (!previous.isEmpty()) return previous.copy();
        CompoundTag original = data.getCompound("cellStack");
        String id = original.getString("id");
        if ((decoded == null || decoded.isEmpty()) && !id.isEmpty() && !id.equals("minecraft:air")) {
            return original.copy();
        }
        return new CompoundTag();
    }
}
