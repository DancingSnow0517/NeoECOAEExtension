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
        return trySolve(problem, graph, Long.MAX_VALUE);
    }

    public static <K, R> Attempt<R> trySolve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        long deadlineNanos
    ) {
        Map<K, Long> balances = new LinkedHashMap<>(problem.inventory());
        Map<K, Long> bootstrapSupply = new LinkedHashMap<>(problem.inventory());
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
                if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                    return Attempt.rejected("planning deadline reached");
                }
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
                    material, graph.producersOf(material), bootstrapSupply
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
                ECOCycleBootstrap.addPlannedProduction(producer, batches, bootstrapSupply);
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
        Map<K, Long> bootstrapSupply
    ) {
        List<ECOPlanningOperation<K, R>> eligible = new ArrayList<>();
        for (var operation : producers) {
            if (ECOPlannerMath.positiveNet(operation, material) > 0L
                && ECOCycleBootstrap.canPotentiallyStart(operation, bootstrapSupply)) {
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
        return ProducerChoice.rejected("several non-equivalent producers require bounded search");
    }

    private static <K, R> K cycleInput(
        K material,
        ECOPlanningOperation<K, R> producer,
        Map<K, Set<K>> dependencyEdges
    ) {
        for (K input : producer.inputs().keySet()) {
            if (input.equals(material) || reaches(input, material, dependencyEdges)) {
                return input;
            }
        }
        return null;
    }

    private static <K> boolean reaches(K current, K target, Map<K, Set<K>> edges) {
        ArrayDeque<K> pending = new ArrayDeque<>();
        Set<K> visited = new HashSet<>();
        pending.addLast(current);
        while (!pending.isEmpty()) {
            K next = pending.removeFirst();
            if (!visited.add(next)) {
                continue;
            }
            if (next.equals(target)) {
                return true;
            }
            pending.addAll(edges.getOrDefault(next, Set.of()));
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
