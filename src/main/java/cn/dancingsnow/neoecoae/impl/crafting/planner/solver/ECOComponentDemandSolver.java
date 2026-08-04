package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOGraphPruner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;
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
        return trySolve(problem, ECOGraphPruner.targetReachable(problem), ECOSolveBudget.DEFAULT.deadlineNanos());
    }

    public static <K, R> Optional<ECOHyperflowResult<R>> trySolve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph
    ) {
        return trySolve(problem, graph, ECOSolveBudget.DEFAULT.deadlineNanos());
    }

    public static <K, R> Optional<ECOHyperflowResult<R>> trySolve(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningGraph<K, R> graph,
        long deadlineNanos
    ) {
        if (ECOSolveBudget.shouldStop(deadlineNanos)) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.COMPONENT_SOLVER,
                ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED,
                problem.requested().keySet().stream().findFirst().orElse(null),
                problem.requested().values().stream().findFirst().orElse(0L),
                "component",
                "deadline_before_start"
            );
            return Optional.empty();
        }
        Map<K, Long> balances = ECOPlannerMath.initialBalances(problem);
        Map<R, Long> executions = new LinkedHashMap<>();
        Set<K> expandableMaterials = findExpandableMaterials(graph);
        ArrayDeque<K> queue = new ArrayDeque<>();
        Set<K> queued = new HashSet<>();
        problem.requested().keySet().forEach(key -> enqueueIfDeficient(key, balances, graph, queue, queued));

        long expansions = 0;
        boolean arithmeticSaturated = false;
        long maxExpansions = Math.min(1_000_000L,
            Math.max(64L, (long) graph.materials().size() * 8L + graph.operations().size() * 4L));
        try {
            while (!queue.isEmpty()) {
                if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                    ECOPlanningFailureDiagnostics.logFailure(
                        ECOPlanningFailureDiagnostics.Stage.COMPONENT_SOLVER,
                        ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED,
                        materialKey(problem),
                        0L,
                        "component",
                        "termination=" + (Thread.currentThread().isInterrupted() ? "interrupted" : "deadline")
                            + " expansions=" + expansions + " maxExpansions=" + maxExpansions
                            + " queueSize=" + queue.size()
                    );
                    return Optional.empty();
                }
                if (++expansions > maxExpansions) {
                    ECOPlanningFailureDiagnostics.logFailure(
                        ECOPlanningFailureDiagnostics.Stage.COMPONENT_SOLVER,
                        ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED,
                        materialKey(problem),
                        0L,
                        "component",
                        "termination=component_limit expansions=" + expansions
                            + " maxExpansions=" + maxExpansions
                            + " queueSize=" + queue.size()
                    );
                    return Optional.empty();
                }
                K material = queue.removeFirst();
                queued.remove(material);
                long deficit = ECOPlannerMath.saturatedNegate(
                    balances.getOrDefault(material, 0L)
                );
                if (deficit <= 0) {
                    continue;
                }
                List<ECOPlanningOperation<K, R>> producers = graph.producersOf(material);
                ProducerChoice<K, R> choice = chooseProducer(
                    material,
                    deficit,
                    producers,
                    balances,
                    problem.requested(),
                    graph,
                    expandableMaterials,
                    deadlineNanos
                );
                if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                    return Optional.empty();
                }
                if (choice == null) {
                    ECOPlanningFailureDiagnostics.logFailure(
                        ECOPlanningFailureDiagnostics.Stage.COMPONENT_SOLVER,
                        ECOPlannerFallbackReason.SOLVER_NO_ROUTE,
                        material,
                        deficit,
                        "component",
                        "no_startable_producer producerCount=" + producers.size()
                            + " expansions=" + expansions
                    );
                    continue;
                }
                ECOPlanningOperation<K, R> producer = choice.operation();
                long net = producer.netOutput(material);
                if (net <= 0) {
                    ECOPlanningFailureDiagnostics.logFailure(
                        ECOPlanningFailureDiagnostics.Stage.COMPONENT_SOLVER,
                        ECOPlannerFallbackReason.SOLVER_NO_ROUTE,
                        material,
                        deficit,
                        "component",
                        "non_positive_net_output operation=" + producer.reference()
                    );
                    continue;
                }
                long bootstrapDeficit = ECOCycleBootstrap.bootstrapDeficit(
                    material, producers, balances, problem.requested()
                );
                long demand = bootstrapDeficit > 0L ? bootstrapDeficit : deficit;
                long batches = ECOPlannerMath.ceilDiv(demand, net);
                long stateCapacity = ECOPlannerMath.immediatelySupportedStateBatches(producer, balances);
                if (!producer.stateTransitionInputs().isEmpty()) {
                    if (stateCapacity > 0L) {
                        batches = Math.min(batches, stateCapacity);
                    } else if (!canExposeStateDependency(producer, balances, graph)) {
                        continue;
                    } else {
                        // One batch makes the missing state visible to the queue. A second batch
                        // would incorrectly assume that the next state already exists.
                        batches = 1L;
                    }
                } else if (choice.inventoryBackedCapacity() > 0L
                    && choice.inventoryBackedCapacity() != Long.MAX_VALUE) {
                    // Commit only the part this route can currently support. The remaining
                    // material deficit is requeued so another producer can consume its own
                    // finite source inventory without invoking integer search.
                    batches = Math.min(batches, choice.inventoryBackedCapacity());
                }
                if (batches <= 0L) {
                    continue;
                }
                long plannedBatches = batches;
                long previousExecutions = executions.getOrDefault(producer.reference(), 0L);
                long updatedExecutions = ECOPlannerMath.saturatedAdd(previousExecutions, plannedBatches);
                arithmeticSaturated |= updatedExecutions == Long.MAX_VALUE
                    && previousExecutions != Long.MAX_VALUE
                    && plannedBatches != Long.MAX_VALUE;
                executions.put(producer.reference(), updatedExecutions);
                for (var input : producer.inputs().entrySet()) {
                    K key = input.getKey();
                    long delta = ECOPlannerMath.saturatedMultiply(input.getValue(), -plannedBatches);
                    long previous = balances.getOrDefault(key, 0L);
                    long updated = ECOPlannerMath.saturatedAdd(previous, delta);
                    arithmeticSaturated |= delta == Long.MIN_VALUE || updated == Long.MIN_VALUE;
                    balances.put(key, updated);
                    enqueueIfDeficient(key, balances, graph, queue, queued);
                }
                for (var output : producer.outputs().entrySet()) {
                    K key = output.getKey();
                    long delta = ECOPlannerMath.saturatedMultiply(output.getValue(), plannedBatches);
                    long previous = balances.getOrDefault(key, 0L);
                    long updated = ECOPlannerMath.saturatedAdd(previous, delta);
                    arithmeticSaturated |= delta == Long.MAX_VALUE || updated == Long.MAX_VALUE;
                    balances.put(key, updated);
                    enqueueIfDeficient(key, balances, graph, queue, queued);
                }
            }
        } catch (ArithmeticException overflow) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.COMPONENT_SOLVER,
                ECOPlannerFallbackReason.PLANNING_FAILURE,
                materialKey(problem),
                0L,
                "component",
                "arithmetic_overflow expansions=" + expansions,
                overflow
            );
            return Optional.empty();
        }

        if (ECOSolveBudget.shouldStop(deadlineNanos)) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.COMPONENT_SOLVER,
                ECOPlannerFallbackReason.SOLVER_BUDGET_EXHAUSTED,
                materialKey(problem),
                0L,
                "component",
                "deadline_after_propagation expansions=" + expansions
            );
            return Optional.empty();
        }

        ECOHyperflowResult<R> result = ECOPlannerMath.buildResult(
            balances,
            executions,
            problem.requested(),
            expandableMaterials,
            graph.materials(),
            expansions
        );
        if (arithmeticSaturated && result.status() == ECOHyperflowResult.Status.COMPLETE) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.COMPONENT_SOLVER,
                ECOPlannerFallbackReason.PLANNING_FAILURE,
                materialKey(problem),
                0L,
                "component",
                "complete_result_after_arithmetic_saturation expansions=" + expansions
            );
            return Optional.empty();
        }
        return Optional.of(result);
    }

    private static <K, R> ProducerChoice<K, R> chooseProducer(
        K material,
        long deficit,
        List<ECOPlanningOperation<K, R>> producers,
        Map<K, Long> balances,
        Map<K, Long> requested,
        ECOPlanningGraph<K, R> graph,
        Set<K> expandableMaterials,
        long deadlineNanos
    ) {
        // bootstrapDeficit does not depend on the current producer — compute once outside the loop.
        long bootstrapDeficit = ECOCycleBootstrap.bootstrapDeficit(material, producers, balances, requested);
        ECOPlanningOperation<K, R> best = null;
        long bestInventoryCapacity = 0L;
        long bestScore = Long.MAX_VALUE;
        int bestPriority = Integer.MAX_VALUE;
        for (var operation : producers) {
            if (ECOSolveBudget.shouldStop(deadlineNanos)) {
                return null;
            }
            if (!ECOCycleBootstrap.canPotentiallyStart(operation, balances, requested)) {
                continue;
            }
            long stateCapacity = ECOPlannerMath.immediatelySupportedStateBatches(operation, balances);
            if (!operation.stateTransitionInputs().isEmpty()
                && stateCapacity <= 0L
                && !canExposeStateDependency(operation, balances, graph)) {
                continue;
            }
            long net = ECOPlannerMath.positiveNet(operation, material);
            if (net <= 0) {
                continue;
            }
            long demand = bootstrapDeficit > 0L ? bootstrapDeficit : deficit;
            long batches = ECOPlannerMath.ceilDiv(demand, net);
            long inventoryCapacity = inventoryBackedOperationCapacity(
                operation, balances, graph, new HashSet<>(), 0
            );
            if (!operation.stateTransitionInputs().isEmpty()) {
                batches = stateCapacity > 0L ? Math.min(batches, stateCapacity) : 1L;
            } else if (inventoryCapacity > 0L && inventoryCapacity != Long.MAX_VALUE) {
                batches = Math.min(batches, inventoryCapacity);
            }
            long score = 0;
            for (var input : operation.inputs().entrySet()) {
                long available = Math.max(0L, balances.getOrDefault(input.getKey(), 0L));
                long required;
                try {
                    required = Math.multiplyExact(input.getValue(), batches);
                } catch (ArithmeticException ignored) {
                    required = Long.MAX_VALUE;
                }
                long missing = required <= available ? 0L : required - available;
                if (operation.outputs().containsKey(input.getKey())) {
                    long bootstrapMissing = ECOCycleBootstrap.missingBootstrapAmount(
                        operation, input.getKey(), missing, balances, requested
                    );
                    if (bootstrapMissing > 0L) {
                        score = ECOPlannerMath.saturatedAdd(score, ECOCycleBootstrap.bootstrapPenalty());
                        missing = bootstrapMissing;
                    } else {
                        missing = 0L;
                    }
                }
                if (missing > 0) {
                    score = ECOPlannerMath.saturatedAdd(
                        score,
                        expandableMaterials.contains(input.getKey())
                            ? ECOPlannerMath.saturatedMultiply(missing, 4L)
                            : 1_000_000L
                    );
                }
            }
            // Prefer fewer local steps and better output density after dependency cost.
            score = ECOPlannerMath.saturatedAdd(score, operation.inputs().size() * 16L);
            score = ECOPlannerMath.saturatedAdd(score, Math.max(0L, 1_000L / Math.min(net, 1_000L)));
            score = ECOPlannerMath.saturatedAdd(score, Math.max(0L, batches - 1L));
            int priority = operation.stateTransitionInputs().isEmpty()
                ? inventoryCapacity > 0L ? 0 : 2
                : stateCapacity > 0L ? 0 : 1;
            if (priority < bestPriority || (priority == bestPriority && score < bestScore)) {
                best = operation;
                bestScore = score;
                bestPriority = priority;
                bestInventoryCapacity = inventoryCapacity;
            }
        }
        return best == null ? null : new ProducerChoice<>(best, bestInventoryCapacity);
    }

    /**
     * Conservative capacity of an operation from the balances currently left in the network.
     * For alternative producers we use the best single route, not their sum, so shared terminal
     * sources are never counted twice. Requeuing exposes the next route after this capacity is
     * committed.
     */
    private static <K, R> long inventoryBackedOperationCapacity(
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> balances,
        ECOPlanningGraph<K, R> graph,
        Set<K> visiting,
        int depth
    ) {
        if (operation.inputs().isEmpty()) {
            return Long.MAX_VALUE;
        }
        long capacity = Long.MAX_VALUE;
        boolean constrained = false;
        for (var input : operation.inputs().entrySet()) {
            if (operation.outputs().getOrDefault(input.getKey(), 0L) >= input.getValue()) {
                continue;
            }
            long available = inventoryBackedMaterialAmount(
                input.getKey(), balances, graph, visiting, depth + 1
            );
            if (available == Long.MAX_VALUE) {
                continue;
            }
            constrained = true;
            capacity = Math.min(capacity, available / input.getValue());
        }
        return constrained ? capacity : Long.MAX_VALUE;
    }

    private static <K, R> long inventoryBackedMaterialAmount(
        K material,
        Map<K, Long> balances,
        ECOPlanningGraph<K, R> graph,
        Set<K> visiting,
        int depth
    ) {
        long balance = balances.getOrDefault(material, 0L);
        if (depth > graph.materials().size() || !visiting.add(material)) {
            return Math.max(0L, balance);
        }
        long bestProduced = 0L;
        try {
            for (var producer : graph.producersOf(material)) {
                long net = ECOPlannerMath.positiveNet(producer, material);
                if (net <= 0L) {
                    continue;
                }
                long batches = inventoryBackedOperationCapacity(
                    producer, balances, graph, visiting, depth + 1
                );
                if (batches == Long.MAX_VALUE) {
                    return Long.MAX_VALUE;
                }
                bestProduced = Math.max(
                    bestProduced,
                    ECOPlannerMath.saturatedMultiply(net, batches)
                );
            }
        } finally {
            visiting.remove(material);
        }
        return Math.max(0L, ECOPlannerMath.saturatedAdd(balance, bestProduced));
    }

    private record ProducerChoice<K, R>(
        ECOPlanningOperation<K, R> operation,
        long inventoryBackedCapacity
    ) {
    }

    private static <K, R> boolean canExposeStateDependency(
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> balances,
        ECOPlanningGraph<K, R> graph
    ) {
        for (K key : operation.stateTransitionInputs()) {
            long required = operation.inputAmount(key);
            long available = balances.getOrDefault(key, 0L);
            if (available >= required) {
                continue;
            }
            if (available < 0L && graph.producersOf(key).isEmpty()) {
                return false;
            }
        }
        return true;
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

    private static <K, R> Set<K> findExpandableMaterials(ECOPlanningGraph<K, R> graph) {
        Set<K> expandable = new HashSet<>();
        for (var operation : graph.operations()) {
            for (K output : operation.selectableOutputs()) {
                if (ECOPlannerMath.positiveNet(operation, output) > 0) {
                    expandable.add(output);
                }
            }
        }
        return expandable;
    }

    private static <K, R> K materialKey(ECOPlanningProblem<K, R> problem) {
        return problem.requested().keySet().stream().findFirst().orElse(null);
    }
}
