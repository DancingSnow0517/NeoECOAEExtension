package cn.dancingsnow.neoecoae.api.me.fastpath;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ECOFastPathStacks {
    private static final ThreadLocal<Map<AEKey, String>> KEY_SORT_ID_CACHE = ThreadLocal.withInitial(WeakHashMap::new);

    private ECOFastPathStacks() {}

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

    public static List<GenericStack> copyStacks(GenericStack[] stacks) {
        KeyCounter copy = new KeyCounter();
        if (stacks != null) {
            for (GenericStack stack : stacks) {
                if (stack != null && stack.amount() > 0) {
                    copy.add(stack.what(), stack.amount());
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
            Optional<ItemStack> itemStack = toItemStack(stack);
            if (itemStack.isEmpty()) {
                return Optional.empty();
            }
            result.add(itemStack.get());
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
        if (itemKey.hasTag() || itemKey.isDamaged()) {
            return false;
        }
        ItemStack itemStack = itemKey.toStack(1);
        if (itemStack.isDamageableItem()) {
            return false;
        }
        return !input || !itemStack.getItem().hasCraftingRemainingItem(itemStack);
    }

    private static Optional<ItemStack> toItemStack(GenericStack stack) {
        if (stack.amount() <= 0 || stack.amount() > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        if (!(stack.what() instanceof AEItemKey itemKey)) {
            return Optional.empty();
        }
        ItemStack itemStack = itemKey.toStack((int) stack.amount());
        return itemStack.isEmpty() ? Optional.empty() : Optional.of(itemStack);
    }

    private static List<GenericStack> copySorted(KeyCounter counter) {
        List<GenericStack> stacks = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            if (entry.getLongValue() > 0) {
                stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        if (stacks.size() <= 1) {
            return List.copyOf(stacks);
        }

        List<SortableStack> sortable = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            sortable.add(new SortableStack(stack, keySortId(stack.what())));
        }
        sortable.sort(Comparator.comparing(SortableStack::sortId)
                .thenComparingLong(stack -> stack.stack().amount()));

        List<GenericStack> sorted = new ArrayList<>(sortable.size());
        for (SortableStack sortableStack : sortable) {
            sorted.add(sortableStack.stack());
        }
        return List.copyOf(sorted);
    }

    public static String keySortId(@Nullable AEKey key) {
        if (key == null) {
            return "";
        }
        return KEY_SORT_ID_CACHE.get().computeIfAbsent(key, ECOFastPathStacks::createKeySortId);
    }

    private static String createKeySortId(AEKey key) {
        try {
            return key.toTagGeneric().toString();
        } catch (RuntimeException e) {
            return key.getClass().getName() + ":" + key.hashCode();
        }
    }

    private record SortableStack(GenericStack stack, String sortId) {}
}
