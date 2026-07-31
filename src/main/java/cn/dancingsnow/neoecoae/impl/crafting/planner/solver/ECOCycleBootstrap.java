package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import java.util.List;
import java.util.Map;

/**
 * Tracks stock that can exist before downstream demand consumes it. Net solver balances cannot
 * answer this question because they already include future consumers.
 */
public final class ECOCycleBootstrap {
    private static final long BOOTSTRAP_PENALTY = 1_000_000L;

    private ECOCycleBootstrap() {
    }

    public static <K, R> boolean canPotentiallyStart(
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> bootstrapSupply
    ) {
        for (var input : operation.inputs().entrySet()) {
            K material = input.getKey();
            if (!operation.outputs().containsKey(material)) {
                continue;
            }
            long available = available(material, bootstrapSupply);
            if (available >= input.getValue()) {
                continue;
            }
            return false;
        }
        return true;
    }

    /** Scores a missing self-input without treating future loop output as a source. */
    public static <K, R> long missingBootstrapAmount(
        ECOPlanningOperation<K, R> operation,
        K material,
        long required,
        Map<K, Long> bootstrapSupply
    ) {
        if (!operation.outputs().containsKey(material)) {
            return required;
        }
        long available = available(material, bootstrapSupply);
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
        Map<K, Long> bootstrapSupply
    ) {
        long available = available(material, bootstrapSupply);
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

    /**
     * Adds production that can exist before unrelated consumers run. The operation must already
     * be potentially startable. A self-growing output contributes only its net growth; an output
     * that is not also an input contributes its full amount.
     */
    public static <K, R> void addPlannedProduction(
        ECOPlanningOperation<K, R> operation,
        long batches,
        Map<K, Long> bootstrapSupply
    ) {
        mergePlannedProduction(operation, batches, bootstrapSupply, false);
    }

    /** Reverses {@link #addPlannedProduction(ECOPlanningOperation, long, Map)}. */
    public static <K, R> void removePlannedProduction(
        ECOPlanningOperation<K, R> operation,
        long batches,
        Map<K, Long> bootstrapSupply
    ) {
        mergePlannedProduction(operation, batches, bootstrapSupply, true);
    }

    public static <K> long available(K material, Map<K, Long> bootstrapSupply) {
        return Math.max(0L, bootstrapSupply.getOrDefault(material, 0L));
    }

    private static <K, R> void mergePlannedProduction(
        ECOPlanningOperation<K, R> operation,
        long batches,
        Map<K, Long> bootstrapSupply,
        boolean remove
    ) {
        if (batches <= 0L) {
            throw new IllegalArgumentException("batches must be positive");
        }
        for (var output : operation.outputs().entrySet()) {
            long input = operation.inputAmount(output.getKey());
            long perBatch = input == 0L ? output.getValue() : Math.max(0L, output.getValue() - input);
            if (perBatch == 0L) {
                continue;
            }
            long delta = Math.multiplyExact(perBatch, batches);
            if (remove) {
                delta = -delta;
            }
            bootstrapSupply.merge(output.getKey(), delta, Math::addExact);
        }
    }

}
