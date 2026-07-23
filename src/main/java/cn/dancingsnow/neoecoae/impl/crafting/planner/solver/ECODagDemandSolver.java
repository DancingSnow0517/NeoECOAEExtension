package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOStrongComponents;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
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
        ECOPlanningGraph<K, R> graph = ECOGraphPruner.targetReachable(
            new ECOPlanningGraph<>(problem.operations()),
            problem.requested().keySet()
        );
        if (containsCycle(graph)) {
            return Optional.empty();
        }

        Map<K, Long> balances = new LinkedHashMap<>(problem.inventory());
        problem.requested().forEach((key, amount) -> balances.merge(key, -amount, Math::addExact));
        Map<R, Long> executions = new LinkedHashMap<>();
        long expansions = 0;

        while (true) {
            K deficient = null;
            for (var entry : balances.entrySet()) {
                if (entry.getValue() < 0 && !graph.producersOf(entry.getKey()).isEmpty()) {
                    deficient = entry.getKey();
                    break;
                }
            }
            if (deficient == null) {
                break;
            }

            K deficientMaterial = deficient;
            List<ECOPlanningOperation<K, R>> producers = graph.producersOf(deficientMaterial).stream()
                .filter(operation -> operation.netOutput(deficientMaterial) > 0)
                .toList();
            if (producers.size() != 1) {
                return Optional.empty();
            }
            ECOPlanningOperation<K, R> operation = producers.getFirst();
            long missing = -balances.get(deficientMaterial);
            long batches = ceilDiv(missing, operation.netOutput(deficientMaterial));
            executions.merge(operation.reference(), batches, Math::addExact);
            operation.inputs().forEach((key, amount) -> mergeScaled(balances, key, amount, -batches));
            operation.outputs().forEach((key, amount) -> mergeScaled(balances, key, amount, batches));
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
        if (ECOStrongComponents.find(graph).stream().anyMatch(component -> component.size() > 1)) {
            return true;
        }
        for (var operation : graph.operations()) {
            Set<K> outputs = operation.outputs().keySet();
            if (operation.inputs().keySet().stream().anyMatch(outputs::contains)) {
                return true;
            }
        }
        return false;
    }

    private static long ceilDiv(long numerator, long denominator) {
        return 1 + (numerator - 1) / denominator;
    }

    private static <K> void mergeScaled(Map<K, Long> balances, K key, long amount, long batches) {
        balances.merge(key, Math.multiplyExact(amount, batches), Math::addExact);
    }
}
