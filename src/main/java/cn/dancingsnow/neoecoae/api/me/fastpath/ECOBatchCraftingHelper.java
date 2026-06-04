package cn.dancingsnow.neoecoae.api.me.fastpath;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;

public final class ECOBatchCraftingHelper {
    private ECOBatchCraftingHelper() {
    }

    public static List<GenericStack> multiply(List<GenericStack> stacks, int multiplier) {
        if (multiplier <= 0 || stacks.isEmpty()) {
            return List.of();
        }
        KeyCounter counter = new KeyCounter();
        for (GenericStack stack : stacks) {
            long amount = multiplyExact(stack.amount(), multiplier);
            counter.add(stack.what(), amount);
        }
        return copyCounter(counter);
    }

    public static int maxCraftsFromInventory(ListCraftingInventory inventory, List<GenericStack> perCraft,
            int requested) {
        int max = requested;
        for (GenericStack stack : perCraft) {
            if (stack.amount() <= 0) {
                return 0;
            }
            long available = inventory.extract(stack.what(), Long.MAX_VALUE, Actionable.SIMULATE);
            max = Math.min(max, (int) Math.min(Integer.MAX_VALUE, available / stack.amount()));
            if (max <= 0) {
                return 0;
            }
        }
        return max;
    }

    public static boolean canExtractExact(ListCraftingInventory inventory, List<GenericStack> stacks) {
        for (GenericStack stack : stacks) {
            long extracted = inventory.extract(stack.what(), stack.amount(), Actionable.SIMULATE);
            if (extracted != stack.amount()) {
                return false;
            }
        }
        return true;
    }

    public static void extractExact(ListCraftingInventory inventory, List<GenericStack> stacks) {
        List<GenericStack> extractedStacks = new ArrayList<>(stacks.size());
        try {
            for (GenericStack stack : stacks) {
                long extracted = inventory.extract(stack.what(), stack.amount(), Actionable.MODULATE);
                if (extracted != stack.amount()) {
                    throw new IllegalStateException("Failed to extract exact fast-path batch inputs");
                }
                extractedStacks.add(stack);
            }
        } catch (RuntimeException e) {
            insertAll(inventory, extractedStacks);
            throw e;
        }
    }

    public static void insertAll(ListCraftingInventory inventory, List<GenericStack> stacks) {
        // ListCraftingInventory 是 CPU 的本地记账库存；向其插入是内存级别的回滚操作，
        // 预期不会像网络存储那样拒绝物品。
        for (GenericStack stack : stacks) {
            inventory.insert(stack.what(), stack.amount(), Actionable.MODULATE);
        }
    }

    private static List<GenericStack> copyCounter(KeyCounter counter) {
        List<GenericStack> stacks = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            if (entry.getLongValue() > 0) {
                stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        return List.copyOf(stacks);
    }

    private static long multiplyExact(long amount, int multiplier) {
        try {
            return Math.multiplyExact(amount, (long) multiplier);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Batch fast path amount overflow", e);
        }
    }
}
