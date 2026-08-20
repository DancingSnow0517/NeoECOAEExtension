package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.inv.ICraftingInventory;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import cn.dancingsnow.neoecoae.impl.crafting.execution.ECOFuzzyCraftingInventory;

public final class ECOBatchCraftingHelper {
    public static final int MAX_BATCH_SIZE = Integer.MAX_VALUE;
    public static final int MAX_BATCH_STACK_ENTRIES = 64;
    public static final long MAX_BATCH_STACK_AMOUNT = Long.MAX_VALUE;

    private ECOBatchCraftingHelper() {
    }

    public static List<GenericStack> multiply(List<GenericStack> stacks, int multiplier) {
        return multiply(stacks, (long) multiplier);
    }

    public static List<GenericStack> multiply(List<GenericStack> stacks, long multiplier) {
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
        return (int) maxCraftsFromInventory(inventory, perCraft, (long) requested);
    }

    public static long maxCraftsFromInventory(ListCraftingInventory inventory, List<GenericStack> perCraft,
            long requested) {
        return inventoryBatchLimit(inventory, perCraft, requested).crafts();
    }

    public static InventoryBatchLimit inventoryBatchLimit(
            ListCraftingInventory inventory,
            List<GenericStack> perCraft,
            long requested) {
        return inventoryBatchLimit(inventory, perCraft, requested, Set.of());
    }

    public static InventoryBatchLimit inventoryBatchLimit(
            ICraftingInventory inventory,
            List<GenericStack> perCraft,
            long requested,
            Set<ResourceLocation> fuzzyItemIds) {
        long max = Math.max(0L, requested);
        AEKey limitingKey = null;
        long limitingAvailable = 0L;
        long limitingPerCraft = 0L;
        Map<Object, GenericStack> grouped = new java.util.LinkedHashMap<>();
        for (GenericStack stack : perCraft) {
            Object group = ECOFuzzyCraftingInventory.isConfiguredFuzzy(stack.what(), fuzzyItemIds)
                ? stack.what().getId()
                : stack.what();
            GenericStack previous = grouped.get(group);
            if (previous == null) {
                grouped.put(group, stack);
            } else {
                grouped.put(group, new GenericStack(previous.what(), Math.addExact(
                    previous.amount(), stack.amount())));
            }
        }
        for (GenericStack stack : grouped.values()) {
            if (stack.amount() <= 0) {
                return new InventoryBatchLimit(0L, stack.what(), 0L, stack.amount());
            }
            // The CPU inventory is already an in-memory KeyCounter. Reading it directly avoids one
            // simulated crafting-inventory transaction per ingredient while preserving the exact
            // same concrete-input semantics as the verified fast-path key.
            long available = inventory instanceof ECOFuzzyCraftingInventory fuzzyInventory
                ? fuzzyInventory.extractTemplate(stack.what(), Long.MAX_VALUE, Actionable.SIMULATE)
                : inventory.extract(stack.what(), Long.MAX_VALUE, Actionable.SIMULATE);
            long ingredientLimit = available / stack.amount();
            if (ingredientLimit < max) {
                max = ingredientLimit;
                limitingKey = stack.what();
                limitingAvailable = available;
                limitingPerCraft = stack.amount();
            }
            if (max <= 0) {
                break;
            }
        }
        return new InventoryBatchLimit(
            max,
            limitingKey,
            limitingAvailable,
            limitingPerCraft
        );
    }

    public record InventoryBatchLimit(
        long crafts,
        AEKey limitingKey,
        long available,
        long perCraft) {
    }

    /**
     * Returns the largest batch whose per-key totals can be represented by a long.
     * Inputs and outputs are bounded independently; output and remaining entries share
     * one bound because both are materialized by the worker in the same batch.
     */
    public static long maxSafeBatchSize(
            List<GenericStack> inputsPerCraft,
            List<GenericStack> outputsPerCraft,
            List<GenericStack> remainingPerCraft,
            long requested) {
        if (requested <= 0L) {
            return 0L;
        }
        long inputLimit = maxBatchForAggregates(inputsPerCraft, List.of());
        long outputLimit = maxBatchForAggregates(outputsPerCraft, remainingPerCraft);
        return Math.min(requested, Math.min(inputLimit, outputLimit));
    }

    /**
     * Limits a batch to the number of crafts that can still contribute to the requested final
     * output. The task count is normally a recipe-execution count, while the final output demand
     * is an item count; keeping this conversion at the batch boundary prevents a recipe that
     * yields multiple items from doubling a request.
     */
    public static long limitByFinalOutputDemand(
            GenericStack finalOutput,
            long remainingAmount,
            long inFlightAmount,
            List<GenericStack> outputsPerCraft,
            long requested) {
        if (requested <= 0L || finalOutput == null) {
            return Math.max(0L, requested);
        }

        long outputAmountPerCraft = 0L;
        for (GenericStack output : outputsPerCraft) {
            if (output != null && output.amount() > 0L && output.what().matches(finalOutput)) {
                if (output.amount() > Long.MAX_VALUE - outputAmountPerCraft) {
                    outputAmountPerCraft = Long.MAX_VALUE;
                    break;
                }
                outputAmountPerCraft += output.amount();
            }
        }
        if (outputAmountPerCraft <= 0L) {
            // BUG: No matching output found! The recipe doesn't produce the final output,
            // or the matching logic is failing. Log this for debugging.
            AELog.warn("NeoECO FastPath limitByFinalOutputDemand: outputAmountPerCraft=0! "
                    + "finalOutput=%s remainingAmount=%d inFlightAmount=%d outputsPerCraft.size=%d requested=%d",
                    finalOutput, remainingAmount, inFlightAmount, outputsPerCraft.size(), requested);
            if (!outputsPerCraft.isEmpty()) {
                AELog.warn("  First output: %s", outputsPerCraft.get(0));
            }
            return requested;
        }

        long normalizedInFlight = Math.max(0L, inFlightAmount);
        if (normalizedInFlight >= remainingAmount) {
            return 0L;
        }
        long outstanding = remainingAmount - normalizedInFlight;
        long craftsNeeded = 1L + (outstanding - 1L) / outputAmountPerCraft;
        return Math.min(requested, craftsNeeded);
    }

    public static boolean canExtractExact(ListCraftingInventory inventory, List<GenericStack> stacks) {
        return canExtractExact(inventory, stacks, Set.of());
    }

    public static boolean canExtractExact(
            ICraftingInventory inventory, List<GenericStack> stacks, Set<ResourceLocation> fuzzyItemIds) {
        for (GenericStack stack : stacks) {
            long extracted = extractMatching(
                inventory, stack.what(), stack.amount(), Actionable.SIMULATE, null, fuzzyItemIds);
            if (extracted != stack.amount()) {
                return false;
            }
        }
        return true;
    }

    public static int maxAffordableCrafts(
        double patternPower,
        int requested,
        DoubleUnaryOperator simulatedExtraction
    ) {
        Objects.requireNonNull(simulatedExtraction, "simulatedExtraction");
        int boundedRequested = Math.max(0, requested);
        if (boundedRequested <= 0 || !Double.isFinite(patternPower) || patternPower < 0.0D) {
            return 0;
        }
        if (patternPower == 0.0D) {
            return boundedRequested;
        }
        if (hasEnoughEnergy(patternPower, boundedRequested, simulatedExtraction)) {
            return boundedRequested;
        }

        int low = 0;
        int high = boundedRequested - 1;
        while (low < high) {
            int batchSize = low + (int) (((long) high - low + 1L) / 2L);
            if (hasEnoughEnergy(patternPower, batchSize, simulatedExtraction)) {
                low = batchSize;
            } else {
                high = batchSize - 1;
            }
        }
        return low;
    }

    public static boolean areValidItemStacks(
        List<GenericStack> stacks,
        long maxAmount,
        boolean requireNonEmpty
    ) {
        if (!areValidPersistedItemStacks(stacks, maxAmount, requireNonEmpty)) {
            return false;
        }
        for (GenericStack stack : stacks) {
            AEItemKey itemKey = (AEItemKey) stack.what();
            ItemStack itemStack = itemKey.toStack(1);
            if (itemStack.isEmpty() || itemKey.isDamaged()) {
                return false;
            }
        }
        return true;
    }

    public static boolean areValidPersistedItemStacks(
        List<GenericStack> stacks,
        long maxAmount,
        boolean requireNonEmpty
    ) {
        if (stacks == null
            || stacks.size() > MAX_BATCH_STACK_ENTRIES
            || requireNonEmpty && stacks.isEmpty()) {
            return false;
        }
        for (GenericStack stack : stacks) {
            if (stack == null
                || stack.amount() <= 0
                || stack.amount() > maxAmount
                || !(stack.what() instanceof AEItemKey)) {
                return false;
            }
        }
        return true;
    }

    public static void extractExact(ListCraftingInventory inventory, List<GenericStack> stacks) {
        extractExactReturning(inventory, stacks, Set.of());
    }

    public static void extractExact(
            ICraftingInventory inventory, List<GenericStack> stacks, Set<ResourceLocation> fuzzyItemIds) {
        extractExactReturning(inventory, stacks, fuzzyItemIds);
    }

    /**
     * Extracts the requested amount and returns the concrete keys that were actually removed.
     * Callers must use this result for any later refund or persisted in-flight ledger: a configured
     * fuzzy item may have been satisfied by a different data-component variant than its template.
     */
    public static List<GenericStack> extractExactReturning(
            ICraftingInventory inventory, List<GenericStack> stacks, Set<ResourceLocation> fuzzyItemIds) {
        List<GenericStack> extractedStacks = new ArrayList<>(stacks.size());
        try {
            for (GenericStack stack : stacks) {
                long extracted = extractMatching(
                    inventory, stack.what(), stack.amount(), Actionable.MODULATE, extractedStacks, fuzzyItemIds);
                if (extracted != stack.amount()) {
                    throw new IllegalStateException("Failed to extract exact fast-path batch inputs");
                }
            }
        } catch (RuntimeException e) {
            insertAll(inventory, extractedStacks);
            throw e;
        }
        return combine(extractedStacks);
    }

    /** Combines concrete stack entries without discarding their data-component identity. */
    public static List<GenericStack> combine(List<GenericStack> first, List<GenericStack> second) {
        List<GenericStack> combined = new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return combine(combined);
    }

    /** Combines concrete stack entries without discarding their data-component identity. */
    public static List<GenericStack> combine(List<GenericStack> stacks) {
        KeyCounter counter = new KeyCounter();
        for (GenericStack stack : stacks) {
            if (stack != null && stack.amount() > 0L) {
                counter.add(stack.what(), stack.amount());
            }
        }
        return copyCounter(counter);
    }

    /**
     * Selects concrete entries equivalent to {@code requested} from an already extracted ledger.
     * For configured fuzzy items, any component variant with the configured item ID is valid; the
     * returned entries always retain the concrete keys that were originally extracted.
     */
    public static List<GenericStack> takeMatchingEntries(
            List<GenericStack> available,
            List<GenericStack> requested,
            Set<ResourceLocation> fuzzyItemIds) {
        Map<AEKey, Long> remaining = new java.util.LinkedHashMap<>();
        for (GenericStack stack : available) {
            if (stack != null && stack.amount() > 0L) {
                remaining.merge(stack.what(), stack.amount(), Math::addExact);
            }
        }

        List<GenericStack> selected = new ArrayList<>();
        for (GenericStack template : requested) {
            if (template == null || template.amount() <= 0L) {
                continue;
            }
            long needed = template.amount();
            boolean fuzzy = ECOFuzzyCraftingInventory.isConfiguredFuzzy(template.what(), fuzzyItemIds);
            for (var entry : remaining.entrySet()) {
                if (needed <= 0L) {
                    break;
                }
                AEKey candidate = entry.getKey();
                boolean matches = fuzzy
                    ? ECOFuzzyCraftingInventory.isConfiguredFuzzy(candidate, fuzzyItemIds)
                        && candidate.getId().equals(template.what().getId())
                    : candidate.equals(template.what());
                if (!matches || entry.getValue() <= 0L) {
                    continue;
                }
                long taken = Math.min(needed, entry.getValue());
                entry.setValue(entry.getValue() - taken);
                selected.add(new GenericStack(candidate, taken));
                needed -= taken;
            }
            if (needed > 0L) {
                throw new IllegalStateException("Extracted fast-path inputs cannot satisfy a rollback");
            }
        }
        return combine(selected);
    }

    private static long extractMatching(
            ICraftingInventory inventory,
            AEKey requested,
            long amount,
            Actionable mode,
            List<GenericStack> extractedStacks,
            Set<ResourceLocation> fuzzyItemIds) {
        long remaining = amount;
        long extracted = 0L;
        Iterable<AEKey> candidates = ECOFuzzyCraftingInventory.isConfiguredFuzzy(requested, fuzzyItemIds)
            ? inventory.findFuzzyTemplates(requested)
            : List.of(requested);
        for (AEKey candidate : candidates) {
            if (remaining <= 0L) {
                break;
            }
            long taken = inventory instanceof ECOFuzzyCraftingInventory fuzzyInventory
                ? fuzzyInventory.extractConcrete(candidate, remaining, mode)
                : inventory.extract(candidate, remaining, mode);
            if (taken > 0L && extractedStacks != null) {
                extractedStacks.add(new GenericStack(candidate, taken));
            }
            extracted += taken;
            remaining -= taken;
        }
        return extracted;
    }

    public static void insertAll(ListCraftingInventory inventory, List<GenericStack> stacks) {
        insertAll((ICraftingInventory) inventory, stacks, null);
    }

    public static void insertAll(ICraftingInventory inventory, List<GenericStack> stacks) {
        insertAll(inventory, stacks, null);
    }

    /**
     * Inserts all stacks into the inventory and optionally triggers inventory change events.
     * @param inventory target inventory
     * @param stacks stacks to insert
     * @param changeCallback optional callback invoked for each inserted key to trigger scheduler wake-up
     */
    public static void insertAll(
            ICraftingInventory inventory,
            List<GenericStack> stacks,
            java.util.function.Consumer<AEKey> changeCallback) {
        // ListCraftingInventory 是 CPU 的本地记账库存；向其插入是内存级别的回滚操作，
        // 预期不会像网络存储那样拒绝物品。
        for (GenericStack stack : stacks) {
            inventory.insert(stack.what(), stack.amount(), Actionable.MODULATE);
            if (changeCallback != null) {
                changeCallback.accept(stack.what());
            }
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

    private static long maxBatchForAggregates(List<GenericStack> first, List<GenericStack> second) {
        Map<AEKey, Long> totals = new HashMap<>();
        for (List<GenericStack> stacks : List.of(first, second)) {
            for (GenericStack stack : stacks) {
                if (stack == null || stack.amount() <= 0L) {
                    return 0L;
                }
                long old = totals.getOrDefault(stack.what(), 0L);
                if (old > Long.MAX_VALUE - stack.amount()) {
                    return 0L;
                }
                totals.put(stack.what(), old + stack.amount());
            }
        }
        long limit = Long.MAX_VALUE;
        for (long total : totals.values()) {
            limit = Math.min(limit, Long.MAX_VALUE / total);
        }
        return limit;
    }

    private static long multiplyExact(long amount, long multiplier) {
        try {
            return Math.multiplyExact(amount, multiplier);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Batch fast path amount overflow", e);
        }
    }

    private static boolean hasEnoughEnergy(
        double patternPower,
        int batchSize,
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
