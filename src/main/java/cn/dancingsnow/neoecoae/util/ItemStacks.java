package cn.dancingsnow.neoecoae.util;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public final class ItemStacks {
    private ItemStacks() {}

    public static void merge(List<ItemStack> stacks, ItemStack toAdd) {
        if (toAdd.isEmpty()) {
            return;
        }
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameTags(stack, toAdd)) {
                stack.grow(toAdd.getCount());
                return;
            }
        }
        stacks.add(toAdd.copy());
    }
}
