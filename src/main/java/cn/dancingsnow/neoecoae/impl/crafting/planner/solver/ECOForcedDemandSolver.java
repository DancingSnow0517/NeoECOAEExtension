package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves demand through a dependency closure whose next operation is forced.
 *
 * <p>This is intentionally narrower than the component and integer solvers. It
 * accepts only one viable operation shape for each shortage, so a long tier
 * chain does not spend its bounded-search budget on a choice that does not
 * exist. Pattern variants with identical inputs and outputs are one shape.
 * Genuine alternatives and cycles are returned to the later solvers.</p>
 */
public final class ECOForcedDemandSolver {
    private ECOForcedDemandSolver() {
    }

    public static <K, R> Attempt<R> trySolve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph
    ) {
        Map<K, Long> balances = new LinkedHashMap<>(problem.inventory());
        Map<R, Long> executions = new LinkedHashMap<>();
        Set<K> expandableMaterials = findExpandableMaterials(graph);
        ArrayDeque<K> queue = new ArrayDeque<>();
        Set<K> queued = new HashSet<>();
        Map<K, Set<K>> dependencyEdges = new LinkedHashMap<>();
        long maxExpansions = Math.min(1_000_000L,
            Math.max(64L, (long) graph.materials().size() * 8L + graph.operations().size() * 4L));
        long expansions = 0L;

        try {
            problem.requested().forEach((key, amount) -> {
                balances.merge(key, -amount, Math::addExact);
                enqueueIfDeficient(key, balances, graph, queue, queued);
            });
            while (!queue.isEmpty()) {
                if (++expansions > maxExpansions) {
                    return Attempt.rejected("linear expansion limit reached");
                }
                if (Thread.currentThread().isInterrupted()) {
                    return Attempt.rejected("planner thread was interrupted");
                }
                K material = queue.removeFirst();
                queued.remove(material);
                long deficit = ECOPlannerMath.saturatedNegate(balances.getOrDefault(material, 0L));
                if (deficit <= 0L) {
                    continue;
                }
                ProducerChoice<K, R> choice = forcedProducer(
                    material, graph.producersOf(material), balances, problem.requested(), expandableMaterials
                );
                if (choice.operation() == null) {
                    return Attempt.rejected("at " + material + ": " + choice.rejection());
                }
                ECOPlanningOperation<K, R> producer = choice.operation();
                K cycleInput = cycleInput(material, producer, dependencyEdges);
                if (cycleInput != null) {
                    return Attempt.rejected("at " + material + ": dependency cycle through " + cycleInput);
                }
                long net = ECOPlannerMath.positiveNet(producer, material);
                long batches = ECOPlannerMath.ceilDiv(deficit, net);
                executions.merge(producer.reference(), batches, Math::addExact);
                dependencyEdges.computeIfAbsent(material, ignored -> new HashSet<>()).addAll(producer.inputs().keySet());
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
            return Attempt.rejected("integer overflow while expanding the dependency closure");
        }

        ECOHyperflowResult<R> result = ECOPlannerMath.buildResult(
            balances, executions, problem.requested(), expandableMaterials, expansions
        );
        return (result.status() == ECOHyperflowResult.Status.COMPLETE
            || result.status() == ECOHyperflowResult.Status.MISSING_SOURCES)
            ? Attempt.accepted(result)
            : Attempt.rejected("closure ended with " + result.status());
    }

    private static <K, R> ProducerChoice<K, R> forcedProducer(
        K material,
        List<ECOPlanningOperation<K, R>> producers,
        Map<K, Long> balances,
        Map<K, Long> requested,
        Set<K> expandableMaterials
    ) {
        List<ECOPlanningOperation<K, R>> eligible = new ArrayList<>();
        for (var operation : producers) {
            if (ECOPlannerMath.positiveNet(operation, material) > 0L
                && ECOCycleBootstrap.canPotentiallyStart(operation, balances, requested)) {
                eligible.add(operation);
            }
        }
        if (eligible.isEmpty()) {
            return ProducerChoice.rejected("no startable positive producer");
        }
        ECOPlanningOperation<K, R> selected = eligible.getFirst();
        boolean equivalent = true;
        for (int index = 1; index < eligible.size(); index++) {
            var alternative = eligible.get(index);
            if (!selected.inputs().equals(alternative.inputs()) || !selected.outputs().equals(alternative.outputs())) {
                equivalent = false;
                break;
            }
        }
        if (equivalent) {
            return ProducerChoice.accepted(selected);
        }

        long deficit = ECOPlannerMath.saturatedNegate(balances.getOrDefault(material, 0L));
        long bestScore = Long.MAX_VALUE;
        ECOPlanningOperation<K, R> best = null;
        for (var operation : eligible) {
            long score = alternativeScore(operation, material, deficit, balances, requested, expandableMaterials);
            if (score < bestScore) {
                bestScore = score;
                best = operation;
            }
        }
        // The producer list originates from the target graph and has stable
        // pattern order. Equal local scores are not a dependency ambiguity;
        // use that order, then retain the normal closure/cycle validation.
        return ProducerChoice.accepted(best);
    }

    /**
     * A strict local winner is safe to propagate: if its complete closure is
     * not executable, this solver returns to the bounded alternatives instead
     * of publishing a partial plan.
     */
    private static <K, R> long alternativeScore(
        ECOPlanningOperation<K, R> operation,
        K material,
        long deficit,
        Map<K, Long> balances,
        Map<K, Long> requested,
        Set<K> expandableMaterials
    ) {
        long net = ECOPlannerMath.positiveNet(operation, material);
        long batches = ECOPlannerMath.ceilDiv(deficit, net);
        long score = 0L;
        for (var input : operation.inputs().entrySet()) {
            long available = Math.max(0L, balances.getOrDefault(input.getKey(), 0L));
            long required = ECOPlannerMath.saturatedMultiply(input.getValue(), batches);
            long missing = required <= available ? 0L : ECOPlannerMath.saturatedAdd(required, -available);
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
            if (missing > 0L) {
                score = ECOPlannerMath.saturatedAdd(
                    score,
                    expandableMaterials.contains(input.getKey())
                        ? ECOPlannerMath.saturatedMultiply(missing, 4L)
                        : 1_000_000L
                );
            }
        }
        score = ECOPlannerMath.saturatedAdd(score, operation.inputs().size() * 16L);
        score = ECOPlannerMath.saturatedAdd(score, Math.max(0L, 1_000L / Math.min(net, 1_000L)));
        return ECOPlannerMath.saturatedAdd(score, Math.max(0L, batches - 1L));
    }

    private static <K, R> K cycleInput(
        K material,
        ECOPlanningOperation<K, R> producer,
        Map<K, Set<K>> dependencyEdges
    ) {
        for (K input : producer.inputs().keySet()) {
            if (input.equals(material) || reaches(input, material, dependencyEdges, new HashSet<>())) {
                return input;
            }
        }
        return null;
    }

    private static <K> boolean reaches(K current, K target, Map<K, Set<K>> edges, Set<K> visited) {
        if (!visited.add(current)) {
            return false;
        }
        if (current.equals(target)) {
            return true;
        }
        for (K next : edges.getOrDefault(current, Set.of())) {
            if (reaches(next, target, edges, visited)) {
                return true;
            }
        }
        return false;
    }

    private static <K, R> void enqueueIfDeficient(
        K material,
        Map<K, Long> balances,
        ECOPlanningGraph<K, R> graph,
        ArrayDeque<K> queue,
        Set<K> queued
    ) {
        if (balances.getOrDefault(material, 0L) < 0L
            && !graph.producersOf(material).isEmpty()
            && queued.add(material)) {
            queue.addLast(material);
        }
    }

    private static <K, R> Set<K> findExpandableMaterials(ECOPlanningGraph<K, R> graph) {
        Set<K> expandable = new HashSet<>();
        for (var operation : graph.operations()) {
            for (K output : operation.selectableOutputs()) {
                if (ECOPlannerMath.positiveNet(operation, output) > 0L) {
                    expandable.add(output);
                }
            }
        }
        return expandable;
    }

    public record Attempt<R>(Optional<ECOHyperflowResult<R>> result, String rejection) {
        private static <R> Attempt<R> accepted(ECOHyperflowResult<R> result) {
            return new Attempt<>(Optional.of(result), "");
        }

        private static <R> Attempt<R> rejected(String rejection) {
            return new Attempt<>(Optional.empty(), rejection);
        }
    }

    private record ProducerChoice<K, R>(ECOPlanningOperation<K, R> operation, String rejection) {
        private static <K, R> ProducerChoice<K, R> accepted(ECOPlanningOperation<K, R> operation) {
            return new ProducerChoice<>(operation, "");
        }

        private static <K, R> ProducerChoice<K, R> rejected(String rejection) {
            return new ProducerChoice<>(null, rejection);
        }
    }
}
