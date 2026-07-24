package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Demand-driven component propagation for graphs with several producers.
 *
 * <p>The old path went straight from a multi-producer graph to an exponential
 * count-vector search. This solver keeps the same integer operation contract,
 * but resolves one deficient material at a time and scores producers by the
 * inventory they can satisfy immediately. It is deliberately conservative:
 * unresolved cycles and ambiguous shortages are left for the bounded search
 * fallback.</p>
 */
public final class ECOComponentDemandSolver {
    private ECOComponentDemandSolver() {
    }

    public static <K, R> Optional<ECOHyperflowResult<R>> trySolve(
        ECOPlanningProblem<K, R> problem
    ) {
        return trySolve(problem, ECOGraphPruner.targetReachable(problem), ECOSolveBudget.DEFAULT.deadlineNanos());
    }

    public static <K, R> Optional<ECOHyperflowResult<R>> trySolve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph
    ) {
        return trySolve(problem, graph, ECOSolveBudget.DEFAULT.deadlineNanos());
    }

    public static <K, R> Optional<ECOHyperflowResult<R>> trySolve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        long deadlineNanos
    ) {
        if (ECOSolveBudget.shouldStop(deadlineNanos)) {
            return Optional.empty();
        }
        Map<K, Long> balances = new LinkedHashMap<>(problem.inventory());
        problem.requested().forEach((key, amount) -> balances.merge(key, -amount, Math::addExact));
        Map<R, Long> executions = new LinkedHashMap<>();
        Set<K> expandableMaterials = findExpandableMaterials(graph);
        ArrayDeque<K> queue = new ArrayDeque<>();
        Set<K> queued = new HashSet<>();
        problem.requested().keySet().forEach(key -> enqueueIfDeficient(key, balances, graph, queue, queued));

        long expansions = 0;
        long maxExpansions = Math.min(1_000_000L,
            Math.max(64L, (long) graph.materials().size() * 8L + graph.operations().size() * 4L));
        try {
            while (!queue.isEmpty()) {
                if (ECOSolveBudget.shouldStop(deadlineNanos) || ++expansions > maxExpansions) {
                    return Optional.empty();
                }
                K material = queue.removeFirst();
                queued.remove(material);
                long deficit = -balances.getOrDefault(material, 0L);
                if (deficit <= 0) {
                    continue;
                }
                List<ECOPlanningOperation<K, R>> producers = graph.producersOf(material);
                ECOPlanningOperation<K, R> producer = chooseProducer(
                    material,
                    deficit,
                    producers,
                    balances,
                    problem.requested(),
                    expandableMaterials,
                    deadlineNanos
                );
                if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                    return Optional.empty();
                }
                if (producer == null) {
                    continue;
                }
                long net = producer.netOutput(material);
                if (net <= 0) {
                    continue;
                }
                long bootstrapDeficit = ECOCycleBootstrap.bootstrapDeficit(
                    material, producers, balances, problem.requested()
                );
                long demand = bootstrapDeficit > 0L ? bootstrapDeficit : deficit;
                long batches = ECOPlannerMath.ceilDiv(demand, net);
                executions.merge(producer.reference(), batches, Math::addExact);
                producer.inputs().forEach((key, amount) -> {
                    balances.merge(key, Math.multiplyExact(amount, -batches), Math::addExact);
                    enqueueIfDeficient(key, balances, graph, queue, queued);
                });
                producer.outputs().forEach((key, amount) -> {
                    balances.merge(key, Math.multiplyExact(amount, batches), Math::addExact);
                    enqueueIfDeficient(key, balances, graph, queue, queued);
                });
            }
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }

        if (ECOSolveBudget.shouldStop(deadlineNanos)) {
            return Optional.empty();
        }

        return Optional.of(ECOPlannerMath.buildResult(balances, executions, problem.requested(), expandableMaterials, expansions));
    }

    private static <K, R> ECOPlanningOperation<K, R> chooseProducer(
        K material,
        long deficit,
        List<ECOPlanningOperation<K, R>> producers,
        Map<K, Long> balances,
        Map<K, Long> requested,
        Set<K> expandableMaterials,
        long deadlineNanos
    ) {
        // bootstrapDeficit does not depend on the current producer — compute once outside the loop.
        long bootstrapDeficit = ECOCycleBootstrap.bootstrapDeficit(material, producers, balances, requested);
        ECOPlanningOperation<K, R> best = null;
        long bestScore = Long.MAX_VALUE;
        for (var operation : producers) {
            if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                return null;
            }
            if (!ECOCycleBootstrap.canPotentiallyStart(operation, balances, requested)) {
                continue;
            }
            long net = ECOPlannerMath.positiveNet(operation, material);
            if (net <= 0) {
                continue;
            }
            long demand = bootstrapDeficit > 0L ? bootstrapDeficit : deficit;
            long batches = ECOPlannerMath.ceilDiv(demand, net);
            long score = 0;
            for (var input : operation.inputs().entrySet()) {
                long available = Math.max(0L, balances.getOrDefault(input.getKey(), 0L));
                long required;
                try {
                    required = Math.multiplyExact(input.getValue(), batches);
                } catch (ArithmeticException ignored) {
                    required = Long.MAX_VALUE;
                }
                long missing = required <= available ? 0L : required - available;
                if (operation.outputs().containsKey(input.getKey())) {
                    long bootstrapMissing = ECOCycleBootstrap.missingBootstrapAmount(
                        operation, input.getKey(), missing, balances, requested
                    );
                    if (bootstrapMissing > 0L) {
                        score = ECOPlannerMath.saturatedAdd(score, ECOCycleBootstrap.bootstrapPenalty());
                        missing = bootstrapMissing;
                    } else {
                        missing = 0L;
                    }
                }
                if (missing > 0) {
                    score = ECOPlannerMath.saturatedAdd(
                        score,
                        expandableMaterials.contains(input.getKey())
                            ? ECOPlannerMath.saturatedMultiply(missing, 4L)
                            : 1_000_000L
                    );
                }
            }
            // Prefer fewer local steps and better output density after dependency cost.
            score = ECOPlannerMath.saturatedAdd(score, operation.inputs().size() * 16L);
            score = ECOPlannerMath.saturatedAdd(score, Math.max(0L, 1_000L / Math.min(net, 1_000L)));
            score = ECOPlannerMath.saturatedAdd(score, Math.max(0L, batches - 1L));
            if (score < bestScore) {
                best = operation;
                bestScore = score;
            }
        }
        return best;
    }

    private static <K, R> void enqueueIfDeficient(
        K material,
        Map<K, Long> balances,
        ECOPlanningGraph<K, R> graph,
        ArrayDeque<K> queue,
        Set<K> queued
    ) {
        if (balances.getOrDefault(material, 0L) < 0
            && !graph.producersOf(material).isEmpty()
            && queued.add(material)) {
            queue.addLast(material);
        }
    }

    private static <K, R> Set<K> findExpandableMaterials(ECOPlanningGraph<K, R> graph) {
        Set<K> expandable = new HashSet<>();
        for (var operation : graph.operations()) {
            for (K output : operation.selectableOutputs()) {
                if (ECOPlannerMath.positiveNet(operation, output) > 0) {
                    expandable.add(output);
                }
            }
        }
        return expandable;
    }
}
