package cn.dancingsnow.neoecoae.impl.crafting.planner.schedule;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningBalances;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOPlanningGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.ECOStrongComponents;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlannerFallbackReason;
import cn.dancingsnow.neoecoae.impl.crafting.planner.service.ECOPlanningFailureDiagnostics;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates that integer operation counts have an inventory-enabled execution order. */
public final class ECOInventoryScheduler {
    private static final int MAX_SCHEDULE_STEPS = 16_384;
    private static final int MAX_SCHEDULE_STATES = 65_536;

    private ECOInventoryScheduler() {
    }

    public static <K, R> ECOInventorySchedule<K, R> schedule(
        ECOPlanningProblem<K, R> problem,
        ECOPlanCandidate<R> candidate
    ) {
        return schedule(
            problem,
            candidate,
            new ECOPlanningGraph<K, R>(problem.operations()).topology(problem.unlimitedInventory())
        );
    }

    /** Reuses a graph topology that was already built by the solver for this planning request. */
    public static <K, R> ECOInventorySchedule<K, R> schedule(
        ECOPlanningProblem<K, R> problem,
        ECOPlanCandidate<R> candidate,
        ECOPlanningGraph<K, R> graph
    ) {
        return schedule(problem, candidate, graph.topology(problem.unlimitedInventory()));
    }

    /** Reuses an immutable SCC index without reconstructing a graph or running SCC again. */
    public static <K, R> ECOInventorySchedule<K, R> schedule(
        ECOPlanningProblem<K, R> problem,
        ECOPlanCandidate<R> candidate,
        ECOStrongComponents.Topology<K, R> topology
    ) {
        Map<K, Long> inventory = ECOPlanningBalances.copyInventory(problem);
        Map<R, Long> remaining = new LinkedHashMap<>(candidate.executions());
        List<ECOScheduleEntry<R>> steps = new ArrayList<>();
        Set<R> cycleOperations = topology.cyclicOperationReferences();
        Set<SchedulerState<K, R>> visitedStates = new HashSet<>();
        visitedStates.add(state(problem, remaining, inventory));
        boolean stateLimitReached = false;
        Map<R, ECOPlanningOperation<K, R>> byReference = new LinkedHashMap<>();
        problem.operations().forEach(operation -> byReference.put(operation.reference(), operation));

        Map<K, List<ECOPlanningOperation<K, R>>> consumers = new LinkedHashMap<>();
        for (var operation : problem.operations()) {
            for (K input : operation.inputs().keySet()) {
                consumers.computeIfAbsent(input, ignored -> new ArrayList<>()).add(operation);
            }
        }

        boolean progress;
        do {
            progress = false;
            ArrayDeque<ECOPlanningOperation<K, R>> pendingOperations = new ArrayDeque<>();
            Set<R> queued = new HashSet<>();
            for (var operation : problem.operations()) {
                if (cycleOperations.contains(operation.reference())) {
                    pendingOperations.addLast(operation);
                    queued.add(operation.reference());
                }
            }
            for (var operation : problem.operations()) {
                if (!cycleOperations.contains(operation.reference())) {
                    pendingOperations.addLast(operation);
                    queued.add(operation.reference());
                }
            }
            while (!pendingOperations.isEmpty()) {
                ECOPlanningOperation<K, R> operation = pendingOperations.removeFirst();
                queued.remove(operation.reference());
                long pending = remaining.getOrDefault(operation.reference(), 0L);
                if (pending <= 0L) continue;
                boolean cyclic = cycleOperations.contains(operation.reference());
                logOperationClassification(operation, cyclic, topology);
                long executable = maxExecutable(problem, operation, inventory, pending, "scheduler");
                long maxExecutable = executable;
                if (executable <= 0L) {
                    logBatchDecision(
                        problem, operation, topology, cyclic, pending,
                        maxExecutable, null, null, null, 0L, inventory, remaining, cycleOperations
                    );
                    continue;
                }
                if (cyclic) {
                    CompressionResult<K, R> compression = compressedExecutable(
                        problem,
                        operation,
                        inventory,
                        remaining,
                        cycleOperations,
                        pending,
                        executable
                    );
                    executable = compression.selectedExecutable();
                    logBatchDecision(
                        problem, operation, topology, true, pending,
                        maxExecutable,
                        compression.seedPreserving().executable(),
                        compression.unlockingExecutable(),
                        compression.selectedExecutable(),
                        executable,
                        inventory,
                        remaining,
                        cycleOperations,
                        compression.seedPreserving().reserves()
                    );
                    if (executable <= 0L) continue;
                } else {
                    logBatchDecision(
                        problem, operation, topology, false, pending,
                        maxExecutable, null, null, null, executable,
                        inventory, remaining, cycleOperations
                    );
                }
                long pendingBefore = pending;
                Map<K, Long> inventoryBeforeExecution = snapshotInventory(problem, operation, inventory);
                apply(problem, operation.inputs(), inventory, executable, false);
                apply(problem, operation.outputs(), inventory, executable, true);
                long left = pending - executable;
                remaining.put(operation.reference(), left);
                appendStep(steps, operation.reference(), executable);
                logExecution(
                    problem, operation, topology, executable, pendingBefore, left,
                    inventoryBeforeExecution, inventory
                );
                progress = true;
                if (fastForwardRepeatedBlock(problem, byReference, remaining, inventory, steps)) {
                    if (!visitedStates.add(state(problem, remaining, inventory))) {
                        stateLimitReached = true;
                    }
                    pendingOperations.clear();
                    break;
                }
                if (visitedStates.size() >= MAX_SCHEDULE_STATES
                    || !visitedStates.add(state(problem, remaining, inventory))) {
                    stateLimitReached = true;
                    pendingOperations.clear();
                    break;
                }
                if (steps.size() >= MAX_SCHEDULE_STEPS) {
                    pendingOperations.clear();
                    break;
                }
                if (left > 0L) enqueue(operation, pendingOperations, queued, cycleOperations);
                for (K output : operation.outputs().keySet()) {
                    for (var consumer : consumers.getOrDefault(output, List.of())) {
                        if (remaining.getOrDefault(consumer.reference(), 0L) > 0L) {
                            enqueue(consumer, pendingOperations, queued, cycleOperations);
                        }
                    }
                }
            }
            if (!progress && remaining.values().stream().anyMatch(value -> value > 0L)) {
                progress = executeBootstrapStep(
                    problem, remaining, inventory, steps, cycleOperations, topology
                );
            }
            if (steps.size() >= MAX_SCHEDULE_STEPS) {
                progress = false;
            }
            if (stateLimitReached) {
                progress = false;
            }
        } while (progress && remaining.values().stream().anyMatch(value -> value > 0L));

        Map<K, Long> blockedBy = new LinkedHashMap<>();
        for (ECOPlanningOperation<K, R> operation : problem.operations()) {
            if (remaining.getOrDefault(operation.reference(), 0L) <= 0) {
                continue;
            }
            operation.inputs().forEach((key, amount) -> {
                if (problem.isUnlimited(key)) {
                    return;
                }
                long missing = amount - inventory.getOrDefault(key, 0L);
                if (missing > 0) {
                    logBlockedDetail(problem, operation, topology, key, amount, missing, inventory, remaining);
                    long previous = blockedBy.getOrDefault(key, 0L);
                    long merged = Math.max(previous, missing);
                    logSchedulerDetail(
                        "blocked_merge key=" + describe(key)
                            + " operationRef=" + describe(operation.reference())
                            + " incomingDeficit=" + missing
                            + " previous=" + previous
                            + " merged=" + merged
                    );
                    blockedBy.put(key, merged);
                }
            });
        }
        if (remaining.values().stream().noneMatch(value -> value > 0)) {
            problem.requested().forEach((key, amount) -> {
                if (problem.isUnlimited(key)) {
                    return;
                }
                long missing = amount - inventory.getOrDefault(key, 0L);
                if (missing > 0) blockedBy.put(key, missing);
            });
        }
        boolean executable = blockedBy.isEmpty();
        if (!executable) {
            ECOPlanningFailureDiagnostics.logFailure(
                ECOPlanningFailureDiagnostics.Stage.SCHEDULER,
                ECOPlannerFallbackReason.ASSEMBLY_REJECTED,
                problem.requested().keySet().stream().findFirst().orElse(null),
                problem.requested().values().stream().findFirst().orElse(0L),
                "scheduler",
                "blockedBy=" + blockedBy
                    + " steps=" + steps.size()
                    + " pendingOperations=" + remaining.values().stream().filter(value -> value > 0).count()
                    + " pendingDetail=" + describePending(problem, remaining, inventory)
                    + " blockedProducers=" + describeBlockedProducers(problem, blockedBy)
            );
        }
        if (ECOPlanningFailureDiagnostics.canLogDetail(
            ECOPlanningFailureDiagnostics.Stage.SCHEDULER
        )) {
            ECOPlanningFailureDiagnostics.logDetail(
                ECOPlanningFailureDiagnostics.Stage.SCHEDULER,
            "scheduler_result sequenceSchedulable=" + executable
                + " steps=" + steps.size()
                + " stepSample=" + ECOPlanningFailureDiagnostics.describeIterable(steps, steps.size())
                + " blockedBy=" + ECOPlanningFailureDiagnostics.describeMap(blockedBy)
                + " remainingInventory=" + ECOPlanningFailureDiagnostics.describeMap(inventory)
            );
        }
        return new ECOInventorySchedule<>(executable, steps, inventory, blockedBy);
    }

    /** Compatibility scheduling mode that supplies only the initial seed for a self-growing input. */
    public static <K, R> ECOInventorySchedule<K, R> scheduleWithSyntheticSources(
        ECOPlanningProblem<K, R> problem,
        ECOPlanCandidate<R> candidate,
        long deadlineNanos,
        long maxExpandedStates
    ) {
        Map<K, Long> inventory = ECOPlanningBalances.copyInventory(problem);
        Map<K, Long> synthetic = new LinkedHashMap<>();
        for (var operation : problem.operations()) {
            if (candidate.executions().getOrDefault(operation.reference(), 0L) <= 0L) {
                continue;
            }
            for (var input : operation.inputs().entrySet()) {
                if (problem.isUnlimited(input.getKey())) {
                    continue;
                }
                long output = operation.outputs().getOrDefault(input.getKey(), 0L);
                if (output < input.getValue()) {
                    continue;
                }
                long available = inventory.getOrDefault(input.getKey(), 0L);
                long seed = Math.max(0L, input.getValue() - available);
                if (seed > 0L) {
                    inventory.merge(input.getKey(), seed, Math::addExact);
                    synthetic.merge(input.getKey(), seed, Math::addExact);
                }
            }
        }
        ECOInventorySchedule<K, R> scheduled = schedule(
            new ECOPlanningProblem<>(
                problem.operations(), inventory, problem.requested(), problem.unlimitedInventory()
            ), candidate
        );
        return new ECOInventorySchedule<>(
            scheduled.executable(), scheduled.steps(), scheduled.remainingInventory(),
            scheduled.blockedBy(), synthetic
        );
    }

    private static <K, R> void enqueue(
        ECOPlanningOperation<K, R> operation,
        ArrayDeque<ECOPlanningOperation<K, R>> pendingOperations,
        Set<R> queued,
        Set<R> cycleOperations
    ) {
        if (queued.add(operation.reference())) {
            // Preserve producer/consumer discovery order. Adding every cyclic consumer at the
            // front reverses siblings, which can run a destructive consumer before the producer
            // that restores the shared cycle seed.
            pendingOperations.addLast(operation);
        }
    }

    private static <K, R> long maxExecutable(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> inventory,
        long pending,
        String evaluation
    ) {
        long result = pending;
        List<MaxInput<K>> inputTraces = new ArrayList<>();
        for (var input : operation.inputs().entrySet()) {
            long available = ECOPlanningBalances.available(problem, inventory, input.getKey());
            long output = operation.outputs().getOrDefault(input.getKey(), 0L);
            long candidateLimit = available / input.getValue();
            boolean stateTransition = operation.stateTransitionInputs().contains(input.getKey());
            MaxInput<K> inputTrace = new MaxInput<>(
                input.getKey(), available, input.getValue(), output,
                stateTransition, candidateLimit
            );
            inputTraces.add(inputTrace);
            if (!operation.stateTransitionInputs().contains(input.getKey()) && output >= input.getValue()) {
                if (available < input.getValue()) {
                    for (MaxInput<K> trace : inputTraces) {
                        logMaxExecutable(operation, pending, evaluation, trace, 0L);
                    }
                    return 0L;
                }
                continue;
            }
            result = Math.min(result, available / input.getValue());
        }
        for (MaxInput<K> trace : inputTraces) {
            logMaxExecutable(operation, pending, evaluation, trace, result);
        }
        if (operation.inputs().isEmpty()) {
            if (isDiagnosticOperation(operation, pending)) {
                logSchedulerDetail(
                        "scheduler_max_executable operationRef=" + describe(operation.reference())
                        + " evaluation=" + evaluation
                        + " pending=" + pending + " finalMaxExecutable=" + result
                );
            }
        }
        return result;
    }

    /**
     * Selects a maximum safe compressed batch from the current balance breakpoints. Candidates
     * represent operation completion, another operation becoming startable, and a material
     * reaching its reserve boundary. No one-batch stepping is needed to cross a stable interval.
     */
    private static <K, R> CompressionResult<K, R> compressedExecutable(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> inventory,
        Map<R, Long> remaining,
        Set<R> cycleOperations,
        long pending,
        long executable
    ) {
        if (executable <= 0L) {
            return new CompressionResult<>(
                0L, new SeedPreservingResult<>(0L, Map.of()), null
            );
        }
        SeedPreservingResult<K, R> seedPreserving = seedPreservingExecutable(
            problem, operation, inventory, remaining, cycleOperations, executable
        );
        // A zero result means this operation would consume a seed reserved for another
        // pending cycle operation. There is no safe compressed batch in that state; treating
        // zero as "no breakpoint" lets an external consumer exhaust the only bootstrap seed.
        if (seedPreserving.executable() == 0L) {
            return new CompressionResult<>(0L, seedPreserving, null);
        }
        long unlocking = unlockingExecutable(problem, operation, inventory, remaining, executable);
        Set<Long> breakpoints = new LinkedHashSet<>();
        breakpoints.add(executable);
        breakpoints.add(Math.min(pending, executable));
        if (seedPreserving.executable() > 0L) {
            breakpoints.add(seedPreserving.executable());
        }
        if (unlocking > 0L) {
            breakpoints.add(unlocking);
        }

        long selected = 0L;
        for (long breakpoint : breakpoints) {
            if (breakpoint <= 0L || breakpoint > executable) {
                continue;
            }
            if (seedPreserving.executable() > 0L && breakpoint > seedPreserving.executable()) {
                continue;
            }
            if (seedPreserving.executable() == 0L && unlocking > 0L && breakpoint != unlocking) {
                continue;
            }
            selected = Math.max(selected, breakpoint);
        }
        return new CompressionResult<>(selected, seedPreserving, unlocking);
    }

    private static <K, R> SchedulerState<K, R> state(
        ECOPlanningProblem<K, R> problem,
        Map<R, Long> remaining,
        Map<K, Long> inventory
    ) {
        Map<R, Long> pending = new LinkedHashMap<>();
        remaining.forEach((reference, batches) -> {
            if (batches > 0L) {
                pending.put(reference, batches);
            }
        });
        Set<K> relevantMaterials = new LinkedHashSet<>(problem.requested().keySet());
        problem.operations().forEach(operation -> {
            relevantMaterials.addAll(operation.inputs().keySet());
            relevantMaterials.addAll(operation.outputs().keySet());
        });
        Map<K, Long> relevantInventory = new LinkedHashMap<>();
        relevantMaterials.forEach(material -> {
            if (!problem.isUnlimited(material)) {
                relevantInventory.put(material, inventory.getOrDefault(material, 0L));
            }
        });
        return new SchedulerState<>(Map.copyOf(pending), Map.copyOf(relevantInventory));
    }

    /**
     * Keeps the smallest input seed required by another pending operation in the same cycle.
     * Re-evaluating the reserve after the batch is reduced reaches a stable cap without
     * expanding large cyclic plans one craft at a time.
     */
    private static <K, R> SeedPreservingResult<K, R> seedPreservingExecutable(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> inventory,
        Map<R, Long> remaining,
        Set<R> cycleOperations,
        long executable
    ) {
        long result = executable;
        Map<K, SeedReserve<R>> reserves = new LinkedHashMap<>();
        for (int pass = 0; pass < 2; pass++) {
            long previous = result;
            for (var input : operation.inputs().entrySet()) {
                if (problem.isUnlimited(input.getKey())
                    || operation.outputs().getOrDefault(input.getKey(), 0L) >= input.getValue()) {
                    continue;
                }
                SeedReserve<R> seed = minimumPendingSeed(
                    problem, input.getKey(), operation.reference(), result, remaining, cycleOperations
                );
                reserves.put(input.getKey(), seed);
                long available = inventory.getOrDefault(input.getKey(), 0L);
                long resulting = available <= seed.amount()
                    ? 0L
                    : Math.min(result, (available - seed.amount()) / input.getValue());
                logSchedulerDetail(
                    "seed_preserve operationRef=" + describe(operation.reference())
                        + " material=" + describe(input.getKey())
                        + " currentExecutable=" + result
                        + " pass=" + pass
                        + " available=" + available
                        + " inputPerBatch=" + input.getValue()
                        + " reserve=" + seed.amount()
                        + " resultingExecutable=" + resulting
                );
                if (available <= seed.amount()) {
                    result = 0L;
                    break;
                }
                result = resulting;
            }
            if (result == previous) {
                break;
            }
        }
        return new SeedPreservingResult<>(result, Map.copyOf(reserves));
    }

    private static <K, R> SeedReserve<R> minimumPendingSeed(
        ECOPlanningProblem<K, R> problem,
        K material,
        R current,
        long currentBatch,
        Map<R, Long> remaining,
        Set<R> cycleOperations
    ) {
        long reserve = Long.MAX_VALUE;
        R selectedFromOperation = null;
        List<R> selectedConsumers = new ArrayList<>();
        boolean sawCyclicCandidate = false;
        boolean sawPendingCyclicCandidate = false;
        boolean sawInputCandidate = false;
        boolean sawCurrentConsumed = false;
        for (var candidate : problem.operations()) {
            long rawPending = remaining.getOrDefault(candidate.reference(), 0L);
            boolean candidateCyclic = cycleOperations.contains(candidate.reference());
            sawCyclicCandidate |= candidateCyclic;
            long candidatePending = rawPending;
            String reason;
            if (!candidateCyclic) {
                reason = "candidate_not_in_cycleOperations";
                logSeedCandidate(
                    current, material, candidate, rawPending, candidatePending,
                    candidateCyclic, candidate.inputs().getOrDefault(material, 0L), false, reason
                );
                continue;
            }
            if (candidate.reference().equals(current)) {
                candidatePending -= currentBatch;
            }
            if (candidatePending <= 0L) {
                reason = rawPending <= 0L ? "candidate_pending_zero" : "current_operation_consumed";
                sawCurrentConsumed |= candidate.reference().equals(current) && rawPending > 0L;
                logSeedCandidate(
                    current, material, candidate, rawPending, candidatePending,
                    candidateCyclic, candidate.inputs().getOrDefault(material, 0L), false, reason
                );
                continue;
            }
            sawPendingCyclicCandidate = true;
            long input = candidate.inputs().getOrDefault(material, 0L);
            if (input > 0L) {
                sawInputCandidate = true;
                reason = "considered";
                boolean considered = true;
                logSeedCandidate(
                    current, material, candidate, rawPending, candidatePending,
                    candidateCyclic, input, considered, reason
                );
                if (input < reserve) {
                    selectedConsumers.clear();
                    selectedConsumers.add(candidate.reference());
                    selectedFromOperation = candidate.reference();
                    reserve = Math.min(reserve, input);
                } else if (input == reserve) {
                    selectedConsumers.add(candidate.reference());
                }
            } else {
                reason = "consumer_input_absent";
                logSeedCandidate(
                    current, material, candidate, rawPending, candidatePending,
                    candidateCyclic, input, false, reason
                );
            }
        }
        long existingMinimumReserve = reserve == Long.MAX_VALUE ? 0L : reserve;
        long currentPendingAfter = remaining.getOrDefault(current, 0L) - currentBatch;
        long currentNextBatchReserve = currentPendingAfter > 0L
            ? problem.operations().stream()
                .filter(candidate -> candidate.reference().equals(current))
                .mapToLong(candidate -> candidate.inputs().getOrDefault(material, 0L))
                .max()
                .orElse(0L)
            : 0L;
        long effectiveReserve = Math.max(existingMinimumReserve, currentNextBatchReserve);
        logSchedulerDetail(
            "minimum_pending_seed currentOperation=" + describe(current)
                + " material=" + describe(material)
                + " existingMinimumReserve=" + existingMinimumReserve
                + " currentNextBatchReserve=" + currentNextBatchReserve
                + " effectiveReserve=" + effectiveReserve
                + " reserve=" + effectiveReserve
                + " effectiveReserveSource=" + reserveSource(
                    existingMinimumReserve, currentNextBatchReserve
                )
                + " selectedFromOperation=" + describe(selectedFromOperation)
                + " reason=" + (currentNextBatchReserve > existingMinimumReserve
                    ? "current_operation_floor"
                    : effectiveReserve > 0L
                        ? "selected_minimum"
                    : sawPendingCyclicCandidate && !sawInputCandidate
                        ? "consumer_input_absent"
                        : sawCurrentConsumed
                            ? "current_operation_consumed"
                            : sawCyclicCandidate
                                ? "consumer_pending_zero"
                                : "no_pending_cyclic_consumer")
        );
        return new SeedReserve<>(
            effectiveReserve,
            selectedFromOperation,
            List.copyOf(selectedConsumers),
            existingMinimumReserve,
            currentNextBatchReserve
        );
    }

    private static <K, R> boolean executeBootstrapStep(
        ECOPlanningProblem<K, R> problem,
        Map<R, Long> remaining,
        Map<K, Long> inventory,
        List<ECOScheduleEntry<R>> steps,
        Set<R> cycleOperations,
        ECOStrongComponents.Topology<K, R> topology
    ) {
        if (steps.size() >= MAX_SCHEDULE_STEPS) {
            return false;
        }
        for (var operation : problem.operations()) {
            long pending = remaining.getOrDefault(operation.reference(), 0L);
            if (pending <= 0L || !cycleOperations.contains(operation.reference())) {
                continue;
            }
            long executable = maxExecutable(problem, operation, inventory, pending, "bootstrap");
            long unlocking = unlockingExecutable(problem, operation, inventory, remaining, executable);
            if (unlocking <= 0L) {
                continue;
            }
            logOperationClassification(operation, true, topology);
            logBatchDecision(
                problem, operation, topology, true, pending,
                executable, null, unlocking, null, unlocking,
                inventory, remaining, cycleOperations
            );
            Map<K, Long> inventoryBeforeExecution = snapshotInventory(problem, operation, inventory);
            apply(problem, operation.inputs(), inventory, unlocking, false);
            apply(problem, operation.outputs(), inventory, unlocking, true);
            long left = pending - unlocking;
            remaining.put(operation.reference(), left);
            appendStep(steps, operation.reference(), unlocking);
            logExecution(
                problem, operation, topology, unlocking, pending, left,
                inventoryBeforeExecution, inventory
            );
            return true;
        }
        return false;
    }

    /** Returns the smallest executable batch that makes a currently blocked operation runnable. */
    private static <K, R> long unlockingExecutable(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningOperation<K, R> producer,
        Map<K, Long> inventory,
        Map<R, Long> remaining,
        long executable
    ) {
        if (executable <= 0L) {
            return 0L;
        }
        long result = Long.MAX_VALUE;
        for (var consumer : problem.operations()) {
            if (remaining.getOrDefault(consumer.reference(), 0L) <= 0L
                || maxExecutable(problem, consumer, inventory,
                    remaining.getOrDefault(consumer.reference(), 0L), "unlocking_probe") > 0L) {
                continue;
            }
            for (var output : producer.outputs().entrySet()) {
                long required = consumer.inputs().getOrDefault(output.getKey(), 0L);
                if (required <= 0L || problem.isUnlimited(output.getKey())) {
                    continue;
                }
                long missing = required - inventory.getOrDefault(output.getKey(), 0L);
                if (missing > 0L) {
                    long batches = 1L + (missing - 1L) / output.getValue();
                    if (batches <= executable) {
                        result = Math.min(result, batches);
                    }
                }
            }
        }
        return result == Long.MAX_VALUE ? 0L : result;
    }

    private static <R> void appendStep(List<ECOScheduleEntry<R>> steps, R operation, long batches) {
        if (!steps.isEmpty() && steps.getLast() instanceof ECOScheduledStep<R> previous) {
            if (previous.operation().equals(operation)) {
                long recorded = Math.addExact(previous.batches(), batches);
                steps.set(steps.size() - 1, new ECOScheduledStep<>(operation, recorded));
                if (batches > 1_000_000L || recorded > 1_000_000L) {
                    logSchedulerDetail(
                        "scheduler_step_record operationRef=" + describe(operation)
                            + " appendedBatches=" + batches
                            + " recordedStepBatches=" + recorded
                            + " merged=true"
                    );
                }
                return;
            }
        }
        steps.add(new ECOScheduledStep<>(operation, batches));
        if (batches > 1_000_000L) {
            logSchedulerDetail(
                "scheduler_step_record operationRef=" + describe(operation)
                    + " appendedBatches=" + batches
                    + " recordedStepBatches=" + batches
                    + " merged=false"
            );
        }
    }

    private static <K, R> boolean fastForwardRepeatedBlock(
        ECOPlanningProblem<K, R> problem,
        Map<R, ECOPlanningOperation<K, R>> byReference,
        Map<R, Long> remaining,
        Map<K, Long> inventory,
        List<ECOScheduleEntry<R>> entries
    ) {
        int maximumBody = Math.min(16, entries.size() / 2);
        for (int bodySize = 1; bodySize <= maximumBody; bodySize++) {
            int first = entries.size() - bodySize * 2;
            int second = entries.size() - bodySize;
            List<ECOScheduledStep<R>> body = new ArrayList<>(bodySize);
            boolean repeated = true;
            for (int offset = 0; offset < bodySize; offset++) {
                if (!(entries.get(first + offset) instanceof ECOScheduledStep<R> left)
                    || !(entries.get(second + offset) instanceof ECOScheduledStep<R> right)
                    || !left.equals(right)) {
                    repeated = false;
                    break;
                }
                body.add(right);
            }
            if (!repeated) {
                continue;
            }

            Map<R, Long> bodyExecutions = new LinkedHashMap<>();
            Map<K, Long> delta = new LinkedHashMap<>();
            Map<K, Long> prefixMinimum = new LinkedHashMap<>();
            for (var step : body) {
                bodyExecutions.merge(step.operation(), step.batches(), Math::addExact);
                var operation = byReference.get(step.operation());
                if (operation == null) {
                    repeated = false;
                    break;
                }
                accumulateDelta(operation.inputs(), delta, prefixMinimum, step.batches(), false);
                accumulateDelta(operation.outputs(), delta, prefixMinimum, step.batches(), true);
            }
            if (!repeated) {
                continue;
            }

            long repetitions = Long.MAX_VALUE;
            for (var execution : bodyExecutions.entrySet()) {
                repetitions = Math.min(repetitions,
                    remaining.getOrDefault(execution.getKey(), 0L) / execution.getValue());
            }
            for (var material : delta.entrySet()) {
                if (problem.isUnlimited(material.getKey())) {
                    continue;
                }
                long minimum = prefixMinimum.getOrDefault(material.getKey(), 0L);
                long availableAtMinimum = inventory.getOrDefault(material.getKey(), 0L) + minimum;
                if (availableAtMinimum < 0L) {
                    repetitions = 0L;
                    break;
                }
                if (material.getValue() < 0L) {
                    repetitions = Math.min(repetitions,
                        1L + availableAtMinimum / -material.getValue());
                }
            }
            if (repetitions <= 1L || repetitions == Long.MAX_VALUE) {
                continue;
            }

            long repetitionsToAppend = repetitions;
            Map<K, Long> inventoryBefore = new LinkedHashMap<>();
            delta.keySet().forEach(material -> {
                if (isDiagnosticMaterial(material)) {
                    inventoryBefore.put(material, ECOPlanningBalances.available(problem, inventory, material));
                }
            });
            bodyExecutions.forEach((reference, count) -> remaining.merge(
                reference, -Math.multiplyExact(count, repetitionsToAppend), Math::addExact
            ));
            delta.forEach((material, amount) -> ECOPlanningBalances.merge(
                problem, inventory, material, Math.multiplyExact(amount, repetitionsToAppend)
            ));
            inventoryBefore.forEach((material, before) -> logSchedulerDetail(
                "scheduler_fast_forward bodyOperations="
                    + ECOPlanningFailureDiagnostics.describeMap(bodyExecutions)
                    + " repeatCount=" + repetitionsToAppend
                    + " material=" + describe(material)
                    + " inventoryBefore=" + before
                    + " prefixMinimum=" + prefixMinimum.getOrDefault(material, 0L)
                    + " delta=" + delta.getOrDefault(material, 0L)
                    + " inventoryAfter=" + ECOPlanningBalances.available(problem, inventory, material)
            ));
            entries.add(new ECORepeatedBlock<>(body, repetitionsToAppend));
            return true;
        }
        return false;
    }

    private static <K> void accumulateDelta(
        Map<K, Long> amounts,
        Map<K, Long> delta,
        Map<K, Long> prefixMinimum,
        long batches,
        boolean add
    ) {
        amounts.forEach((material, amount) -> {
            long scaled = Math.multiplyExact(amount, batches);
            long updated = Math.addExact(delta.getOrDefault(material, 0L), add ? scaled : -scaled);
            delta.put(material, updated);
            prefixMinimum.merge(material, updated, Math::min);
        });
    }

    private static <K, R> void logOperationClassification(
        ECOPlanningOperation<K, R> operation,
        boolean cyclic,
        ECOStrongComponents.Topology<K, R> topology
    ) {
        if (!isDiagnosticOperation(operation, 0L)) {
            return;
        }
        logSchedulerDetail(
            "scheduler_operation_classification operationRef=" + describe(operation.reference())
                + " cyclic=" + cyclic
                + " owningComponent=" + componentId(topology.owningComponentOf(operation.reference()))
                + " localComponentMemberships=" + localComponentMemberships(topology, operation.reference())
        );
        logSchedulerDetail(
            "operation_components operationRef=" + describe(operation.reference())
                + " owningComponent=" + componentId(topology.owningComponentOf(operation.reference()))
                + " localComponents=" + localComponentMemberships(topology, operation.reference())
                + " cyclicOperationReference=" + cyclic
        );
    }

    private static <K, R> void logMaxExecutable(
        ECOPlanningOperation<K, R> operation,
        long pending,
        String evaluation,
        MaxInput<K> input,
        long finalMaxExecutable
    ) {
        if (!isDiagnosticMaterial(input.material()) && !isDiagnosticOperation(operation, pending)) {
            return;
        }
        logSchedulerDetail(
            "scheduler_max_executable operationRef=" + describe(operation.reference())
                + " evaluation=" + evaluation
                + " pending=" + pending
                + " key=" + describe(input.material())
                + " available=" + input.available()
                + " inputPerBatch=" + input.inputPerBatch()
                + " outputPerBatch=" + input.outputPerBatch()
                + " stateTransition=" + input.stateTransition()
                + " candidateLimit=" + input.candidateLimit()
                + " finalMaxExecutable=" + finalMaxExecutable
        );
    }

    private static <K, R> void logSeedCandidate(
        R current,
        K material,
        ECOPlanningOperation<K, R> candidate,
        long rawPending,
        long candidatePending,
        boolean candidateCyclic,
        long inputPerBatch,
        boolean considered,
        String reason
    ) {
        if (!isDiagnosticMaterial(material)) {
            return;
        }
        logSchedulerDetail(
            "seed_candidate currentOperation=" + describe(current)
                + " material=" + describe(material)
                + " candidateOperation=" + describe(candidate.reference())
                + " candidatePending=" + candidatePending
                + " rawPending=" + rawPending
                + " candidateCyclic=" + candidateCyclic
                + " candidateInputPerBatch=" + inputPerBatch
                + " considered=" + considered
                + " reason=" + reason
        );
    }

    private static <K, R> void logBatchDecision(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningOperation<K, R> operation,
        ECOStrongComponents.Topology<K, R> topology,
        boolean cyclic,
        long pending,
        long maxExecutable,
        Long seedPreservingExecutable,
        Long unlockingExecutable,
        Long compressedExecutable,
        long selectedExecutable,
        Map<K, Long> inventory,
        Map<R, Long> remaining,
        Set<R> cycleOperations
    ) {
        logBatchDecision(
            problem, operation, topology, cyclic, pending, maxExecutable,
            seedPreservingExecutable, unlockingExecutable, compressedExecutable,
            selectedExecutable, inventory, remaining, cycleOperations, Map.of()
        );
    }

    private static <K, R> void logBatchDecision(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningOperation<K, R> operation,
        ECOStrongComponents.Topology<K, R> topology,
        boolean cyclic,
        long pending,
        long maxExecutable,
        Long seedPreservingExecutable,
        Long unlockingExecutable,
        Long compressedExecutable,
        long selectedExecutable,
        Map<K, Long> inventory,
        Map<R, Long> remaining,
        Set<R> cycleOperations,
        Map<K, SeedReserve<R>> reserves
    ) {
        if (!isDiagnosticOperation(operation, Math.max(maxExecutable, selectedExecutable))) {
            return;
        }
        List<String> inputs = new ArrayList<>();
        operation.inputs().forEach((key, amount) -> {
            SeedReserve<R> reserve = reserves.get(key);
            inputs.add(
                "{key=" + describe(key)
                    + ",requiredPerBatch=" + amount
                    + ",availableBefore=" + ECOPlanningBalances.available(problem, inventory, key)
                    + ",outputPerBatch=" + operation.outputs().getOrDefault(key, 0L)
                    + ",unlimited=" + problem.isUnlimited(key)
                    + ",reserve=" + (reserve == null ? "not_applicable" : reserve.amount())
                    + ",existingMinimumReserve="
                    + (reserve == null ? "not_applicable" : reserve.existingMinimumReserve())
                    + ",currentNextBatchReserve="
                    + (reserve == null ? "not_applicable" : reserve.currentNextBatchReserve())
                    + ",reserveConsumers="
                    + (reserve == null ? "not_applicable" : reserve.consumers())
                    + "}"
            );
        });
        logSchedulerDetail(
            "scheduler_batch_decision operationRef=" + describe(operation.reference())
                + " cyclic=" + cyclic
                + " owningComponent=" + componentId(topology.owningComponentOf(operation.reference()))
                + " localComponentMemberships=" + localComponentMemberships(topology, operation.reference())
                + " pending=" + pending
                + " maxExecutable=" + maxExecutable
                + " seedPreservingExecutable=" + valueOrNotApplicable(seedPreservingExecutable, cyclic)
                + " unlockingExecutable=" + valueOrNotApplicable(unlockingExecutable, cyclic)
                + " compressedExecutable=" + valueOrNotApplicable(compressedExecutable, cyclic)
                + " selectedExecutable=" + selectedExecutable
                + " inputs=" + inputs
                + " cycleOperationReference=" + cycleOperations.contains(operation.reference())
                + " remainingOperationCount=" + remaining.size()
        );
    }

    private static <K, R> void logExecution(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningOperation<K, R> operation,
        ECOStrongComponents.Topology<K, R> topology,
        long batches,
        long pendingBefore,
        long pendingAfter,
        Map<K, Long> inventoryBefore,
        Map<K, Long> inventory
    ) {
        if (!isDiagnosticOperation(operation, batches)) {
            return;
        }
        List<String> inputs = new ArrayList<>();
        operation.inputs().forEach((key, amount) -> inputs.add(
            "{key=" + describe(key)
                + ",availableBefore=" + inventoryBefore.getOrDefault(key, 0L)
                + ",consume=" + ECOPlanningBalances.saturatedMultiply(amount, batches)
                + ",availableAfter=" + ECOPlanningBalances.available(problem, inventory, key) + "}"
        ));
        List<String> outputs = new ArrayList<>();
        operation.outputs().forEach((key, amount) -> outputs.add(
            "{key=" + describe(key)
                + ",produce=" + ECOPlanningBalances.saturatedMultiply(amount, batches)
                + ",availableAfter=" + ECOPlanningBalances.available(problem, inventory, key) + "}"
        ));
        logSchedulerDetail(
            "scheduler_execute operationRef=" + describe(operation.reference())
                + " batches=" + batches
                + " cyclic=" + topology.cyclicOperationReferences().contains(operation.reference())
                + " pendingBefore=" + pendingBefore
                + " pendingAfter=" + pendingAfter
                + " inputs=" + inputs
                + " outputs=" + outputs
        );
    }

    private static <K, R> Map<K, Long> snapshotInventory(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> inventory
    ) {
        Map<K, Long> result = new LinkedHashMap<>();
        operation.inputs().keySet().forEach(key ->
            result.put(key, ECOPlanningBalances.available(problem, inventory, key))
        );
        operation.outputs().keySet().forEach(key ->
            result.putIfAbsent(key, ECOPlanningBalances.available(problem, inventory, key))
        );
        return result;
    }

    private static <K, R> void logBlockedDetail(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningOperation<K, R> operation,
        ECOStrongComponents.Topology<K, R> topology,
        K key,
        long requiredPerBatch,
        long deficit,
        Map<K, Long> inventory,
        Map<R, Long> remaining
    ) {
        logSchedulerDetail(
            "scheduler_blocked_detail key=" + describe(key)
                + " requiredPerBatch=" + requiredPerBatch
                + " available=" + inventory.getOrDefault(key, 0L)
                + " deficit=" + deficit
                + " operationRef=" + describe(operation.reference())
                + " pendingExecutions=" + remaining.getOrDefault(operation.reference(), 0L)
                + " cyclic=" + topology.cyclicOperationReferences().contains(operation.reference())
                + " owningComponent=" + componentId(topology.owningComponentOf(operation.reference()))
                + " localComponentMemberships=" + localComponentMemberships(topology, operation.reference())
                + " inputs=" + ECOPlanningFailureDiagnostics.describeMap(operation.inputs())
                + " outputs=" + ECOPlanningFailureDiagnostics.describeMap(operation.outputs())
        );
    }

    private static void logSchedulerDetail(String context) {
        if (ECOPlanningFailureDiagnostics.canLogDetail(
            ECOPlanningFailureDiagnostics.Stage.SCHEDULER
        )) {
            ECOPlanningFailureDiagnostics.logDetail(
                ECOPlanningFailureDiagnostics.Stage.SCHEDULER,
                context
            );
        }
    }

    private static <K, R> boolean isDiagnosticOperation(
        ECOPlanningOperation<K, R> operation,
        long batches
    ) {
        return batches > 1_000_000L
            || operation.inputs().keySet().stream().anyMatch(ECOInventoryScheduler::isDiagnosticMaterial)
            || operation.outputs().keySet().stream().anyMatch(ECOInventoryScheduler::isDiagnosticMaterial);
    }

    private static boolean isDiagnosticMaterial(Object material) {
        String value = describe(material);
        return value.contains("data_energistics:data_crystal")
            || value.contains("data_energistics:data_dust")
            || value.contains("data_crystal")
            || value.contains("data_dust");
    }

    private static <K> String componentId(ECOStrongComponents.Component<K> component) {
        return component == null ? "none" : Integer.toString(component.id());
    }

    private static <K, R> String localComponentMemberships(
        ECOStrongComponents.Topology<K, R> topology,
        R reference
    ) {
        List<Integer> components = new ArrayList<>();
        topology.cyclicComponents().forEach(component -> {
            if (topology.localOperationsOf(component).stream()
                .anyMatch(operation -> operation.reference().equals(reference))) {
                components.add(component.id());
            }
        });
        return components.toString();
    }

    private static String valueOrNotApplicable(Long value, boolean cyclic) {
        return value == null ? (cyclic ? "not_evaluated" : "not_applicable") : value.toString();
    }

    private static String reserveSource(long existingMinimumReserve, long currentNextBatchReserve) {
        if (currentNextBatchReserve > existingMinimumReserve) {
            return "current_operation_floor";
        }
        if (existingMinimumReserve > 0L) {
            return "pending_consumer_minimum";
        }
        return "none";
    }

    private static String describe(Object value) {
        if (value == null) {
            return "none";
        }
        try {
            return value.toString();
        } catch (Throwable ignored) {
            return value.getClass().getName();
        }
    }

    private static <K, R> String describePending(
        ECOPlanningProblem<K, R> problem,
        Map<R, Long> remaining,
        Map<K, Long> inventory
    ) {
        List<String> details = new ArrayList<>();
        int total = 0;
        for (var operation : problem.operations()) {
            long batches = remaining.getOrDefault(operation.reference(), 0L);
            if (batches <= 0L) {
                continue;
            }
            total++;
            if (details.size() < 12) {
                Map<K, Long> availableInputs = new LinkedHashMap<>();
                operation.inputs().keySet().forEach(key ->
                    availableInputs.put(key, inventory.getOrDefault(key, 0L))
                );
                details.add(
                    "{operation=" + operation.reference()
                        + ",pending=" + batches
                        + ",inputs=" + ECOPlanningFailureDiagnostics.describeMap(operation.inputs())
                        + ",available=" + ECOPlanningFailureDiagnostics.describeMap(availableInputs)
                        + ",outputs=" + ECOPlanningFailureDiagnostics.describeMap(operation.outputs())
                        + ",transitionInputs=" + operation.stateTransitionInputs() + "}"
                );
            }
        }
        return ECOPlanningFailureDiagnostics.describeIterable(details, total);
    }

    private static <K, R> String describeBlockedProducers(
        ECOPlanningProblem<K, R> problem,
        Map<K, Long> blockedBy
    ) {
        List<String> details = new ArrayList<>();
        int total = 0;
        for (K material : blockedBy.keySet()) {
            for (var operation : problem.operations()) {
                if (!operation.selectableOutputs().contains(material)) {
                    continue;
                }
                total++;
                if (details.size() < 12) {
                    details.add(
                        "{material=" + material
                            + ",operation=" + operation.reference()
                            + ",inputs=" + ECOPlanningFailureDiagnostics.describeMap(operation.inputs())
                            + ",outputs=" + ECOPlanningFailureDiagnostics.describeMap(operation.outputs()) + "}"
                    );
                }
            }
        }
        return ECOPlanningFailureDiagnostics.describeIterable(details, total);
    }

    private static <K> void apply(
        ECOPlanningProblem<K, ?> problem,
        Map<K, Long> amounts,
        Map<K, Long> inventory,
        long batches,
        boolean add
    ) {
        amounts.forEach((key, amount) -> {
            long delta = ECOPlanningBalances.saturatedMultiply(amount, batches);
            ECOPlanningBalances.merge(problem, inventory, key, add ? delta : -delta);
        });
    }

    private record SchedulerState<K, R>(Map<R, Long> remaining, Map<K, Long> inventory) {
    }

    private record MaxInput<K>(
        K material,
        long available,
        long inputPerBatch,
        long outputPerBatch,
        boolean stateTransition,
        long candidateLimit
    ) {
    }

    private record SeedReserve<R>(
        long amount,
        R selectedFromOperation,
        List<R> consumers,
        long existingMinimumReserve,
        long currentNextBatchReserve
    ) {
    }

    private record SeedPreservingResult<K, R>(
        long executable,
        Map<K, SeedReserve<R>> reserves
    ) {
    }

    private record CompressionResult<K, R>(
        long selectedExecutable,
        SeedPreservingResult<K, R> seedPreserving,
        Long unlockingExecutable
    ) {
    }
}
