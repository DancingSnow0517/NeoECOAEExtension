package cn.dancingsnow.neoecoae.multiblock.placement;

import net.minecraft.world.item.ItemStack;

public record RequiredItem(ItemStack stack, int count) {
    public RequiredItem {
        stack = stack.copyWithCount(1);
        count = Math.max(0, count);
    }

    @Override
    public ItemStack stack() {
        return stack.copy();
    }

    public boolean isEmpty() {
        return stack.isEmpty() || count <= 0;
    }

    public RequiredItem grow(int amount) {
        return new RequiredItem(stack, count + amount);
    }
}
