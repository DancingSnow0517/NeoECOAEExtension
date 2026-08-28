package cn.dancingsnow.neoecoae.api;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** A decoded, validated pattern that can be routed to several destinations without decoding again. */
public record ECOPreparedPattern(ItemStack stack, IPatternDetails details, @Nullable AEItemKey key) {
    public ECOPreparedPattern {
        stack = stack == null ? ItemStack.EMPTY : stack.copy();
        Objects.requireNonNull(details, "details");
    }

    public boolean matches(ItemStack candidate) {
        if (candidate == null || candidate.isEmpty() || stack.isEmpty()) {
            return false;
        }
        if (key != null) {
            return key.equals(AEItemKey.of(candidate));
        }
        return ItemStack.isSameItemSameComponents(stack, candidate);
    }
}
