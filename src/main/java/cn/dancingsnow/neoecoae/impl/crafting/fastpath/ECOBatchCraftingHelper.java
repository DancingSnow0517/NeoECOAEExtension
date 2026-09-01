package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;

public final class ECOBatchCraftingHelper {
    /** Maximum number of distinct item entries in one batch. */
    public static final int MAX_BATCH_STACK_ENTRIES = 64;
    /**
     * Per-entry amount limit for a multiplied batch total, and therefore the only hard ceiling a batch
     * has. How many crafts a batch may carry is decided by the live capability of the F-series host that
     * accepts it, so no fixed batch-size constant exists.
     */
    public static final long MAX_BATCH_STACK_AMOUNT = 1L << 42;

    private ECOBatchCraftingHelper() {
    }

    /**
     * Sanitizes a persisted batch size. Only the lower bound is structural; the plausible upper bound
     * depends on the batch's own totals and is applied by the caller that owns them.
     */
    public static int clampPersistedBatchSize(int batchSize) {
        return Math.max(1, batchSize);
    }

    public static void validateBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }

    /**
     * Largest batch multiplier that keeps a single per-craft entry within {@link #MAX_BATCH_STACK_AMOUNT}
     * once it is multiplied out.
     */
    public static long maxBatchSizeForAmount(long perCraftAmount) {
        if (perCraftAmount <= 0L) {
            return 0;
        }
        return MAX_BATCH_STACK_AMOUNT / perCraftAmount;
    }

    /**
     * Largest batch multiplier that keeps every per-craft entry of a recipe within
     * {@link #MAX_BATCH_STACK_AMOUNT}. This replaces a fixed batch cap: the bound follows from the recipe
     * itself, so a host may batch as many crafts as its own thread capacity allows.
     */
    public static long maxBatchSizeForPerCraftStacks(
        List<GenericStack> inputsPerCraft,
        List<GenericStack> outputsPerCraft,
        List<GenericStack> remainingPerCraft
    ) {
        long max = Long.MAX_VALUE;
        max = Math.min(max, maxBatchSizeForStacks(inputsPerCraft));
        max = Math.min(max, maxBatchSizeForStacks(outputsPerCraft));
        max = Math.min(max, maxBatchSizeForStacks(remainingPerCraft));
        return max;
    }

    /**
     * Upper bound implied by an already-multiplied batch total: every craft contributes at least one unit
     * to each entry, so the batch size can never exceed the smallest total amount. Used to reject a
     * corrupted persisted thread-slot count without inventing a magic limit.
     */
    public static int maxBatchSizeFromTotals(List<GenericStack> totals) {
        int max = Integer.MAX_VALUE;
        for (GenericStack stack : totals) {
            if (stack == null) {
                continue;
            }
            max = (int) Math.min(max, Math.max(0L, stack.amount()));
        }
        return max;
    }

    private static long maxBatchSizeForStacks(List<GenericStack> perCraft) {
        long max = Long.MAX_VALUE;
        for (GenericStack stack : perCraft) {
            if (stack == null) {
                continue;
            }
            max = Math.min(max, maxBatchSizeForAmount(stack.amount()));
            if (max <= 0) {
                return 0;
            }
        }
        return max;
    }

    public static List<GenericStack> multiply(List<GenericStack> stacks, int multiplier) {
        return multiply(stacks, (long) multiplier);
    }

    /** Long-count multiplication used by explicit virtual batches and opt-in unknown-capacity batch commits. */
    public static List<GenericStack> multiply(List<GenericStack> stacks, long multiplier) {
        if (multiplier <= 0 || stacks.isEmpty()) {
            return List.of();
        }
        KeyCounter counter = new KeyCounter();
        for (GenericStack stack : stacks) {
            long amount = multiplyExact(stack.amount(), multiplier);
            counter.add(stack.what(), amount);
        }
        return ECOFastPathStacks.copyCounter(counter);
    }

    /** Returns {@code total - alreadyOwned}, preserving concrete AEItemKey components. */
    public static List<GenericStack> subtract(List<GenericStack> total, List<GenericStack> alreadyOwned) {
        KeyCounter counter = new KeyCounter();
        for (GenericStack stack : total) counter.add(stack.what(), stack.amount());
        for (GenericStack stack : alreadyOwned) {
            long available = counter.get(stack.what());
            if (available < stack.amount()) {
                throw new IllegalArgumentException("Batch total is smaller than its first craft inputs");
            }
            counter.remove(stack.what(), stack.amount());
        }
        return ECOFastPathStacks.copyCounter(counter);
    }

    public static int maxCraftsFromInventory(ListCraftingInventory inventory, List<GenericStack> perCraft,
            int requested) {
        int max = requested;
        for (GenericStack stack : perCraft) {
            if (stack.amount() <= 0) {
                return 0;
            }
            // The CPU inventory is already an in-memory KeyCounter. Reading it directly avoids one
            // simulated crafting-inventory transaction per ingredient while preserving the exact
            // same concrete-input semantics as the verified fast-path key.
            long available = inventory.list.get(stack.what());
            max = Math.min(max, (int) Math.min(Integer.MAX_VALUE, available / stack.amount()));
            if (max <= 0) {
                return 0;
            }
        }
        return max;
    }

    public static long maxCraftsFromInventory(ListCraftingInventory inventory, List<GenericStack> perCraft,
            long requested) {
        long max = Math.max(0L, requested);
        for (GenericStack stack : perCraft) {
            if (stack.amount() <= 0L) {
                return 0L;
            }
            max = Math.min(max, inventory.list.get(stack.what()) / stack.amount());
            if (max <= 0L) {
                return 0L;
            }
        }
        return max;
    }

    public static int maxAffordableCrafts(
        double patternPower,
        int requested,
        DoubleUnaryOperator simulatedExtraction
    ) {
        return (int) maxAffordableCrafts(patternPower, (long) requested, simulatedExtraction);
    }

    public static long maxAffordableCrafts(
        double patternPower,
        long requested,
        DoubleUnaryOperator simulatedExtraction
    ) {
        Objects.requireNonNull(simulatedExtraction, "simulatedExtraction");
        long boundedRequested = Math.max(0L, requested);
        if (boundedRequested <= 0 || !Double.isFinite(patternPower) || patternPower < 0.0D) {
            return 0;
        }
        if (patternPower == 0.0D) {
            return boundedRequested;
        }
        if (hasEnoughEnergy(patternPower, boundedRequested, simulatedExtraction)) {
            return boundedRequested;
        }

        long low = 0L;
        long high = boundedRequested - 1L;
        while (low < high) {
            long difference = high - low;
            long batchSize = low + difference / 2L + difference % 2L;
            if (hasEnoughEnergy(patternPower, batchSize, simulatedExtraction)) {
                low = batchSize;
            } else {
                high = batchSize - 1;
            }
        }
        return low;
    }

    public static void extractExact(ListCraftingInventory inventory, List<GenericStack> stacks) {
        List<GenericStack> extractedStacks = new ArrayList<>(stacks.size());
        try {
            for (GenericStack stack : stacks) {
                long extracted = inventory.extract(stack.what(), stack.amount(), Actionable.MODULATE);
                if (extracted > 0L) {
                    extractedStacks.add(new GenericStack(stack.what(), extracted));
                }
                if (extracted != stack.amount()) {
                    throw new IllegalStateException("Failed to extract exact fast-path batch inputs");
                }
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

    private static long multiplyExact(long amount, long multiplier) {
        try {
            return Math.multiplyExact(amount, (long) multiplier);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Batch fast path amount overflow", e);
        }
    }

    private static boolean hasEnoughEnergy(
        double patternPower,
        long batchSize,
        DoubleUnaryOperator simulatedExtraction
    ) {
        double totalPower = patternPower * batchSize;
        if (!Double.isFinite(totalPower) || totalPower < 0.0D) {
            return false;
        }
        double extracted = simulatedExtraction.applyAsDouble(totalPower);
        return !Double.isNaN(extracted) && extracted >= totalPower - 0.01D;
    }
}
