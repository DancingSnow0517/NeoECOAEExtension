package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import java.util.Map;
import java.util.Set;

/** Shared arithmetic utilities for ECO planning solvers. */
final class ECOPlannerMath {
    private ECOPlannerMath() {
    }

    /** Returns ceil(numerator / denominator) without overflow. */
    static long ceilDiv(long numerator, long denominator) {
        long quotient = numerator / denominator;
        return numerator % denominator == 0 ? quotient : quotient + 1;
    }

    /** Saturating addition: returns Long.MAX_VALUE or Long.MIN_VALUE on overflow. */
    static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return right < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }

    /** Saturating multiplication: returns Long.MAX_VALUE or Long.MIN_VALUE on overflow. */
    static long saturatedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    /** Saturating negation: maps Long.MIN_VALUE to Long.MAX_VALUE. */
    static long saturatedNegate(long value) {
        return value == Long.MIN_VALUE ? Long.MAX_VALUE : -value;
    }

    /** Returns the positive net output of an operation for a material, or Long.MAX_VALUE on overflow. */
    static <K, R> long positiveNet(ECOPlanningOperation<K, R> operation, K material) {
        try {
            return operation.netOutput(material);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    /** Sums a collection of longs with saturation. */
    static long saturatedSum(Iterable<Long> values) {
        long total = 0;
        for (long value : values) {
            total = saturatedAdd(total, value);
        }
        return total;
    }

    /**
     * Builds a hyperflow result from balances, executions, and expandable materials.
     * Used by ECOComponentDemandSolver and ECOIntegerHyperflowSolver.
     */
    static <K, R> ECOHyperflowResult<R> buildResult(
        Map<K, Long> balances,
        Map<R, Long> executions,
        Map<K, Long> requested,
        Set<K> expandableMaterials,
        long expansions
    ) {
        long requestedShortfall = 0;
        long dependencyShortfall = 0;
        long sourceShortfall = 0;
        long surplus = 0;

        for (var entry : balances.entrySet()) {
            long balance = entry.getValue();
            if (balance < 0) {
                long missing = balance == Long.MIN_VALUE ? Long.MAX_VALUE : -balance;
                if (requested.containsKey(entry.getKey())) {
                    requestedShortfall = saturatedAdd(requestedShortfall, missing);
                } else if (expandableMaterials.contains(entry.getKey())) {
                    dependencyShortfall = saturatedAdd(dependencyShortfall, missing);
                } else {
                    sourceShortfall = saturatedAdd(sourceShortfall, missing);
                }
            } else {
                surplus = saturatedAdd(surplus, balance);
            }
        }

        ECOPlanCandidate<R> candidate = new ECOPlanCandidate<>(
            executions,
            requestedShortfall,
            dependencyShortfall,
            sourceShortfall,
            surplus
        );

        ECOHyperflowResult.Status status = requestedShortfall > 0 || dependencyShortfall > 0
            ? ECOHyperflowResult.Status.NO_ROUTE
            : sourceShortfall > 0
                ? ECOHyperflowResult.Status.MISSING_SOURCES
                : ECOHyperflowResult.Status.COMPLETE;

        return new ECOHyperflowResult<>(status, candidate, expansions);
    }
}
