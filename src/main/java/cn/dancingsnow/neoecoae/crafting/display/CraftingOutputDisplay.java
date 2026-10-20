package cn.dancingsnow.neoecoae.crafting.display;

import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;

/** A display icon and its actual output quantity, independent of ItemStack's int count. */
public record CraftingOutputDisplay(ItemStack item, BigInteger amount) {
    public static final CraftingOutputDisplay EMPTY = new CraftingOutputDisplay(ItemStack.EMPTY, BigInteger.ZERO);

    public CraftingOutputDisplay {
        if (item.isEmpty() || amount.signum() <= 0) {
            item = ItemStack.EMPTY;
            amount = BigInteger.ZERO;
        } else {
            item = item.copyWithCount(1);
        }
    }
}
