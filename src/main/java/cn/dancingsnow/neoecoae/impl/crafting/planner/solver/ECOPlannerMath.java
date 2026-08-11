package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningBalances;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import java.util.Map;
import java.util.LinkedHashMap;
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
            return (left < 0L) ^ (right < 0L) ? Long.MIN_VALUE : Long.MAX_VALUE;
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

    /**
     * Returns how many batches of a state-transition operation can start from the current
     * balances. Ordinary operations are not capacity-limited by this helper.
     */
    static <K, R> long immediatelySupportedStateBatches(
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> balances
    ) {
        if (operation.stateTransitionInputs().isEmpty()) {
            return Long.MAX_VALUE;
        }
        long capacity = Long.MAX_VALUE;
        for (K key : operation.stateTransitionInputs()) {
            long amount = operation.inputAmount(key);
            if (amount <= 0L) {
                return 0L;
            }
            long available = Math.max(0L, balances.getOrDefault(key, 0L));
            capacity = Math.min(capacity, available / amount);
        }
        return capacity;
    }

    /** Sums a collection of longs with saturation. */
    static long saturatedSum(Iterable<Long> values) {
        long total = 0;
        for (long value : values) {
            total = saturatedAdd(total, value);
        }
        return total;
    }

    /** Initial balances for a crafting request; existing requested outputs are not a crafted result. */
    static <K, R> Map<K, Long> initialBalances(ECOPlanningProblem<K, R> problem) {
        Map<K, Long> balances = ECOPlanningBalances.copyInventory(problem);
        problem.requested().keySet().forEach(key -> balances.remove(key));
        problem.requested().forEach((key, amount) -> balances.merge(key, -amount, ECOPlannerMath::saturatedAdd));
        return balances;
    }

    /**
     * Builds a hyperflow result from balances and executions.
     * A material whose only positive producers require an unavailable self-growth seed is a
     * missing source, rather than an unresolvable dependency.
     */
    static <K, R> ECOHyperflowResult<R> buildResult(
        Map<K, Long> balances,
        Map<R, Long> executions,
        Map<K, Long> requested,
        Set<K> startableMaterials,
        Set<K> relevantMaterials,
        long expansions
    ) {
        long requestedShortfall = 0;
        long dependencyShortfall = 0;
        long sourceShortfall = 0;
        long surplus = 0;

        for (var entry : balances.entrySet()) {
            K material = entry.getKey();
            if (!requested.containsKey(material) && !relevantMaterials.contains(material)) {
                continue;
            }
            long balance = entry.getValue();
            if (balance < 0) {
                long missing = balance == Long.MIN_VALUE ? Long.MAX_VALUE : -balance;
                if (requested.containsKey(material) && startableMaterials.contains(material)) {
                    requestedShortfall = saturatedAdd(requestedShortfall, missing);
                } else if (startableMaterials.contains(material)) {
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

    static <K, R> Set<K> findStartableMaterials(
        ECOPlanningGraph<K, R> graph,
        Set<K> expandableMaterials,
        Map<K, Long> balances,
        Map<K, Long> requested
    ) {
        java.util.LinkedHashSet<K> startable = new java.util.LinkedHashSet<>();
        for (K material : expandableMaterials) {
            boolean canStart = graph.producersOf(material).stream().anyMatch(operation ->
                positiveNet(operation, material) > 0L
                    && ECOCycleBootstrap.canPotentiallyStart(operation, balances, requested)
            );
            if (canStart) {
                startable.add(material);
            }
        }
        return Set.copyOf(startable);
    }
}
