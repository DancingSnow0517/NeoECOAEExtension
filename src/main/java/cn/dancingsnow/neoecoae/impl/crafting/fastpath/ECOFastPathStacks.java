package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ECOFastPathStacks {
    private static final int MAX_SAFE_ITEM_STACK_COUNT = 99;

    private ECOFastPathStacks() {
    }

    public static List<GenericStack> copyCounter(KeyCounter counter) {
        KeyCounter copy = new KeyCounter();
        if (counter != null) {
            copy.addAll(counter);
        }
        return copySorted(copy);
    }

    public static List<GenericStack> copyCounters(KeyCounter[] counters) {
        KeyCounter copy = new KeyCounter();
        if (counters != null) {
            for (KeyCounter counter : counters) {
                if (counter != null) {
                    copy.addAll(counter);
                }
            }
        }
        return copySorted(copy);
    }

    public static Optional<List<GenericStack>> fromItemStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        GenericStack genericStack = GenericStack.fromItemStack(stack);
        if (genericStack == null || genericStack.amount() <= 0) {
            return Optional.empty();
        }
        return Optional.of(List.of(genericStack));
    }

    public static Optional<List<GenericStack>> fromItemStacks(List<ItemStack> stacks) {
        KeyCounter counter = new KeyCounter();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            GenericStack genericStack = GenericStack.fromItemStack(stack);
            if (genericStack == null || genericStack.amount() <= 0) {
                return Optional.empty();
            }
            counter.add(genericStack.what(), genericStack.amount());
        }
        return Optional.of(copySorted(counter));
    }

    public static Optional<ItemStack> toSingleItemStack(List<GenericStack> stacks) {
        if (stacks.size() != 1) {
            return Optional.empty();
        }
        return toItemStack(stacks.get(0));
    }

    public static Optional<List<ItemStack>> toItemStacks(List<GenericStack> stacks) {
        List<ItemStack> result = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            if (!appendItemStacks(stack, result)) {
                return Optional.empty();
            }
        }
        return Optional.of(List.copyOf(result));
    }

    public static boolean isSafeForFastPath(List<GenericStack> stacks, boolean input) {
        for (GenericStack stack : stacks) {
            if (!isSafeForFastPath(stack, input)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSafeForFastPath(GenericStack stack, boolean input) {
        if (stack.amount() <= 0 || stack.amount() > Integer.MAX_VALUE) {
            return false;
        }
        if (!(stack.what() instanceof AEItemKey itemKey)) {
            return false;
        }
        ItemStack itemStack = itemKey.toStack(1);
        if (!itemStack.isComponentsPatchEmpty() || itemKey.isDamaged()) {
            return false;
        }
        if (input) {
            return !itemStack.isDamageableItem()
                && !itemStack.getItem().hasCraftingRemainingItem(itemStack);
        }
        return true;
    }

    private static Optional<ItemStack> toItemStack(GenericStack stack) {
        if (stack.amount() <= 0 || stack.amount() > MAX_SAFE_ITEM_STACK_COUNT) {
            return Optional.empty();
        }
        if (!(stack.what() instanceof AEItemKey itemKey)) {
            return Optional.empty();
        }
        ItemStack itemStack = itemKey.toStack((int) stack.amount());
        return itemStack.isEmpty() ? Optional.empty() : Optional.of(itemStack);
    }

    private static boolean appendItemStacks(GenericStack stack, List<ItemStack> target) {
        if (stack.amount() <= 0 || stack.amount() > Integer.MAX_VALUE) {
            return false;
        }
        if (!(stack.what() instanceof AEItemKey itemKey)) {
            return false;
        }
        int remaining = (int) stack.amount();
        while (remaining > 0) {
            int count = Math.min(remaining, MAX_SAFE_ITEM_STACK_COUNT);
            ItemStack itemStack = itemKey.toStack(count);
            if (itemStack.isEmpty()) {
                return false;
            }
            target.add(itemStack);
            remaining -= count;
        }
        return true;
    }

    public static ListTag writeGenericStacks(HolderLookup.Provider registries, List<GenericStack> stacks) {
        ListTag tag = new ListTag();
        for (GenericStack stack : stacks) {
            if (stack != null && stack.amount() > 0) {
                tag.add(GenericStack.writeTag(registries, stack));
            }
        }
        return tag;
    }

    public static List<GenericStack> readGenericStacks(HolderLookup.Provider registries, ListTag tag) {
        try {
            List<GenericStack> stacks = new ArrayList<>(tag.size());
            for (int i = 0; i < tag.size(); i++) {
                CompoundTag stackTag = tag.getCompound(i);
                GenericStack stack = GenericStack.readTag(registries, stackTag);
                if (stack != null && stack.amount() > 0) {
                    stacks.add(stack);
                }
            }
            return List.copyOf(stacks);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public static Optional<List<GenericStack>> readValidatedBatchItemStacks(
        HolderLookup.Provider registries,
        ListTag tag,
        boolean requireNonEmpty
    ) {
        if (tag.size() > ECOBatchCraftingHelper.MAX_BATCH_STACK_ENTRIES
            || requireNonEmpty && tag.isEmpty()) {
            return Optional.empty();
        }
        try {
            List<GenericStack> stacks = new ArrayList<>(tag.size());
            for (int i = 0; i < tag.size(); i++) {
                GenericStack stack = GenericStack.readTag(registries, tag.getCompound(i));
                if (stack == null) {
                    return Optional.empty();
                }
                stacks.add(stack);
            }
            if (!ECOBatchCraftingHelper.areValidPersistedItemStacks(
                    stacks, ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT, requireNonEmpty)) {
                return Optional.empty();
            }
            return Optional.of(List.copyOf(stacks));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static List<GenericStack> copySorted(KeyCounter counter) {
        List<GenericStack> stacks = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            if (entry.getLongValue() > 0) {
                stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        stacks.sort(Comparator.comparing((GenericStack stack) -> keySortId(stack.what()))
            .thenComparingLong(GenericStack::amount));
        return List.copyOf(stacks);
    }

    private static String keySortId(@Nullable AEKey key) {
        if (key == null) {
            return "";
        }
        return key.getType().getId() + ":" + key.getId() + ":" + key.hashCode();
    }
}
