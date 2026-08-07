package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOStrongComponents;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Exact replay of the DAG fast path, used only after its long arithmetic overflows. */
public final class ECOBigIntegerDagSolver {
    private ECOBigIntegerDagSolver() {
    }

    public static <K, R> Optional<Result<R>> trySolve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph
    ) {
        if (containsCycle(graph)) {
            return Optional.empty();
        }

        Map<K, BigInteger> balances = new LinkedHashMap<>();
        problem.inventory().forEach((key, amount) -> balances.put(key, BigInteger.valueOf(amount)));
        problem.requested().keySet().forEach(balances::remove);
        problem.requested().forEach((key, amount) -> balances.merge(
            key, BigInteger.valueOf(amount).negate(), BigInteger::add
        ));

        Map<R, BigInteger> executions = new LinkedHashMap<>();
        ArrayDeque<K> deficientMaterials = new ArrayDeque<>();
        Set<K> queued = new HashSet<>();
        for (K requested : problem.requested().keySet()) {
            enqueueIfDeficient(requested, balances, graph, deficientMaterials, queued);
        }

        while (!deficientMaterials.isEmpty()) {
            K deficientMaterial = deficientMaterials.removeFirst();
            queued.remove(deficientMaterial);
            BigInteger balance = balances.getOrDefault(deficientMaterial, BigInteger.ZERO);
            if (balance.signum() >= 0) {
                continue;
            }
            List<ECOPlanningOperation<K, R>> producers = graph.producersOf(deficientMaterial).stream()
                .filter(operation -> positiveNet(operation, deficientMaterial).signum() > 0)
                .toList();
            if (producers.size() != 1) {
                return Optional.empty();
            }

            ECOPlanningOperation<K, R> operation = producers.getFirst();
            BigInteger batches = ceilDiv(balance.negate(), positiveNet(operation, deficientMaterial));
            executions.merge(operation.reference(), batches, BigInteger::add);
            operation.inputs().forEach((key, amount) -> {
                mergeScaled(balances, key, amount, batches.negate());
                enqueueIfDeficient(key, balances, graph, deficientMaterials, queued);
            });
            operation.outputs().forEach((key, amount) -> {
                mergeScaled(balances, key, amount, batches);
                enqueueIfDeficient(key, balances, graph, deficientMaterials, queued);
            });
        }

        return Optional.of(new Result<>(Map.copyOf(executions)));
    }

    private static <K, R> boolean containsCycle(ECOPlanningGraph<K, R> graph) {
        return ECOStrongComponents.find(graph).stream().anyMatch(component -> component.size() > 1)
            || graph.operations().stream().anyMatch(operation -> operation.inputs().keySet().stream()
                .anyMatch(operation.outputs()::containsKey));
    }

    private static <K, R> BigInteger positiveNet(ECOPlanningOperation<K, R> operation, K material) {
        return BigInteger.valueOf(operation.outputAmount(material))
            .subtract(BigInteger.valueOf(operation.inputAmount(material)));
    }

    private static BigInteger ceilDiv(BigInteger numerator, BigInteger denominator) {
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    private static <K, R> void enqueueIfDeficient(
        K material,
        Map<K, BigInteger> balances,
        ECOPlanningGraph<K, R> graph,
        ArrayDeque<K> deficientMaterials,
        Set<K> queued
    ) {
        if (balances.getOrDefault(material, BigInteger.ZERO).signum() < 0
            && !graph.producersOf(material).isEmpty()
            && queued.add(material)) {
            deficientMaterials.addLast(material);
        }
    }

    private static <K> void mergeScaled(
        Map<K, BigInteger> balances,
        K key,
        long amount,
        BigInteger batches
    ) {
        balances.merge(key, BigInteger.valueOf(amount).multiply(batches), BigInteger::add);
    }

    public record Result<R>(Map<R, BigInteger> executions) {
    }
}
