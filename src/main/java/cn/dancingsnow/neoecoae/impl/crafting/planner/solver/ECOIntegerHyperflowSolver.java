package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOInventoryScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;
import java.util.ArrayList;
import java.util.Arrays;
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
        private ECOPlanCandidate<R> completeBest;
        private long expandedStates;
        private long overflowBranches;
        private long prunedBranches;
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
            if (completeBest != null) {
                return new ECOHyperflowResult<>(
                    ECOHyperflowResult.Status.COMPLETE,
                    completeBest,
                    expandedStates
                );
            }
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
                    + " prunedBranches=" + prunedBranches
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
                if (expandedStates >= budget.maxExpandedStates()) {
                    exhausted = true;
                    termination = Termination.MAX_EXPANDED_STATES;
                } else {
                    prunedBranches++;
                }
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
            if (evaluation.candidate.requestedShortfall() == 0
                && evaluation.candidate.dependencyShortfall() == 0
                && evaluation.candidate.sourceShortfall() == 0
                && ECOInventoryScheduler.schedule(problem, evaluation.candidate).executable()) {
                if (completeBest == null || compare(evaluation.candidate, completeBest) < 0) {
                    completeBest = evaluation.candidate;
                }
                if (!containsProductiveSelfCycle(evaluation.candidate)) {
                    return;
                }
            }
            Deficiency<K> deficiency = chooseExpandableDeficiency(
                evaluation.balances, evaluation.bootstrapSupply);
            if (deficiency == null) {
                return;
            }

            List<ECOPlanningOperation<K, R>> producers = new ArrayList<>(graph.producersOf(deficiency.material));
            producers.sort((left, right) -> {
                int priority = Integer.compare(
                    statePriority(left, evaluation.balances),
                    statePriority(right, evaluation.balances)
                );
                if (priority != 0) {
                    return priority;
                }
                return Long.compare(
                    -ECOPlannerMath.positiveNet(left, deficiency.material),
                    -ECOPlannerMath.positiveNet(right, deficiency.material)
                );
            });
            long bootstrapDeficit = ECOCycleBootstrap.bootstrapDeficit(
                deficiency.material, producers, evaluation.bootstrapSupply, Map.of()
            );
            if (producers.size() == 1) {
                ECOPlanningOperation<K, R> producer = producers.getFirst();
                if (ECOCycleBootstrap.canPotentiallyStart(producer, evaluation.bootstrapSupply, Map.of())) {
                    long net = ECOPlannerMath.positiveNet(producer, deficiency.material);
                    if (net > 0L) {
                        long demand = bootstrapDeficit > 0L ? bootstrapDeficit : deficiency.amount;
                        long increment = stateAwareMinimum(
                            producer,
                            ECOPlannerMath.ceilDiv(demand, net),
                            evaluation.balances
                        );
                        int index = operationIndices.get(producer);
                        long[] branch = counts.clone();
                        try {
                            branch[index] = Math.addExact(branch[index], increment);
                            explore(branch, depth + 1);
                        } catch (ArithmeticException ignored) {
                            overflowBranches++;
                        }
                    }
                }
                return;
            }
            // Probe every producer's complete route before any split branch. This
            // keeps a usable route ahead of combinations that can consume the
            // entire state budget without ever reaching a source-backed branch.
            for (var producer : producers) {
                if (completeBest != null && !containsProductiveSelfCycle(completeBest)) {
                    return;
                }
                if (shouldStop()) {
                    exhausted = true;
                    termination = Thread.currentThread().isInterrupted()
                        ? Termination.INTERRUPTED
                        : Termination.DEADLINE;
                    return;
                }
                if (!ECOCycleBootstrap.canPotentiallyStart(producer, evaluation.bootstrapSupply, Map.of())) {
                    continue;
                }
                long net = ECOPlannerMath.positiveNet(producer, deficiency.material);
                if (net <= 0) {
                    continue;
                }
                long demand = bootstrapDeficit > 0L ? bootstrapDeficit : deficiency.amount;
                long minimum = stateAwareMinimum(
                    producer,
                    ECOPlannerMath.ceilDiv(demand, net),
                    evaluation.balances
                );
                int index = operationIndices.get(producer);
                long[] branch = counts.clone();
                try {
                    branch[index] = Math.addExact(branch[index], minimum);
                    explore(branch, depth + 1);
                } catch (ArithmeticException ignored) {
                    overflowBranches++;
                }
            }
            for (var producer : producers) {
                if (completeBest != null && !containsProductiveSelfCycle(completeBest)) {
                    return;
                }
                if (shouldStop()) {
                    exhausted = true;
                    termination = Thread.currentThread().isInterrupted()
                        ? Termination.INTERRUPTED
                        : Termination.DEADLINE;
                    return;
                }
                if (!ECOCycleBootstrap.canPotentiallyStart(producer, evaluation.bootstrapSupply, Map.of())) {
                    continue;
                }
                long net = ECOPlannerMath.positiveNet(producer, deficiency.material);
                if (net <= 0) {
                    continue;
                }
                long demand = bootstrapDeficit > 0L ? bootstrapDeficit : deficiency.amount;
                long minimum = stateAwareMinimum(
                    producer,
                    ECOPlannerMath.ceilDiv(demand, net),
                    evaluation.balances
                );
                int index = operationIndices.get(producer);
                for (long increment : branchIncrements(
                    producer, minimum, producers.size(), evaluation.bootstrapSupply)) {
                    if (increment == minimum) {
                        continue;
                    }
                    if (shouldStop()) {
                        exhausted = true;
                        termination = Thread.currentThread().isInterrupted()
                            ? Termination.INTERRUPTED
                            : Termination.DEADLINE;
                        return;
                    }
                    long[] branch = counts.clone();
                    try {
                        branch[index] = Math.addExact(branch[index], increment);
                    } catch (ArithmeticException ignored) {
                        overflowBranches++;
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

            Map<K, Long> bootstrapSupply = buildBootstrapSupply(counts);
            if (bootstrapSupply == null) {
                return null;
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
                if (present < request.getValue()
                    && hasStartableProducer(request.getKey(), bootstrapSupply)) {
                    requestedShortfall = ECOPlannerMath.saturatedAdd(
                        requestedShortfall, request.getValue() - present);
                }
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
                    if (hasStartableProducer(balance.getKey(), bootstrapSupply)) {
                        if (!problem.requested().containsKey(balance.getKey())) {
                            dependencyShortfall = ECOPlannerMath.saturatedAdd(dependencyShortfall, missing);
                        }
                    } else {
                        sourceShortfall = ECOPlannerMath.saturatedAdd(sourceShortfall, missing);
                    }
                } else if (balance.getValue() > 0
                    && (graph.materials().contains(balance.getKey())
                        || problem.requested().containsKey(balance.getKey()))) {
                    surplus = ECOPlannerMath.saturatedAdd(surplus, balance.getValue());
                }
            }
            return new Evaluation<>(
                balances,
                bootstrapSupply,
                new ECOPlanCandidate<>(executions, requestedShortfall, dependencyShortfall, sourceShortfall, surplus)
            );
        }

        private Deficiency<K> chooseExpandableDeficiency(
            Map<K, Long> balances,
            Map<K, Long> bootstrapSupply
        ) {
            Deficiency<K> selected = null;
            for (var entry : balances.entrySet()) {
                if (shouldStop()) {
                    exhausted = true;
                    termination = Thread.currentThread().isInterrupted()
                        ? Termination.INTERRUPTED
                        : Termination.DEADLINE;
                    return null;
                }
                if (entry.getValue() >= 0 || !hasStartableProducer(entry.getKey(), bootstrapSupply)) {
                    continue;
                }
                long amount = ECOPlannerMath.saturatedNegate(entry.getValue());
                if (selected == null || amount > selected.amount) {
                    selected = new Deficiency<>(entry.getKey(), amount);
                }
            }
            // A self-growing target can hide its seed deficit in a zero net
            // balance. Surface the self-input as a dependency so its producer
            // can be planned before the growth operation.
            for (K requested : problem.requested().keySet()) {
                if (balances.getOrDefault(requested, 0L) >= 0L) {
                    continue;
                }
                for (var producer : graph.producersOf(requested)) {
                    if (ECOPlannerMath.positiveNet(producer, requested) <= 0L
                        || ECOCycleBootstrap.canPotentiallyStart(producer, bootstrapSupply, Map.of())) {
                        continue;
                    }
                    for (var input : producer.inputs().entrySet()) {
                        if (producer.outputs().containsKey(input.getKey())
                            && hasStartableProducer(input.getKey(), bootstrapSupply)) {
                            return new Deficiency<>(
                                input.getKey(),
                                Math.max(1L, input.getValue()
                                    - bootstrapSupply.getOrDefault(input.getKey(), 0L))
                            );
                        }
                    }
                }
            }
            return selected;
        }

        private boolean hasStartableProducer(K material, Map<K, Long> bootstrapSupply) {
            return graph.producersOf(material).stream().anyMatch(operation ->
                ECOPlannerMath.positiveNet(operation, material) > 0L
                    && ECOCycleBootstrap.canPotentiallyStart(operation, bootstrapSupply, Map.of()));
        }

        private List<Long> branchIncrements(
            ECOPlanningOperation<K, R> producer,
            long minimum,
            int producerCount,
            Map<K, Long> bootstrapSupply
        ) {
            Set<Long> increments = new java.util.LinkedHashSet<>();
            increments.add(minimum);
            long stateCapacity = ECOPlannerMath.immediatelySupportedStateBatches(
                producer,
                bootstrapSupply
            );
            if (stateCapacity > 0L && stateCapacity < Long.MAX_VALUE) {
                increments.add(Math.min(minimum, stateCapacity));
            }
            long immediate = immediatelyExecutable(producer, bootstrapSupply);
            if (immediate > 0L) {
                increments.add(Math.min(minimum, immediate));
            }
            if (producerCount > 1) {
                increments.add(ECOPlannerMath.ceilDiv(minimum, producerCount));
            }
            int splits = Math.max(2, Math.min(8, Math.max(producerCount, budget.extraBatchChoices() + 1)));
            for (int divisor = 2; divisor <= splits; divisor++) {
                increments.add(ECOPlannerMath.ceilDiv(minimum, divisor));
            }
            increments.add(1L);
            for (int extra = 1; extra <= budget.extraBatchChoices(); extra++) {
                try {
                    increments.add(Math.addExact(minimum, extra));
                } catch (ArithmeticException ignored) {
                    overflowBranches++;
                    break;
                }
            }
            increments.removeIf(value -> value <= 0L);
            return List.copyOf(increments);
        }

        private long immediatelyExecutable(
            ECOPlanningOperation<K, R> operation,
            Map<K, Long> bootstrapSupply
        ) {
            long stateCapacity = ECOPlannerMath.immediatelySupportedStateBatches(operation, bootstrapSupply);
            if (stateCapacity != Long.MAX_VALUE) {
                return stateCapacity;
            }
            long result = Long.MAX_VALUE;
            for (var input : operation.inputs().entrySet()) {
                result = Math.min(result,
                    bootstrapSupply.getOrDefault(input.getKey(), 0L) / input.getValue());
            }
            return operation.inputs().isEmpty() ? Long.MAX_VALUE : result;
        }

        private long stateAwareMinimum(
            ECOPlanningOperation<K, R> operation,
            long minimum,
            Map<K, Long> balances
        ) {
            long capacity = ECOPlannerMath.immediatelySupportedStateBatches(operation, balances);
            if (capacity == Long.MAX_VALUE) {
                return minimum;
            }
            return capacity > 0L ? Math.min(minimum, capacity) : 1L;
        }

        private int statePriority(
            ECOPlanningOperation<K, R> operation,
            Map<K, Long> balances
        ) {
            if (operation.stateTransitionInputs().isEmpty()) {
                return 2;
            }
            return ECOPlannerMath.immediatelySupportedStateBatches(operation, balances) > 0L ? 0 : 1;
        }

        private Map<K, Long> buildBootstrapSupply(long[] counts) {
            Map<K, Long> supply = new LinkedHashMap<>(problem.inventory());
            for (int index = 0; index < operations.size(); index++) {
                long count = counts[index];
                if (count <= 0L) {
                    continue;
                }
                var operation = operations.get(index);
                for (var output : operation.outputs().entrySet()) {
                    long input = operation.inputAmount(output.getKey());
                    long perBatch = input == 0L
                        ? output.getValue()
                        : Math.max(0L, output.getValue() - input);
                    if (perBatch == 0L) {
                        continue;
                    }
                    if (!mergeScaled(supply, output.getKey(), perBatch, count)) {
                        overflowBranches++;
                        return null;
                    }
                }
            }
            return supply;
        }

        private boolean shouldStop() {
            return ECOSolveBudget.shouldStop(deadlineNanos);
        }

        private boolean hasPositiveProducer(K material) {
            return expandableMaterials.contains(material);
        }

        private boolean containsProductiveSelfCycle(ECOPlanCandidate<R> candidate) {
            return operations.stream().anyMatch(operation -> {
                if (candidate.executions().getOrDefault(operation.reference(), 0L) <= 0L) {
                    return false;
                }
                return operation.inputs().keySet().stream().anyMatch(material ->
                    operation.outputs().containsKey(material)
                        && ECOPlannerMath.positiveNet(operation, material) > 0L);
            });
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

    private record Evaluation<K, R>(
        Map<K, Long> balances,
        Map<K, Long> bootstrapSupply,
        ECOPlanCandidate<R> candidate
    ) {
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
