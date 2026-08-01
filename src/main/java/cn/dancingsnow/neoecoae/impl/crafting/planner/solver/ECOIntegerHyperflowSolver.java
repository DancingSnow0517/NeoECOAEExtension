package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;
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
        private long overflowBranches;
        private boolean exhausted;
        private Termination termination = Termination.NONE;

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
            if (exhausted) {
                status = ECOHyperflowResult.Status.BUDGET_EXHAUSTED;
            } else if (best.requestedShortfall() > 0 || best.dependencyShortfall() > 0) {
                status = ECOHyperflowResult.Status.NO_ROUTE;
            } else if (best.sourceShortfall() > 0) {
                status = ECOHyperflowResult.Status.MISSING_SOURCES;
            } else {
                status = ECOHyperflowResult.Status.COMPLETE;
            }
            ECOHyperflowResult<R> result = new ECOHyperflowResult<>(status, best, expandedStates);
            ECOPlanningFailureDiagnostics.logSolverResult(
                ECOPlanningFailureDiagnostics.Stage.INTEGER_SOLVER,
                problem,
                result,
                "budgetMaxExpandedStates=" + budget.maxExpandedStates()
                    + " maxDepth=" + budget.maxDepth()
                    + " extraBatchChoices=" + budget.extraBatchChoices()
                    + " exhausted=" + exhausted
                    + " termination=" + termination
                    + " overflowBranches=" + overflowBranches
                    + " visitedStates=" + visited.size()
            );
            if (exhausted) {
                ECOPlanningFailureDiagnostics.logFailure(
                    ECOPlanningFailureDiagnostics.Stage.INTEGER_SOLVER,
                    ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED,
                    problem.requested().keySet().stream().findFirst().orElse(null),
                    problem.requested().values().stream().findFirst().orElse(0L),
                    "integer",
                    "termination=" + termination
                        + " expandedStates=" + expandedStates
                        + " maxExpandedStates=" + budget.maxExpandedStates()
                );
            }
            return result;
        }

        private void explore(long[] counts, int depth) {
            if (shouldStop()) {
                exhausted = true;
                termination = Thread.currentThread().isInterrupted()
                    ? Termination.INTERRUPTED
                    : Termination.DEADLINE;
                return;
            }
            if (expandedStates >= budget.maxExpandedStates() || depth > budget.maxDepth()) {
                exhausted = true;
                termination = expandedStates >= budget.maxExpandedStates()
                    ? Termination.MAX_EXPANDED_STATES
                    : Termination.MAX_DEPTH;
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
                    termination = Thread.currentThread().isInterrupted()
                        ? Termination.INTERRUPTED
                        : Termination.DEADLINE;
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
                        termination = Thread.currentThread().isInterrupted()
                            ? Termination.INTERRUPTED
                            : Termination.DEADLINE;
                        return;
                    }
                    long increment;
                    try {
                        increment = Math.addExact(minimum, extra);
                    } catch (ArithmeticException ignored) {
                        exhausted = true;
                        termination = Termination.ARITHMETIC_OVERFLOW;
                        continue;
                    }
                    long[] branch = counts.clone();
                    try {
                        branch[index] = Math.addExact(branch[index], increment);
                    } catch (ArithmeticException ignored) {
                        exhausted = true;
                        termination = Termination.ARITHMETIC_OVERFLOW;
                        continue;
                    }
                    explore(branch, depth + 1);
                }
            }
        }

        private Evaluation<K, R> evaluate(long[] counts) {
            if (shouldStop()) {
                exhausted = true;
                termination = Thread.currentThread().isInterrupted()
                    ? Termination.INTERRUPTED
                    : Termination.DEADLINE;
                return null;
            }
            Map<K, Long> available = new LinkedHashMap<>(problem.inventory());
            Map<R, Long> executions = new LinkedHashMap<>();
            for (int i = 0; i < operations.size(); i++) {
                if (shouldStop()) {
                    exhausted = true;
                    termination = Thread.currentThread().isInterrupted()
                        ? Termination.INTERRUPTED
                        : Termination.DEADLINE;
                    return null;
                }
                long count = counts[i];
                if (count == 0) {
                    continue;
                }
                var operation = operations.get(i);
                executions.put(operation.reference(), count);
                for (var input : operation.inputs().entrySet()) {
                    if (!mergeScaled(available, input.getKey(), input.getValue(), -count)) {
                        overflowBranches++;
                        return null;
                    }
                }
                for (var output : operation.outputs().entrySet()) {
                    if (!mergeScaled(available, output.getKey(), output.getValue(), count)) {
                        overflowBranches++;
                        return null;
                    }
                }
            }

            long requestedShortfall = 0;
            Map<K, Long> balances = new LinkedHashMap<>(available);
            for (var request : problem.requested().entrySet()) {
                if (shouldStop()) {
                    exhausted = true;
                    termination = Thread.currentThread().isInterrupted()
                        ? Termination.INTERRUPTED
                        : Termination.DEADLINE;
                    return null;
                }
                long present = Math.max(0, available.getOrDefault(request.getKey(), 0L));
                requestedShortfall = ECOPlannerMath.saturatedAdd(
                    requestedShortfall,
                    Math.max(0, request.getValue() - present)
                );
                if (!mergeScaled(balances, request.getKey(), request.getValue(), -1L)) {
                    overflowBranches++;
                    return null;
                }
            }

            long sourceShortfall = 0;
            long dependencyShortfall = 0;
            long surplus = 0;
            for (var balance : balances.entrySet()) {
                if (shouldStop()) {
                    exhausted = true;
                    termination = Thread.currentThread().isInterrupted()
                        ? Termination.INTERRUPTED
                        : Termination.DEADLINE;
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
                    termination = Thread.currentThread().isInterrupted()
                        ? Termination.INTERRUPTED
                        : Termination.DEADLINE;
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
                    termination = Thread.currentThread().isInterrupted()
                        ? Termination.INTERRUPTED
                        : Termination.DEADLINE;
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

    private static <K> boolean mergeScaled(Map<K, Long> target, K key, long amount, long scale) {
        try {
            long delta = Math.multiplyExact(amount, scale);
            target.merge(key, delta, Math::addExact);
            return true;
        } catch (ArithmeticException ignored) {
            return false;
        }
    }

    private record Evaluation<K, R>(Map<K, Long> balances, ECOPlanCandidate<R> candidate) {
    }

    private record Deficiency<K>(K material, long amount) {
    }

    private enum Termination {
        NONE,
        DEADLINE,
        INTERRUPTED,
        MAX_EXPANDED_STATES,
        MAX_DEPTH,
        ARITHMETIC_OVERFLOW
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
