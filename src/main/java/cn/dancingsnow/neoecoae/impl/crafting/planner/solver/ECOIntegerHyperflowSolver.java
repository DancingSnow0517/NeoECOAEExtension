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
        long deadlineNanos = budget.deadlineNanos();
        ECOPlanningGraph<K, R> graph = ECOGraphPruner.targetReachable(problem);
        return solve(problem, graph, budget, deadlineNanos);
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        ECOSolveBudget budget
    ) {
        return solve(problem, graph, budget, budget.deadlineNanos());
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        ECOSolveBudget budget,
        long deadlineNanos
    ) {
        Search<K, R> search = new Search<>(problem, graph, budget, deadlineNanos);
        return search.run();
    }

    private static final class Search<K, R> {
        private final ECOPlanningProblem<K, R> problem;
        private final ECOPlanningGraph<K, R> graph;
        private final ECOSolveBudget budget;
        private final long deadlineNanos;
        private final List<ECOPlanningOperation<K, R>> operations;
        private final Map<ECOPlanningOperation<K, R>, Integer> operationIndices = new HashMap<>();
        private final Set<K> expandableMaterials = new HashSet<>();
        private final Set<CountVector> visited = new HashSet<>();
        private ECOPlanCandidate<R> best;
        private long expandedStates;
        private boolean exhausted;

        private Search(
            ECOPlanningProblem<K, R> problem,
            ECOPlanningGraph<K, R> graph,
            ECOSolveBudget budget,
            long deadlineNanos
        ) {
            this.problem = problem;
            this.graph = graph;
            this.budget = budget;
            this.deadlineNanos = deadlineNanos;
            this.operations = graph.operations();
            for (int i = 0; i < operations.size(); i++) {
                var operation = operations.get(i);
                operationIndices.put(operation, i);
                for (K output : operation.selectableOutputs()) {
                    if (ECOPlannerMath.positiveNet(operation, output) > 0) {
                        expandableMaterials.add(output);
                    }
                }
            }
        }

        private ECOHyperflowResult<R> run() {
            explore(new long[operations.size()], 0);
            if (best == null) {
                best = new ECOPlanCandidate<>(
                    Map.of(), ECOPlannerMath.saturatedSum(problem.requested().values()), 0, 0, 0
                );
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
            if (shouldStop()) {
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
            if (evaluation == null) {
                return;
            }
            if (best == null || compare(evaluation.candidate, best) < 0) {
                best = evaluation.candidate;
            }
            // A complete residual plan is already executable candidate material;
            // continuing to enumerate alternative count vectors only adds latency.
            if (best.requestedShortfall() == 0
                && best.dependencyShortfall() == 0
                && best.sourceShortfall() == 0) {
                return;
            }
            Deficiency<K> deficiency = chooseExpandableDeficiency(evaluation.balances);
            if (deficiency == null) {
                return;
            }

            List<ECOPlanningOperation<K, R>> producers = new ArrayList<>(graph.producersOf(deficiency.material));
            producers.sort(Comparator.comparingLong(
                operation -> -ECOPlannerMath.positiveNet(operation, deficiency.material)
            ));
            long bootstrapDeficit = ECOCycleBootstrap.bootstrapDeficit(
                deficiency.material, producers, evaluation.balances, problem.requested()
            );
            for (var producer : producers) {
                if (shouldStop()) {
                    exhausted = true;
                    return;
                }
                if (!ECOCycleBootstrap.canPotentiallyStart(producer, evaluation.balances, problem.requested())) {
                    continue;
                }
                long net = ECOPlannerMath.positiveNet(producer, deficiency.material);
                if (net <= 0) {
                    continue;
                }
                long demand = bootstrapDeficit > 0L ? bootstrapDeficit : deficiency.amount;
                long minimum = ECOPlannerMath.ceilDiv(demand, net);
                int index = operationIndices.get(producer);
                for (int extra = 0; extra <= budget.extraBatchChoices(); extra++) {
                    if (shouldStop()) {
                        exhausted = true;
                        return;
                    }
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
            if (shouldStop()) {
                exhausted = true;
                return null;
            }
            Map<K, Long> available = new LinkedHashMap<>(problem.inventory());
            Map<R, Long> executions = new LinkedHashMap<>();
            for (int i = 0; i < operations.size(); i++) {
                if (shouldStop()) {
                    exhausted = true;
                    return null;
                }
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
                if (shouldStop()) {
                    exhausted = true;
                    return null;
                }
                long present = Math.max(0, available.getOrDefault(request.getKey(), 0L));
                requestedShortfall = ECOPlannerMath.saturatedAdd(
                    requestedShortfall,
                    Math.max(0, request.getValue() - present)
                );
                balances.merge(request.getKey(), -request.getValue(), ECOPlannerMath::saturatedAdd);
            }

            long sourceShortfall = 0;
            long dependencyShortfall = 0;
            long surplus = 0;
            for (var balance : balances.entrySet()) {
                if (shouldStop()) {
                    exhausted = true;
                    return null;
                }
                if (balance.getValue() < 0) {
                    long missing = ECOPlannerMath.saturatedNegate(balance.getValue());
                    if (hasPositiveProducer(balance.getKey())) {
                        dependencyShortfall = ECOPlannerMath.saturatedAdd(dependencyShortfall, missing);
                    } else {
                        sourceShortfall = ECOPlannerMath.saturatedAdd(sourceShortfall, missing);
                    }
                } else if (balance.getValue() > 0) {
                    surplus = ECOPlannerMath.saturatedAdd(surplus, balance.getValue());
                }
            }
            return new Evaluation<>(
                balances,
                new ECOPlanCandidate<>(executions, requestedShortfall, dependencyShortfall, sourceShortfall, surplus)
            );
        }

        private Deficiency<K> chooseExpandableDeficiency(Map<K, Long> balances) {
            for (K requested : problem.requested().keySet()) {
                if (shouldStop()) {
                    exhausted = true;
                    return null;
                }
                long balance = balances.getOrDefault(requested, 0L);
                if (balance < 0 && hasPositiveProducer(requested)) {
                    return new Deficiency<>(requested, ECOPlannerMath.saturatedNegate(balance));
                }
            }
            Deficiency<K> selected = null;
            for (var entry : balances.entrySet()) {
                if (shouldStop()) {
                    exhausted = true;
                    return null;
                }
                if (entry.getValue() >= 0 || !hasPositiveProducer(entry.getKey())) {
                    continue;
                }
                long amount = ECOPlannerMath.saturatedNegate(entry.getValue());
                if (selected == null || amount > selected.amount) {
                    selected = new Deficiency<>(entry.getKey(), amount);
                }
            }
            return selected;
        }

        private boolean shouldStop() {
            return ECOSolveBudget.shouldStop(deadlineNanos);
        }

        private boolean hasPositiveProducer(K material) {
            return expandableMaterials.contains(material);
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
    }

    private static <K> void mergeScaled(Map<K, Long> target, K key, long amount, long scale) {
        long delta;
        try {
            delta = Math.multiplyExact(amount, scale);
        } catch (ArithmeticException ignored) {
            delta = scale < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        target.merge(key, delta, ECOPlannerMath::saturatedAdd);
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
