package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import java.util.List;
import java.util.Map;

/**
 * Guards positive-net operations that also consume the material they produce.
 */
public final class ECOCycleBootstrap {
    private static final long BOOTSTRAP_PENALTY = 1_000_000L;

    private ECOCycleBootstrap() {
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
            if (available < input.getValue()) {
                return false;
            }
        }
        return true;
    }

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
