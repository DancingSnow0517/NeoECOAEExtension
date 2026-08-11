package cn.dancingsnow.neoecoae.impl.crafting.planner.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mutates finite planning balances while keeping explicit unlimited sources immutable. */
public final class ECOPlanningBalances {
    private ECOPlanningBalances() {
    }

    public static <K, R> Map<K, Long> copyInventory(ECOPlanningProblem<K, R> problem) {
        return new LinkedHashMap<>(problem.inventory());
    }

    public static <K, R> long available(
        ECOPlanningProblem<K, R> problem,
        Map<K, Long> balances,
        K key
    ) {
        return problem.isUnlimited(key) ? Long.MAX_VALUE : balances.getOrDefault(key, 0L);
    }

    public static <K, R> void merge(
        ECOPlanningProblem<K, R> problem,
        Map<K, Long> balances,
        K key,
        long delta
    ) {
        if (problem.isUnlimited(key)) {
            return;
        }
        balances.merge(key, delta, ECOPlanningBalances::saturatedAdd);
    }

    public static <K, R> void mergeScaled(
        ECOPlanningProblem<K, R> problem,
        Map<K, Long> balances,
        K key,
        long amount,
        long batches
    ) {
        merge(problem, balances, key, saturatedMultiply(amount, batches));
    }

    public static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return right < 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }

    public static long saturatedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return (left < 0L) ^ (right < 0L) ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }
}
