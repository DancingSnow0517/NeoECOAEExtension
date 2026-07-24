package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOStrongComponents;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
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
        if (containsCycle(graph)) {
            return Optional.empty();
        }

        Map<K, Long> balances = new LinkedHashMap<>(problem.inventory());
        problem.requested().forEach((key, amount) -> balances.merge(key, -amount, Math::addExact));
        Map<R, Long> executions = new LinkedHashMap<>();
        ArrayDeque<K> deficientMaterials = new ArrayDeque<>();
        Set<K> queued = new HashSet<>();
        for (K requested : problem.requested().keySet()) {
            enqueueIfDeficient(requested, balances, graph, deficientMaterials, queued);
        }
        long expansions = 0;

        while (!deficientMaterials.isEmpty()) {
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
            long missing = -balances.get(deficientMaterial);
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

        long requestedShortfall = 0;
        long dependencyShortfall = 0;
        long sourceShortfall = 0;
        long surplus = 0;
        for (var entry : balances.entrySet()) {
            if (entry.getValue() < 0) {
                long missing = -entry.getValue();
                if (problem.requested().containsKey(entry.getKey())) {
                    requestedShortfall = Math.addExact(requestedShortfall, missing);
                }
                if (graph.producersOf(entry.getKey()).isEmpty()) {
                    sourceShortfall = Math.addExact(sourceShortfall, missing);
                } else {
                    dependencyShortfall = Math.addExact(dependencyShortfall, missing);
                }
            } else {
                surplus = Math.addExact(surplus, entry.getValue());
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
        return Optional.of(new ECOHyperflowResult<>(status, candidate, expansions));
    }

    private static <K, R> boolean containsCycle(ECOPlanningGraph<K, R> graph) {
        return ECOStrongComponents.find(graph).stream().anyMatch(scc -> scc.size() > 1)
            || graph.operations().stream().anyMatch(operation -> operation.inputs().keySet().stream()
                .anyMatch(operation.outputs()::containsKey));
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
        balances.merge(key, Math.multiplyExact(amount, batches), Math::addExact);
    }
}
