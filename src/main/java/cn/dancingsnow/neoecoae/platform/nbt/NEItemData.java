package cn.dancingsnow.neoecoae.platform.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;

/** 1.20 item tags. Read access never creates tags; updates copy before replacing the root. */
public final class NEItemData {
    private NEItemData() {}

    /** Treat the returned tag as read-only. Use copy or update for changes. */
    public static CompoundTag read(ItemStack stack) {
        return stack.getTag() == null ? new CompoundTag() : stack.getTag();
    }

    public static CompoundTag copy(ItemStack stack) { return read(stack).copy(); }

    public static void write(ItemStack stack, CompoundTag tag) {
        stack.setTag(tag.isEmpty() ? null : tag.copy());
    }

    public static void update(ItemStack stack, Consumer<CompoundTag> change) {
        CompoundTag tag = copy(stack);
        change.accept(tag);
        write(stack, tag);
    }

    public static void remove(ItemStack stack, String key) { update(stack, tag -> tag.remove(key)); }
}
