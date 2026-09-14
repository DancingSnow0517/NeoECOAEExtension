package cn.dancingsnow.neoecoae.api.me;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/** Conservative per-key bound for all physical inventory and outstanding outputs of a child job. */
public final class ECOBigCraftingBatchLimits {
    private ECOBigCraftingBatchLimits() {}

    public static boolean fits(ICraftingPlan plan) {
        Map<AEKey, BigInteger> totals = new HashMap<>();
        for (var entry : plan.usedItems()) {
            if (!add(totals, entry.getKey(), entry.getLongValue(), 1)) return false;
        }
        for (var entry : plan.emittedItems()) {
            if (!add(totals, entry.getKey(), entry.getLongValue(), 1)) return false;
        }
        for (var task : plan.patternTimes().entrySet()) {
            long count = task.getValue();
            if (count < 0) return false;
            for (var output : task.getKey().getOutputs()) {
                if (!add(totals, output.what(), output.amount(), count)) return false;
            }
            for (var input : task.getKey().getInputs()) {
                for (var option : input.getPossibleInputs()) {
                    AEKey remainder = input.getRemainingKey(option.what());
                    if (remainder != null) {
                        BigInteger amount = BigInteger.valueOf(option.amount())
                                .multiply(BigInteger.valueOf(input.getMultiplier()))
                                .multiply(BigInteger.valueOf(count));
                        if (!add(totals, remainder, amount)) return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean add(Map<AEKey, BigInteger> totals, AEKey key, long amount, long count) {
        if (amount < 0 || count < 0) return false;
        return add(totals, key, BigInteger.valueOf(amount).multiply(BigInteger.valueOf(count)));
    }

    private static boolean add(Map<AEKey, BigInteger> totals, AEKey key, BigInteger amount) {
        return amount.signum() >= 0
                && totals.merge(key, amount, BigInteger::add).compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
    }
}
