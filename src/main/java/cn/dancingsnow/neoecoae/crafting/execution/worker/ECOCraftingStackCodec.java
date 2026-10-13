package cn.dancingsnow.neoecoae.crafting.execution.worker;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Converts crafting recovery counters to the persistable generic-stack representation. */
public final class ECOCraftingStackCodec {
    private ECOCraftingStackCodec() {}

    public static KeyCounter collect(List<ItemStack> stacks) {
        KeyCounter result = new KeyCounter();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) result.add(AEItemKey.of(stack), stack.getCount());
        }
        return result;
    }

    public static List<GenericStack> toGenericStacks(KeyCounter counter, boolean allowFluid) {
        List<GenericStack> stacks = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            if (entry.getLongValue() <= 0) continue;
            if (!(entry.getKey() instanceof AEItemKey)
                && !(allowFluid && entry.getKey() instanceof AEFluidKey)) return List.of();
            stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return stacks;
    }

    public static boolean canRetain(List<GenericStack> stacks, boolean allowFluid) {
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0
                || (!(stack.what() instanceof AEItemKey)
                    && !(allowFluid && stack.what() instanceof AEFluidKey))) return false;
        }
        return true;
    }

    public static boolean isEmpty(KeyCounter counter) {
        return !counter.iterator().hasNext();
    }

    static void copyStacks(List<ItemStack> source, List<ItemStack> target) {
        for (ItemStack stack : source) {
            if (!stack.isEmpty()) {
                target.add(stack.copy());
            }
        }
    }

    static List<ItemStack> copyStacks(List<ItemStack> source) {
        List<ItemStack> copy = new ArrayList<>();
        copyStacks(source, copy);
        return copy;
    }

    /** Adds one item stack, following AE2's generic-stack unwrapping; empty or unkeyable stacks are ignored. */
    static void addStack(KeyCounter counter, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            GenericStack genericStack = GenericStack.fromItemStack(stack);
            if (genericStack != null && genericStack.amount() > 0L) {
                counter.add(genericStack.what(), genericStack.amount());
            }
        }
    }

    static void addGenericStacks(KeyCounter counter, List<GenericStack> stacks) {
        for (GenericStack stack : stacks) {
            if (stack != null && stack.amount() > 0) {
                counter.add(stack.what(), stack.amount());
            }
        }
    }
}
