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

    enum ItemStackValidation {
        PERSISTED(true, true),
        FAST_PATH(false, false),
        FAST_PATH_INPUT(true, true),
        /** A slow-path-verified result may carry component patches and non-zero durability. */
        FAST_PATH_MUTATION(true, true);

        private final boolean componentPatchAllowed;
        private final boolean damagedAllowed;

        ItemStackValidation(boolean componentPatchAllowed, boolean damagedAllowed) {
            this.componentPatchAllowed = componentPatchAllowed;
            this.damagedAllowed = damagedAllowed;
        }

        boolean isComponentPatchAllowed() {
            return componentPatchAllowed;
        }

        boolean isDamagedAllowed() {
            return damagedAllowed;
        }
    }

    enum ItemStackValidationFailure {
        NONE,
        NULL_COLLECTION,
        TOO_MANY_ENTRIES,
        EMPTY_REQUIRED,
        NULL_STACK,
        INVALID_AMOUNT,
        NON_ITEM_KEY,
        EMPTY_ITEM_STACK,
        DAMAGED_ITEM,
        COMPONENT_PATCH
    }

    private ECOFastPathStacks() {
    }

    public static List<GenericStack> copyCounter(KeyCounter counter) {
        // copySorted only reads the counter and builds fresh GenericStacks, so no intermediate KeyCounter copy
        // is needed to keep the returned list independent of the caller's counter.
        return counter == null ? List.of() : copySorted(counter);
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

    static boolean areValidItemStacks(
        List<GenericStack> stacks,
        long maxAmount,
        boolean requireNonEmpty,
        ItemStackValidation validation
    ) {
        return validateItemStacks(stacks, maxAmount, requireNonEmpty, validation)
            == ItemStackValidationFailure.NONE;
    }

    static ItemStackValidationFailure validateItemStacks(
        List<GenericStack> stacks,
        long maxAmount,
        boolean requireNonEmpty,
        ItemStackValidation validation
    ) {
        if (stacks == null) return ItemStackValidationFailure.NULL_COLLECTION;
        if (stacks.size() > ECOBatchCraftingHelper.MAX_BATCH_STACK_ENTRIES) {
            return ItemStackValidationFailure.TOO_MANY_ENTRIES;
        }
        if (requireNonEmpty && stacks.isEmpty()) return ItemStackValidationFailure.EMPTY_REQUIRED;
        for (GenericStack stack : stacks) {
            ItemStackValidationFailure failure = validateItemStack(stack, maxAmount, validation);
            if (failure != ItemStackValidationFailure.NONE) return failure;
        }
        return ItemStackValidationFailure.NONE;
    }

    private static ItemStackValidationFailure validateItemStack(
        @Nullable GenericStack stack,
        long maxAmount,
        ItemStackValidation validation
    ) {
        if (stack == null) return ItemStackValidationFailure.NULL_STACK;
        if (stack.amount() <= 0 || stack.amount() > maxAmount) {
            return ItemStackValidationFailure.INVALID_AMOUNT;
        }
        if (!(stack.what() instanceof AEItemKey itemKey)) {
            return ItemStackValidationFailure.NON_ITEM_KEY;
        }
        if (validation == ItemStackValidation.PERSISTED) {
            return ItemStackValidationFailure.NONE;
        }
        ItemStack itemStack = itemKey.toStack(1);
        if (itemStack.isEmpty()) return ItemStackValidationFailure.EMPTY_ITEM_STACK;
        if (itemKey.isDamaged() && !validation.isDamagedAllowed()) {
            return ItemStackValidationFailure.DAMAGED_ITEM;
        }
        if (!validation.isComponentPatchAllowed() && !itemStack.isComponentsPatchEmpty()) {
            return ItemStackValidationFailure.COMPONENT_PATCH;
        }
        // Stateful inputs are admitted only after the slow-path verifier attaches a concrete model.
        return ItemStackValidationFailure.NONE;
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

    public static Optional<List<GenericStack>> readValidatedBatchItemStacks(
        HolderLookup.Provider registries,
        ListTag tag,
        boolean requireNonEmpty
    ) {
        return readValidatedBatchItemStacks(
            registries, tag, requireNonEmpty, ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT);
    }

    public static Optional<List<GenericStack>> readValidatedBatchItemStacks(
        HolderLookup.Provider registries,
        ListTag tag,
        boolean requireNonEmpty,
        long maxAmount
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
            if (!areValidItemStacks(
                    stacks,
                    maxAmount,
                    requireNonEmpty,
                    ItemStackValidation.PERSISTED)) {
                return Optional.empty();
            }
            return Optional.of(List.copyOf(stacks));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static List<GenericStack> copySorted(KeyCounter counter) {
        // Decorate-sort-undecorate: keySortId concatenates strings, so computing it once per entry instead of
        // once per comparison removes O(n log n) throwaway strings from every dispatch.
        List<SortableStack> sortable = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            if (entry.getLongValue() > 0) {
                sortable.add(new SortableStack(
                    keySortId(entry.getKey()),
                    new GenericStack(entry.getKey(), entry.getLongValue())
                ));
            }
        }
        sortable.sort(SORTABLE_ORDER);
        List<GenericStack> stacks = new ArrayList<>(sortable.size());
        for (SortableStack entry : sortable) {
            stacks.add(entry.stack());
        }
        return List.copyOf(stacks);
    }

    private record SortableStack(String sortId, GenericStack stack) {}

    private static final Comparator<SortableStack> SORTABLE_ORDER =
        Comparator.comparing(SortableStack::sortId)
            .thenComparingLong(entry -> entry.stack().amount());

    static String keySortId(@Nullable AEKey key) {
        if (key == null) {
            return "";
        }
        return key.getType().getId() + ":" + key.getId() + ":" + key.hashCode();
    }
}
