package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.schedule.ECOInventoryScheduler;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bounded integer search over the target-reachable crafting hypergraph.
 *
 * <p>Counts are sparse and material balances are updated in-place. Failed bounded branching is
 * deliberately classified as {@link ECOHyperflowResult.Status#BUDGET_EXHAUSTED}; NO_ROUTE is
 * reserved for frontiers with no startable positive producer.</p>
 */
public final class ECOIntegerHyperflowSolver {
    private ECOIntegerHyperflowSolver() {
    }

    public static <K, R> ECOHyperflowResult<R> solve(
        ECOPlanningProblem<K, R> problem,
        ECOSolveBudget budget
    ) {
        long deadlineNanos = budget.deadlineNanos();
        return solve(problem, ECOGraphPruner.targetReachable(problem), budget, deadlineNanos);
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
        return new Search<>(problem, graph, budget, deadlineNanos).run();
    }

    private static final class Search<K, R> {
        private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
        private static final int MAX_RECURSION_DEPTH = 768;

        private final ECOPlanningProblem<K, R> problem;
        private final ECOPlanningGraph<K, R> graph;
        private final ECOSolveBudget budget;
        private final long deadlineNanos;
        private final List<ECOPlanningOperation<K, R>> operations;
        private final Map<ECOPlanningOperation<K, R>, Integer> operationIndices = new HashMap<>();
        private final Set<K> expandableMaterials = new HashSet<>();
        private final Set<K> requestedMaterials = new HashSet<>();
        private final Map<K, Long> balances = new LinkedHashMap<>();
        private final Map<K, Long> bootstrapSupply = new LinkedHashMap<>();
        private final Set<K> requestedDeficiencies = new LinkedHashSet<>();
        private final Set<K> dependencyDeficiencies = new LinkedHashSet<>();
        private final NavigableMap<Integer, Long> counts = new TreeMap<>();
        private final Set<SparseCountVector> visited = new HashSet<>();

        private BigInteger requestedShortfall = BigInteger.ZERO;
        private BigInteger dependencyShortfall = BigInteger.ZERO;
        private BigInteger sourceShortfall = BigInteger.ZERO;
        private BigInteger surplus = BigInteger.ZERO;
        private BigInteger totalExecutions = BigInteger.ZERO;
        private Best<R> best;
        private Feasible<R> feasibleMissing;
        private ECOHyperflowResult<R> solution;
        private long expandedStates;
        private boolean exhausted;
        private boolean boundedBranching;

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
            this.requestedMaterials.addAll(problem.requested().keySet());

            Set<K> relevant = new LinkedHashSet<>(graph.materials());
            relevant.addAll(problem.requested().keySet());
            for (int index = 0; index < operations.size(); index++) {
                ECOPlanningOperation<K, R> operation = operations.get(index);
                operationIndices.put(operation, index);
                for (K output : operation.selectableOutputs()) {
                    if (ECOPlannerMath.positiveNet(operation, output) > 0L) {
                        expandableMaterials.add(output);
                    }
                }
            }
            for (K material : relevant) {
                balances.put(material, problem.inventory().getOrDefault(material, 0L));
                bootstrapSupply.put(material, problem.inventory().getOrDefault(material, 0L));
            }
            problem.requested().forEach((key, amount) ->
                balances.put(key, Math.subtractExact(balances.getOrDefault(key, 0L), amount)));
            for (var balance : balances.entrySet()) {
                addContribution(balance.getKey(), balance.getValue());
            }
        }

        private ECOHyperflowResult<R> run() {
            explore(0);
            if (solution != null) {
                return solution;
            }
            if (feasibleMissing != null && !exhausted && !boundedBranching) {
                return feasibleMissing.result();
            }
            if (best == null) {
                ECOPlanCandidate<R> empty = new ECOPlanCandidate<>(
                    Map.of(),
                    toLong(requestedShortfall),
                    toLong(dependencyShortfall),
                    toLong(sourceShortfall),
                    toLong(surplus)
                );
                best = new Best<>(score(), empty);
            }
            ECOHyperflowResult.Status status = exhausted || boundedBranching
                ? ECOHyperflowResult.Status.BUDGET_EXHAUSTED
                : ECOHyperflowResult.Status.NO_ROUTE;
            return new ECOHyperflowResult<>(status, best.candidate(), expandedStates);
        }

        private void explore(int depth) {
            if (solution != null) {
                return;
            }
            if (shouldStop()
                || expandedStates >= budget.maxExpandedStates()
                || depth >= Math.min(budget.maxDepth(), MAX_RECURSION_DEPTH)) {
                exhausted = true;
                return;
            }
            if (!visited.add(new SparseCountVector(counts))) {
                return;
            }
            expandedStates++;

            Score currentScore = score();
            if (best == null || currentScore.compareTo(best.score()) < 0) {
                best = new Best<>(currentScore, candidate());
            }

            if (requestedDeficiencies.isEmpty() && dependencyDeficiencies.isEmpty()) {
                ECOPlanCandidate<R> candidate = candidate();
                var schedule = ECOInventoryScheduler.scheduleWithSyntheticSources(
                    problem,
                    candidate,
                    deadlineNanos,
                    Math.max(1L, Math.min(50_000L, budget.maxExpandedStates()))
                );
                if (schedule.executable()) {
                    if (sourceShortfall.signum() == 0 && schedule.syntheticSources().isEmpty()) {
                        solution = new ECOHyperflowResult<>(
                            ECOHyperflowResult.Status.COMPLETE, candidate, expandedStates
                        );
                    } else {
                        BigInteger syntheticTotal = schedule.syntheticSources().values().stream()
                            .map(BigInteger::valueOf)
                            .reduce(BigInteger.ZERO, BigInteger::add);
                        if (feasibleMissing == null
                            || syntheticTotal.compareTo(feasibleMissing.syntheticTotal()) < 0) {
                            feasibleMissing = new Feasible<>(
                                new ECOHyperflowResult<>(
                                    ECOHyperflowResult.Status.MISSING_SOURCES, candidate, expandedStates
                                ),
                                syntheticTotal
                            );
                        }
                    }
                } else if (schedule.budgetExhausted()) {
                    exhausted = true;
                }
                return;
            }

            DeficiencyChoice<K, R> deficiencyChoice = chooseStartableDeficiency();
            if (deficiencyChoice == null) {
                return;
            }
            Deficiency<K> deficiency = deficiencyChoice.deficiency();
            List<ECOPlanningOperation<K, R>> producers = deficiencyChoice.producers();
            producers.sort(Comparator.comparingLong(
                operation -> -ECOPlannerMath.positiveNet(operation, deficiency.material())
            ));

            long bootstrapDeficit = ECOCycleBootstrap.bootstrapDeficit(
                deficiency.material(), producers, bootstrapSupply
            );
            boolean severalProducers = producers.size() > 1;
            if (severalProducers) {
                boundedBranching = true;
                // First try a complete route through every producer. This reaches the common
                // "one usable recipe among several" case before exploring mixture splits.
                for (ECOPlanningOperation<K, R> producer : producers) {
                    if (solution != null) {
                        return;
                    }
                    if (shouldStop()) {
                        exhausted = true;
                        return;
                    }
                    if (!ECOCycleBootstrap.canPotentiallyStart(producer, bootstrapSupply)) {
                        continue;
                    }
                    exploreBranch(
                        producer,
                        minimumIncrement(deficiency, producer, bootstrapDeficit),
                        depth
                    );
                }
            }
            for (ECOPlanningOperation<K, R> producer : producers) {
                if (solution != null) {
                    return;
                }
                if (shouldStop()) {
                    exhausted = true;
                    return;
                }
                if (!ECOCycleBootstrap.canPotentiallyStart(producer, bootstrapSupply)) {
                    continue;
                }
                long minimum = minimumIncrement(deficiency, producer, bootstrapDeficit);
                if (!severalProducers) {
                    // With one positive producer, the minimum batch count is forced. Splitting or
                    // overproducing it only recreates the same count vector through deeper states.
                    exploreBranch(producer, minimum, depth);
                    continue;
                }
                for (long increment : branchIncrements(producer, minimum, producers.size())) {
                    if (increment == minimum) {
                        continue;
                    }
                    exploreBranch(producer, increment, depth);
                }
            }
        }

        private long minimumIncrement(
            Deficiency<K> deficiency,
            ECOPlanningOperation<K, R> producer,
            long bootstrapDeficit
        ) {
            long demand = bootstrapDeficit > 0L
                ? Math.min(deficiency.amount(), bootstrapDeficit)
                : deficiency.amount();
            return ECOPlannerMath.ceilDiv(
                demand, ECOPlannerMath.positiveNet(producer, deficiency.material())
            );
        }

        private void exploreBranch(
            ECOPlanningOperation<K, R> producer,
            long increment,
            int depth
        ) {
            Integer operationIndex = operationIndices.get(producer);
            if (operationIndex == null || !apply(operationIndex, producer, increment)) {
                exhausted = true;
                return;
            }
            explore(depth + 1);
            undo(operationIndex, producer, increment);
        }

        private List<Long> branchIncrements(
            ECOPlanningOperation<K, R> producer,
            long minimum,
            int producerCount
        ) {
            Set<Long> increments = new LinkedHashSet<>();
            // Try the complete route through this producer before bounded mixture splits.
            increments.add(minimum);
            long supported = supportedBatches(producer, minimum, new HashSet<>(), 0);
            if (supported > 0L) {
                increments.add(Math.min(minimum, supported));
            }
            long immediate = immediatelyExecutable(producer);
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
                    exhausted = true;
                    break;
                }
            }
            increments.removeIf(value -> value <= 0L);
            return List.copyOf(increments);
        }

        private long immediatelyExecutable(ECOPlanningOperation<K, R> operation) {
            if (operation.inputs().isEmpty()) {
                return Long.MAX_VALUE;
            }
            long result = Long.MAX_VALUE;
            for (var input : operation.inputs().entrySet()) {
                long available = Math.max(0L, balances.getOrDefault(input.getKey(), 0L));
                result = Math.min(result, available / input.getValue());
            }
            return result;
        }

        private long supportedBatches(
            ECOPlanningOperation<K, R> operation,
            long maximum,
            Set<K> visiting,
            int depth
        ) {
            if (operation.inputs().isEmpty()) {
                return maximum;
            }
            long result = maximum;
            for (var input : operation.inputs().entrySet()) {
                long needed = ECOPlannerMath.saturatedMultiply(input.getValue(), result);
                long supply = potentialSupply(input.getKey(), needed, visiting, depth + 1);
                result = Math.min(result, supply / input.getValue());
                if (result == 0L) {
                    break;
                }
            }
            return result;
        }

        private long potentialSupply(K material, long needed, Set<K> visiting, int depth) {
            long available = Math.max(0L, balances.getOrDefault(material, 0L));
            if (available >= needed || depth > 12 || !expandableMaterials.contains(material) || !visiting.add(material)) {
                return available;
            }
            long bestProduced = 0L;
            for (ECOPlanningOperation<K, R> producer : graph.producersOf(material)) {
                long net = ECOPlannerMath.positiveNet(producer, material);
                if (net <= 0L || !ECOCycleBootstrap.canPotentiallyStart(producer, bootstrapSupply)) {
                    continue;
                }
                long missing = Math.max(0L, needed - available);
                long desiredBatches = ECOPlannerMath.ceilDiv(missing, net);
                long batches = supportedBatches(producer, desiredBatches, visiting, depth + 1);
                bestProduced = Math.max(bestProduced, ECOPlannerMath.saturatedMultiply(net, batches));
            }
            visiting.remove(material);
            return ECOPlannerMath.saturatedAdd(available, bestProduced);
        }

        private boolean apply(
            int operationIndex,
            ECOPlanningOperation<K, R> operation,
            long increment
        ) {
            long oldCount = counts.getOrDefault(operationIndex, 0L);
            long newCount;
            try {
                newCount = Math.addExact(oldCount, increment);
            } catch (ArithmeticException overflow) {
                return false;
            }
            Map<K, Long> previous = touchedBalances(operation);
            Map<K, Long> previousBootstrap = touchedBootstrapSupply(operation);
            try {
                for (var input : operation.inputs().entrySet()) {
                    addBalance(input.getKey(), Math.multiplyExact(input.getValue(), -increment));
                }
                for (var output : operation.outputs().entrySet()) {
                    addBalance(output.getKey(), Math.multiplyExact(output.getValue(), increment));
                }
                ECOCycleBootstrap.addPlannedProduction(operation, increment, bootstrapSupply);
            } catch (ArithmeticException overflow) {
                restoreBalances(previous);
                restoreBootstrapSupply(previousBootstrap);
                return false;
            }
            counts.put(operationIndex, newCount);
            totalExecutions = totalExecutions.add(BigInteger.valueOf(increment));
            return true;
        }

        private void undo(
            int operationIndex,
            ECOPlanningOperation<K, R> operation,
            long increment
        ) {
            long count = counts.get(operationIndex);
            long restoredCount = count - increment;
            if (restoredCount == 0L) {
                counts.remove(operationIndex);
            } else {
                counts.put(operationIndex, restoredCount);
            }
            Map<K, Long> previous = touchedBalances(operation);
            Map<K, Long> previousBootstrap = touchedBootstrapSupply(operation);
            try {
                ECOCycleBootstrap.removePlannedProduction(operation, increment, bootstrapSupply);
                for (var output : operation.outputs().entrySet()) {
                    addBalance(output.getKey(), Math.multiplyExact(output.getValue(), -increment));
                }
                for (var input : operation.inputs().entrySet()) {
                    addBalance(input.getKey(), Math.multiplyExact(input.getValue(), increment));
                }
            } catch (ArithmeticException impossible) {
                restoreBalances(previous);
                restoreBootstrapSupply(previousBootstrap);
                throw new IllegalStateException("Failed to restore an ECO search branch", impossible);
            }
            totalExecutions = totalExecutions.subtract(BigInteger.valueOf(increment));
        }

        private Map<K, Long> touchedBalances(ECOPlanningOperation<K, R> operation) {
            Map<K, Long> result = new LinkedHashMap<>();
            operation.inputs().keySet().forEach(key -> result.put(key, balances.getOrDefault(key, 0L)));
            operation.outputs().keySet().forEach(key -> result.putIfAbsent(key, balances.getOrDefault(key, 0L)));
            return result;
        }

        private void restoreBalances(Map<K, Long> previous) {
            previous.forEach(this::setBalance);
        }

        private Map<K, Long> touchedBootstrapSupply(ECOPlanningOperation<K, R> operation) {
            Map<K, Long> result = new LinkedHashMap<>();
            operation.outputs().keySet().forEach(key ->
                result.put(key, bootstrapSupply.getOrDefault(key, 0L)));
            return result;
        }

        private void restoreBootstrapSupply(Map<K, Long> previous) {
            previous.forEach(bootstrapSupply::put);
        }

        private void addBalance(K material, long delta) {
            setBalance(material, Math.addExact(balances.getOrDefault(material, 0L), delta));
        }

        private void setBalance(K material, long value) {
            long oldValue = balances.getOrDefault(material, 0L);
            removeContribution(material, oldValue);
            balances.put(material, value);
            addContribution(material, value);
        }

        private void addContribution(K material, long balance) {
            BigInteger amount = magnitude(balance);
            if (balance < 0L) {
                if (!expandableMaterials.contains(material)) {
                    sourceShortfall = sourceShortfall.add(amount);
                } else if (requestedMaterials.contains(material)) {
                    requestedShortfall = requestedShortfall.add(amount);
                    requestedDeficiencies.add(material);
                } else {
                    dependencyShortfall = dependencyShortfall.add(amount);
                    dependencyDeficiencies.add(material);
                }
            } else if (balance > 0L) {
                surplus = surplus.add(amount);
            }
        }

        private void removeContribution(K material, long balance) {
            BigInteger amount = magnitude(balance);
            if (balance < 0L) {
                if (!expandableMaterials.contains(material)) {
                    sourceShortfall = sourceShortfall.subtract(amount);
                } else if (requestedMaterials.contains(material)) {
                    requestedShortfall = requestedShortfall.subtract(amount);
                    requestedDeficiencies.remove(material);
                } else {
                    dependencyShortfall = dependencyShortfall.subtract(amount);
                    dependencyDeficiencies.remove(material);
                }
            } else if (balance > 0L) {
                surplus = surplus.subtract(amount);
            }
        }

        private DeficiencyChoice<K, R> chooseStartableDeficiency() {
            DeficiencyChoice<K, R> selected = largestStartableDeficiency(requestedDeficiencies);
            return selected != null ? selected : largestStartableDeficiency(dependencyDeficiencies);
        }

        private DeficiencyChoice<K, R> largestStartableDeficiency(Set<K> deficiencies) {
            K selectedMaterial = null;
            long selectedAmount = 0L;
            for (K material : deficiencies) {
                long amount = ECOPlannerMath.saturatedNegate(balances.getOrDefault(material, 0L));
                if (amount <= selectedAmount) {
                    continue;
                }
                boolean startable = false;
                for (var operation : graph.producersOf(material)) {
                    if (ECOPlannerMath.positiveNet(operation, material) > 0L
                        && ECOCycleBootstrap.canPotentiallyStart(operation, bootstrapSupply)) {
                        startable = true;
                        break;
                    }
                }
                if (startable) {
                    selectedMaterial = material;
                    selectedAmount = amount;
                }
            }
            if (selectedMaterial == null) {
                return null;
            }
            List<ECOPlanningOperation<K, R>> producers = new ArrayList<>();
            for (var operation : graph.producersOf(selectedMaterial)) {
                if (ECOPlannerMath.positiveNet(operation, selectedMaterial) > 0L) {
                    producers.add(operation);
                }
            }
            return new DeficiencyChoice<>(new Deficiency<>(selectedMaterial, selectedAmount), producers);
        }

        private Score score() {
            return new Score(
                requestedShortfall,
                dependencyShortfall,
                sourceShortfall,
                totalExecutions,
                surplus,
                counts.size()
            );
        }

        private ECOPlanCandidate<R> candidate() {
            Map<R, Long> executions = new LinkedHashMap<>();
            counts.forEach((index, count) -> executions.put(operations.get(index).reference(), count));
            return new ECOPlanCandidate<>(
                executions,
                toLong(requestedShortfall),
                toLong(dependencyShortfall),
                toLong(sourceShortfall),
                toLong(surplus)
            );
        }

        private boolean shouldStop() {
            return ECOSolveBudget.shouldStop(deadlineNanos);
        }

        private static BigInteger magnitude(long value) {
            return BigInteger.valueOf(value).abs();
        }

        private static long toLong(BigInteger value) {
            return value.compareTo(LONG_MAX) >= 0 ? Long.MAX_VALUE : value.longValue();
        }
    }

    private record Deficiency<K>(K material, long amount) {
    }

    private record DeficiencyChoice<K, R>(
        Deficiency<K> deficiency,
        List<ECOPlanningOperation<K, R>> producers
    ) {
    }

    private record Best<R>(Score score, ECOPlanCandidate<R> candidate) {
    }

    private record Feasible<R>(ECOHyperflowResult<R> result, BigInteger syntheticTotal) {
    }

    private record Score(
        BigInteger requestedShortfall,
        BigInteger dependencyShortfall,
        BigInteger sourceShortfall,
        BigInteger totalExecutions,
        BigInteger surplus,
        int operationCount
    ) implements Comparable<Score> {
        @Override
        public int compareTo(Score other) {
            int result = requestedShortfall.compareTo(other.requestedShortfall);
            if (result == 0) result = dependencyShortfall.compareTo(other.dependencyShortfall);
            if (result == 0) result = sourceShortfall.compareTo(other.sourceShortfall);
            if (result == 0) result = totalExecutions.compareTo(other.totalExecutions);
            if (result == 0) result = surplus.compareTo(other.surplus);
            if (result == 0) result = Integer.compare(operationCount, other.operationCount);
            return result;
        }
    }

    private static final class SparseCountVector {
        private final int[] indices;
        private final long[] values;
        private final int hash;

        private SparseCountVector(NavigableMap<Integer, Long> counts) {
            this.indices = new int[counts.size()];
            this.values = new long[counts.size()];
            int cursor = 0;
            for (var entry : counts.entrySet()) {
                indices[cursor] = entry.getKey();
                values[cursor] = entry.getValue();
                cursor++;
            }
            this.hash = 31 * Arrays.hashCode(indices) + Arrays.hashCode(values);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SparseCountVector vector
                && Arrays.equals(indices, vector.indices)
                && Arrays.equals(values, vector.values);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
