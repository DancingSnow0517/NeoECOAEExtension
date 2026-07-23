package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
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
        ECOPlanningGraph<K, R> graph = ECOGraphPruner.targetReachable(
            new ECOPlanningGraph<>(problem.operations()),
            problem.requested().keySet()
        );
        Map<K, Long> balances = new LinkedHashMap<>(problem.inventory());
        problem.requested().forEach((key, amount) -> balances.merge(key, -amount, Math::addExact));
        Map<R, Long> executions = new LinkedHashMap<>();
        ArrayDeque<K> queue = new ArrayDeque<>();
        Set<K> queued = new HashSet<>();
        problem.requested().keySet().forEach(key -> enqueueIfDeficient(key, balances, graph, queue, queued));

        long expansions = 0;
        long maxExpansions = Math.min(1_000_000L,
            Math.max(64L, (long) graph.materials().size() * 8L + graph.operations().size() * 4L));
        try {
            while (!queue.isEmpty()) {
                if (++expansions > maxExpansions) {
                    return Optional.empty();
                }
                K material = queue.removeFirst();
                queued.remove(material);
                long deficit = -balances.getOrDefault(material, 0L);
                if (deficit <= 0) {
                    continue;
                }
                ECOPlanningOperation<K, R> producer = chooseProducer(material, graph.producersOf(material), balances, graph);
                if (producer == null) {
                    continue;
                }
                long net = producer.netOutput(material);
                if (net <= 0) {
                    continue;
                }
                long batches = ceilDiv(deficit, net);
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

        long requestedShortfall = 0;
        long dependencyShortfall = 0;
        long sourceShortfall = 0;
        long surplus = 0;
        for (var entry : balances.entrySet()) {
            long balance = entry.getValue();
            if (balance < 0) {
                long missing = balance == Long.MIN_VALUE ? Long.MAX_VALUE : -balance;
                if (problem.requested().containsKey(entry.getKey())) {
                    requestedShortfall = saturatedAdd(requestedShortfall, missing);
                } else if (graph.producersOf(entry.getKey()).stream()
                    .anyMatch(operation -> positiveNet(operation, entry.getKey()) > 0)) {
                    dependencyShortfall = saturatedAdd(dependencyShortfall, missing);
                } else {
                    sourceShortfall = saturatedAdd(sourceShortfall, missing);
                }
            } else {
                surplus = saturatedAdd(surplus, balance);
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

    private static <K, R> ECOPlanningOperation<K, R> chooseProducer(
        K material,
        List<ECOPlanningOperation<K, R>> producers,
        Map<K, Long> balances,
        ECOPlanningGraph<K, R> graph
    ) {
        ECOPlanningOperation<K, R> best = null;
        long bestScore = Long.MAX_VALUE;
        for (var operation : producers) {
            long net = positiveNet(operation, material);
            if (net <= 0) {
                continue;
            }
            long score = 0;
            for (var input : operation.inputs().entrySet()) {
                long available = Math.max(0L, balances.getOrDefault(input.getKey(), 0L));
                long missing = Math.max(0L, input.getValue() - available);
                score = saturatedAdd(score, missing);
                if (missing > 0 && graph.producersOf(input.getKey()).isEmpty()) {
                    score = saturatedAdd(score, 1_000_000L);
                }
            }
            // Prefer larger useful batches, then fewer dependency slots.
            score = saturatedAdd(score, operation.inputs().size() * 16L);
            score = saturatedAdd(score, Math.max(0L, 1_000L / Math.min(net, 1_000L)));
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

    private static <K, R> long positiveNet(ECOPlanningOperation<K, R> operation, K material) {
        try {
            return operation.netOutput(material);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long ceilDiv(long numerator, long denominator) {
        return numerator == Long.MAX_VALUE
            ? Long.MAX_VALUE
            : 1 + (numerator - 1) / denominator;
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
