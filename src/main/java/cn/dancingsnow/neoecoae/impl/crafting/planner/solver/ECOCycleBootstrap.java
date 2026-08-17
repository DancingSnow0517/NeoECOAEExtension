package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Guards positive-net operations that also consume the material they produce.
 * Such an operation can grow an inventory only after its first batch has been
 * activated by an existing stack. A separate producer is planned first when
 * one is needed to create that initial stack.
 */
public final class ECOCycleBootstrap {
    private static final long BOOTSTRAP_PENALTY = 1_000_000L;

    private ECOCycleBootstrap() {
    }

    public static <K, R> boolean isSelfGrowth(ECOPlanningOperation<K, R> operation, K material) {
        long input = operation.inputAmount(material);
        return input > 0L && operation.outputAmount(material) > input;
    }

    public static <K, R> boolean canPotentiallyStart(
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> balances,
        Map<K, Long> requested
    ) {
        for (var input : operation.inputs().entrySet()) {
            K material = input.getKey();
            if (!operation.outputs().containsKey(material)) {
                continue;
            }
            long available = availableBeforeRequest(material, balances, requested);
            if (available >= input.getValue()) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * Checks startability through the producer graph, not only within one operation.
     *
     * <p>The old overload can detect {@code A -> 2A}, but it cannot distinguish
     * {@code A -> B} plus {@code B -> A} from a source-backed route. This overload
     * computes the least fixed point of materials that have either initial supply or
     * a producer whose inputs are themselves startable. A producer in an unseeded
     * dependency cycle therefore remains unavailable.</p>
     */
    public static <K, R> boolean canPotentiallyStart(
        ECOPlanningOperation<K, R> operation,
        ECOPlanningGraph<K, R> graph,
        Map<K, Long> balances,
        Map<K, Long> requested
    ) {
        return canPotentiallyStart(operation, graph, balances, requested, Set.of());
    }

    public static <K, R> boolean canPotentiallyStart(
        ECOPlanningOperation<K, R> operation,
        ECOPlanningGraph<K, R> graph,
        Map<K, Long> balances,
        Map<K, Long> requested,
        Set<K> unlimited
    ) {
        // Keep the dedicated self-growth handling in the solvers. A graph-level
        // dependency check must not turn an unseeded A -> 2A operation into a
        // normal source-backed producer.
        for (K input : operation.inputs().keySet()) {
            if (isSelfGrowth(operation, input)
                && availableBeforeRequest(input, balances, requested) < operation.inputAmount(input)) {
                return false;
            }
        }
        Map<K, Boolean> memo = new HashMap<>();
        Set<K> visiting = new HashSet<>();
        for (K input : operation.inputs().keySet()) {
            if (!isMaterialPotentiallyAvailable(
                input, graph, balances, requested, unlimited, memo, visiting)) {
                return false;
            }
        }
        return true;
    }

    private static <K, R> boolean isMaterialPotentiallyAvailable(
        K material,
        ECOPlanningGraph<K, R> graph,
        Map<K, Long> balances,
        Map<K, Long> requested,
        Set<K> unlimited,
        Map<K, Boolean> memo,
        Set<K> visiting
    ) {
        Boolean cached = memo.get(material);
        if (cached != null) {
            return cached;
        }
        if (unlimited.contains(material)
            || availableBeforeRequest(material, balances, requested) > 0L) {
            memo.put(material, true);
            return true;
        }
        if (!visiting.add(material)) {
            return false;
        }
        boolean available = false;
        for (var producer : graph.producersOf(material)) {
            if (producer.outputAmount(material) <= 0L) {
                continue;
            }
            boolean inputsAvailable = true;
            for (K input : producer.inputs().keySet()) {
                // A self-growth producer is a valid bootstrap route, but its
                // self-input is reported separately as the missing seed.
                if (input.equals(material) && isSelfGrowth(producer, material)) {
                    continue;
                }
                if (!isMaterialPotentiallyAvailable(
                    input, graph, balances, requested, unlimited, memo, visiting)) {
                    inputsAvailable = false;
                    break;
                }
            }
            if (!inputsAvailable) {
                continue;
            }
            available = true;
            break;
        }
        visiting.remove(material);
        memo.put(material, available);
        return available;
    }

    /** Scores a missing self-input without treating future loop output as a source. */
    public static <K, R> long missingBootstrapAmount(
        ECOPlanningOperation<K, R> operation,
        K material,
        long required,
        Map<K, Long> balances,
        Map<K, Long> requested
    ) {
        if (!operation.outputs().containsKey(material)) {
            return required;
        }
        long available = availableBeforeRequest(material, balances, requested);
        if (available >= operation.inputAmount(material)) {
            return 0L;
        }
        return Math.max(0L, operation.inputAmount(material) - available);
    }

    public static long bootstrapPenalty() {
        return BOOTSTRAP_PENALTY;
    }

    /** Returns the minimum seed deficit needed to activate a positive self-growth operation. */
    public static <K, R> long bootstrapDeficit(
        K material,
        List<ECOPlanningOperation<K, R>> producers,
        Map<K, Long> balances,
        Map<K, Long> requested
    ) {
        long available = availableBeforeRequest(material, balances, requested);
        long required = 0L;
        for (var producer : producers) {
            long input = producer.inputAmount(material);
            long output = producer.outputAmount(material);
            if (input > 0L && output > input && available < input) {
                required = Math.max(required, input - available);
            }
        }
        return required;
    }

    public static <K, R> long bootstrapDeficit(
        K material,
        List<ECOPlanningOperation<K, R>> producers,
        Map<K, Long> balances
    ) {
        long available = Math.max(0L, balances.getOrDefault(material, 0L));
        long required = Long.MAX_VALUE;
        for (var producer : producers) {
            long input = producer.inputAmount(material);
            long output = producer.outputAmount(material);
            if (input > 0L && output > input && available < input) {
                required = Math.min(required, input - available);
            }
        }
        return required == Long.MAX_VALUE ? 0L : required;
    }

    public static <K> long availableBeforeRequest(
        K material,
        Map<K, Long> balances,
        Map<K, Long> requested
    ) {
        long balance = balances.getOrDefault(material, 0L);
        long requestedAmount = requested.getOrDefault(material, 0L);
        try {
            return Math.max(0L, Math.addExact(balance, requestedAmount));
        } catch (ArithmeticException ignored) {
            return balance < 0L ? 0L : Long.MAX_VALUE;
        }
    }

}
