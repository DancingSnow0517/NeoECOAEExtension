package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Splits exact input/remaining matches from ingredients that are consumed by every craft.
 * Reusable inputs remain leased by the CPU and are therefore excluded from worker batch totals.
 */
public record ECOReusableCraftingPlan(
        List<GenericStack> consumedInputsPerCraft,
        List<GenericStack> reusableInputs,
        List<GenericStack> ordinaryRemainingPerCraft) {
    public ECOReusableCraftingPlan {
        consumedInputsPerCraft = List.copyOf(consumedInputsPerCraft);
        reusableInputs = List.copyOf(reusableInputs);
        ordinaryRemainingPerCraft = List.copyOf(ordinaryRemainingPerCraft);
    }

    public static ECOReusableCraftingPlan of(List<GenericStack> inputsPerCraft, List<GenericStack> remainingPerCraft) {
        Map<AEKey, Long> inputAmounts = aggregate(inputsPerCraft);
        Map<AEKey, Long> remainingAmounts = aggregate(remainingPerCraft);
        Set<AEKey> reusableKeys = new HashSet<>();
        for (var entry : inputAmounts.entrySet()) {
            long amount = entry.getValue();
            if (amount > 0L && remainingAmounts.getOrDefault(entry.getKey(), 0L) == amount) {
                reusableKeys.add(entry.getKey());
            }
        }

        return new ECOReusableCraftingPlan(
                select(inputAmounts, reusableKeys, false),
                select(inputAmounts, reusableKeys, true),
                select(remainingAmounts, reusableKeys, false));
    }

    public List<GenericStack> extraInputs(long extraCrafts) {
        return ECOBatchCraftingHelper.multiply(consumedInputsPerCraft, extraCrafts);
    }

    public List<GenericStack> batchInputs(long batchSize) {
        return ECOBatchCraftingHelper.multiply(consumedInputsPerCraft, batchSize);
    }

    public List<GenericStack> batchRemaining(long batchSize) {
        return ECOBatchCraftingHelper.multiply(ordinaryRemainingPerCraft, batchSize);
    }

    private static Map<AEKey, Long> aggregate(List<GenericStack> stacks) {
        Map<AEKey, Long> amounts = new HashMap<>();
        for (GenericStack stack : stacks) {
            if (stack != null && stack.what() != null && stack.amount() > 0L) {
                amounts.merge(stack.what(), stack.amount(), Math::addExact);
            }
        }
        return amounts;
    }

    private static List<GenericStack> select(Map<AEKey, Long> amounts, Set<AEKey> selected, boolean include) {
        KeyCounter counter = new KeyCounter();
        for (var entry : amounts.entrySet()) {
            if (selected.contains(entry.getKey()) == include) {
                counter.add(entry.getKey(), entry.getValue());
            }
        }
        return copyCounter(counter);
    }

    private static List<GenericStack> copyCounter(KeyCounter counter) {
        List<GenericStack> stacks = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            if (entry.getLongValue() > 0L) {
                stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        return List.copyOf(stacks);
    }
}
