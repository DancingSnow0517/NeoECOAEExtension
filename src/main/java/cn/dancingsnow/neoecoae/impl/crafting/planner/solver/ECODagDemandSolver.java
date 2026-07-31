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

/** Linear-time batch propagation for acyclic target slices with one producer per demanded material. */
public final class ECODagDemandSolver {
    private ECODagDemandSolver() {
    }

    public static <K, R> Optional<ECOHyperflowResult<R>> trySolve(ECOPlanningProblem<K, R> problem) {
        return trySolve(problem, ECOGraphPruner.targetReachable(problem));
    }

    public static <K, R> Optional<ECOHyperflowResult<R>> trySolve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph
    ) {
        return trySolve(problem, graph, Long.MAX_VALUE);
    }

    public static <K, R> Optional<ECOHyperflowResult<R>> trySolve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        long deadlineNanos
    ) {
        if (ECOSolveBudget.shouldStop(deadlineNanos)) {
            return Optional.empty();
        }
        if (containsCycle(graph, deadlineNanos)) {
            return Optional.empty();
        }

        Map<K, Long> balances = new LinkedHashMap<>(problem.inventory());
        problem.requested().forEach((key, amount) ->
            balances.merge(key, ECOPlannerMath.saturatedNegate(amount), ECOPlannerMath::saturatedAdd));
        Map<R, Long> executions = new LinkedHashMap<>();
        ArrayDeque<K> deficientMaterials = new ArrayDeque<>();
        Set<K> queued = new HashSet<>();
        for (K requested : problem.requested().keySet()) {
            enqueueIfDeficient(requested, balances, graph, deficientMaterials, queued);
        }
        long expansions = 0;

        while (!deficientMaterials.isEmpty()) {
            if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                return Optional.empty();
            }
            K deficientMaterial = deficientMaterials.removeFirst();
            queued.remove(deficientMaterial);
            if (balances.getOrDefault(deficientMaterial, 0L) >= 0) {
                continue;
            }
            List<ECOPlanningOperation<K, R>> producers = graph.producersOf(deficientMaterial).stream()
                .filter(operation -> ECOPlannerMath.positiveNet(operation, deficientMaterial) > 0)
                .toList();
            if (producers.size() != 1) {
                return Optional.empty();
            }
            ECOPlanningOperation<K, R> operation = producers.getFirst();
            long missing = ECOPlannerMath.saturatedNegate(balances.get(deficientMaterial));
            long batches = ECOPlannerMath.ceilDiv(missing, ECOPlannerMath.positiveNet(operation, deficientMaterial));
            executions.merge(operation.reference(), batches, Math::addExact);
            operation.inputs().forEach((key, amount) -> {
                mergeScaled(balances, key, amount, -batches);
                enqueueIfDeficient(key, balances, graph, deficientMaterials, queued);
            });
            operation.outputs().forEach((key, amount) -> {
                mergeScaled(balances, key, amount, batches);
                enqueueIfDeficient(key, balances, graph, deficientMaterials, queued);
            });
            expansions++;
        }

        Set<K> expandableMaterials = new HashSet<>();
        for (var operation : graph.operations()) {
            for (K output : operation.selectableOutputs()) {
                if (ECOPlannerMath.positiveNet(operation, output) > 0L) {
                    expandableMaterials.add(output);
                }
            }
        }
        return Optional.of(ECOPlannerMath.buildResult(
            balances, executions, problem.requested(), expandableMaterials, expansions
        ));
    }

    private static <K, R> boolean containsCycle(ECOPlanningGraph<K, R> graph, long deadlineNanos) {
        Map<K, Set<K>> edges = new LinkedHashMap<>();
        Map<K, Integer> indegree = new LinkedHashMap<>();
        for (K material : graph.materials()) {
            edges.put(material, new HashSet<>());
            indegree.put(material, 0);
        }
        for (var operation : graph.operations()) {
            if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                return true;
            }
            for (K input : operation.inputs().keySet()) {
                Set<K> adjacent = edges.computeIfAbsent(input, ignored -> new HashSet<>());
                indegree.putIfAbsent(input, 0);
                for (K output : operation.outputs().keySet()) {
                    indegree.putIfAbsent(output, 0);
                    edges.computeIfAbsent(output, ignored -> new HashSet<>());
                    if (adjacent.add(output)) {
                        indegree.merge(output, 1, Integer::sum);
                    }
                }
            }
        }

        ArrayDeque<K> ready = new ArrayDeque<>();
        indegree.forEach((material, degree) -> {
            if (degree == 0) {
                ready.addLast(material);
            }
        });
        int visited = 0;
        while (!ready.isEmpty()) {
            if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                return true;
            }
            K material = ready.removeFirst();
            visited++;
            for (K output : edges.getOrDefault(material, Set.of())) {
                int degree = indegree.merge(output, -1, Integer::sum);
                if (degree == 0) {
                    ready.addLast(output);
                }
            }
        }
        return visited != indegree.size();
    }

    private static <K, R> void enqueueIfDeficient(
        K material,
        Map<K, Long> balances,
        ECOPlanningGraph<K, R> graph,
        ArrayDeque<K> deficientMaterials,
        Set<K> queued
    ) {
        if (balances.getOrDefault(material, 0L) < 0
            && !graph.producersOf(material).isEmpty()
            && queued.add(material)) {
            deficientMaterials.addLast(material);
        }
    }

    private static <K> void mergeScaled(Map<K, Long> balances, K key, long amount, long batches) {
        balances.merge(
            key,
            ECOPlannerMath.saturatedMultiply(amount, batches),
            ECOPlannerMath::saturatedAdd
        );
    }
}
