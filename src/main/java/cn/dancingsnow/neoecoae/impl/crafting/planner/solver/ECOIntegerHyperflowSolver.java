package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded integer search over the target-reachable crafting hypergraph.
 * It optimizes requested output first, then missing source material, operation count and surplus.
 */
public final class ECOIntegerHyperflowSolver {
    private ECOIntegerHyperflowSolver() {
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOSolveBudget budget
    ) {
        ECOPlanningGraph<K, R> fullGraph = new ECOPlanningGraph<>(problem.operations());
        ECOPlanningGraph<K, R> graph = ECOGraphPruner.targetReachable(fullGraph, problem.requested().keySet());
        Search<K, R> search = new Search<>(problem, graph, budget);
        return search.run();
    }

    private static final class Search<K, R> {
        private final ECOPlanningProblem<K, R> problem;
        private final ECOPlanningGraph<K, R> graph;
        private final ECOSolveBudget budget;
        private final List<ECOPlanningOperation<K, R>> operations;
        private final Map<ECOPlanningOperation<K, R>, Integer> operationIndices = new HashMap<>();
        private final Set<CountVector> visited = new HashSet<>();
        private ECOPlanCandidate<R> best;
        private long expandedStates;
        private boolean exhausted;

        private Search(ECOPlanningProblem<K, R> problem, ECOPlanningGraph<K, R> graph, ECOSolveBudget budget) {
            this.problem = problem;
            this.graph = graph;
            this.budget = budget;
            this.operations = graph.operations();
            for (int i = 0; i < operations.size(); i++) {
                operationIndices.put(operations.get(i), i);
            }
        }

        private ECOHyperflowResult<R> run() {
            explore(new long[operations.size()], 0);
            if (best == null) {
                best = new ECOPlanCandidate<>(Map.of(), sum(problem.requested().values()), 0, 0, 0);
            }
            ECOHyperflowResult.Status status;
            if (exhausted && best.requestedShortfall() > 0) {
                status = ECOHyperflowResult.Status.BUDGET_EXHAUSTED;
            } else if (best.requestedShortfall() > 0 || best.dependencyShortfall() > 0) {
                status = ECOHyperflowResult.Status.NO_ROUTE;
            } else if (best.sourceShortfall() > 0) {
                status = ECOHyperflowResult.Status.MISSING_SOURCES;
            } else {
                status = ECOHyperflowResult.Status.COMPLETE;
            }
            return new ECOHyperflowResult<>(status, best, expandedStates);
        }

        private void explore(long[] counts, int depth) {
            if (Thread.currentThread().isInterrupted()) {
                exhausted = true;
                return;
            }
            if (expandedStates >= budget.maxExpandedStates() || depth > budget.maxDepth()) {
                exhausted = true;
                return;
            }
            CountVector signature = new CountVector(counts);
            if (!visited.add(signature)) {
                return;
            }
            expandedStates++;

            Evaluation<K, R> evaluation = evaluate(counts);
            if (best == null || compare(evaluation.candidate, best) < 0) {
                best = evaluation.candidate;
            }
            Deficiency<K> deficiency = chooseExpandableDeficiency(evaluation.balances);
            if (deficiency == null) {
                return;
            }

            List<ECOPlanningOperation<K, R>> producers = new ArrayList<>(graph.producersOf(deficiency.material));
            producers.sort(Comparator.comparingLong(operation -> -positiveNet(operation, deficiency.material)));
            for (var producer : producers) {
                long net = positiveNet(producer, deficiency.material);
                if (net <= 0) {
                    continue;
                }
                long minimum = ceilDiv(deficiency.amount, net);
                int index = operationIndices.get(producer);
                for (int extra = 0; extra <= budget.extraBatchChoices(); extra++) {
                    long increment;
                    try {
                        increment = Math.addExact(minimum, extra);
                    } catch (ArithmeticException ignored) {
                        exhausted = true;
                        continue;
                    }
                    long[] branch = counts.clone();
                    try {
                        branch[index] = Math.addExact(branch[index], increment);
                    } catch (ArithmeticException ignored) {
                        exhausted = true;
                        continue;
                    }
                    explore(branch, depth + 1);
                }
            }
        }

        private Evaluation<K, R> evaluate(long[] counts) {
            Map<K, Long> available = new LinkedHashMap<>(problem.inventory());
            Map<R, Long> executions = new LinkedHashMap<>();
            for (int i = 0; i < operations.size(); i++) {
                long count = counts[i];
                if (count == 0) {
                    continue;
                }
                var operation = operations.get(i);
                executions.put(operation.reference(), count);
                operation.inputs().forEach((key, amount) -> mergeScaled(available, key, amount, -count));
                operation.outputs().forEach((key, amount) -> mergeScaled(available, key, amount, count));
            }

            long requestedShortfall = 0;
            Map<K, Long> balances = new LinkedHashMap<>(available);
            for (var request : problem.requested().entrySet()) {
                long present = Math.max(0, available.getOrDefault(request.getKey(), 0L));
                requestedShortfall = saturatedAdd(
                    requestedShortfall,
                    Math.max(0, request.getValue() - present)
                );
                balances.merge(request.getKey(), -request.getValue(), ECOIntegerHyperflowSolver::saturatedAdd);
            }

            long sourceShortfall = 0;
            long dependencyShortfall = 0;
            long surplus = 0;
            for (var balance : balances.entrySet()) {
                if (balance.getValue() < 0) {
                    long missing = saturatedNegate(balance.getValue());
                    if (hasPositiveProducer(balance.getKey())) {
                        dependencyShortfall = saturatedAdd(dependencyShortfall, missing);
                    } else {
                        sourceShortfall = saturatedAdd(sourceShortfall, missing);
                    }
                } else if (balance.getValue() > 0) {
                    surplus = saturatedAdd(surplus, balance.getValue());
                }
            }
            return new Evaluation<>(
                balances,
                new ECOPlanCandidate<>(executions, requestedShortfall, dependencyShortfall, sourceShortfall, surplus)
            );
        }

        private Deficiency<K> chooseExpandableDeficiency(Map<K, Long> balances) {
            for (K requested : problem.requested().keySet()) {
                long balance = balances.getOrDefault(requested, 0L);
                if (balance < 0 && hasPositiveProducer(requested)) {
                    return new Deficiency<>(requested, saturatedNegate(balance));
                }
            }
            Deficiency<K> selected = null;
            for (var entry : balances.entrySet()) {
                if (entry.getValue() >= 0 || !hasPositiveProducer(entry.getKey())) {
                    continue;
                }
                long amount = saturatedNegate(entry.getValue());
                if (selected == null || amount > selected.amount) {
                    selected = new Deficiency<>(entry.getKey(), amount);
                }
            }
            return selected;
        }

        private boolean hasPositiveProducer(K material) {
            return graph.producersOf(material).stream().anyMatch(operation -> positiveNet(operation, material) > 0);
        }

        private static <K, R> long positiveNet(ECOPlanningOperation<K, R> operation, K material) {
            try {
                return operation.netOutput(material);
            } catch (ArithmeticException ignored) {
                return Long.MAX_VALUE;
            }
        }

        private static <R> int compare(ECOPlanCandidate<R> left, ECOPlanCandidate<R> right) {
            int result = Long.compare(left.requestedShortfall(), right.requestedShortfall());
            if (result == 0) result = Long.compare(left.dependencyShortfall(), right.dependencyShortfall());
            if (result == 0) result = Long.compare(left.sourceShortfall(), right.sourceShortfall());
            if (result == 0) result = Long.compare(left.totalExecutions(), right.totalExecutions());
            if (result == 0) result = Long.compare(left.surplus(), right.surplus());
            if (result == 0) result = Integer.compare(left.executions().size(), right.executions().size());
            return result;
        }

        private static long ceilDiv(long numerator, long denominator) {
            return 1 + (numerator - 1) / denominator;
        }

        private static long sum(Iterable<Long> values) {
            long total = 0;
            for (long value : values) total = saturatedAdd(total, value);
            return total;
        }
    }

    private static <K> void mergeScaled(Map<K, Long> target, K key, long amount, long scale) {
        long delta;
        try {
            delta = Math.multiplyExact(amount, scale);
        } catch (ArithmeticException ignored) {
            delta = scale < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        target.merge(key, delta, ECOIntegerHyperflowSolver::saturatedAdd);
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return right < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }

    private static long saturatedNegate(long value) {
        return value == Long.MIN_VALUE ? Long.MAX_VALUE : -value;
    }

    private record Evaluation<K, R>(Map<K, Long> balances, ECOPlanCandidate<R> candidate) {
    }

    private record Deficiency<K>(K material, long amount) {
    }

    private static final class CountVector {
        private final long[] values;
        private final int hash;

        private CountVector(long[] values) {
            this.values = values.clone();
            this.hash = Arrays.hashCode(this.values);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CountVector vector && Arrays.equals(values, vector.values);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
