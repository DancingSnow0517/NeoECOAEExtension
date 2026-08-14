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
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates that integer operation counts have an inventory-enabled execution order. */
public final class ECOInventoryScheduler {
    private static final int MAX_SCHEDULE_STEPS = 16_384;

    private ECOInventoryScheduler() {
    }

    public static <K, R> ECOInventorySchedule<K, R> schedule(
        ECOPlanningProblem<K, R> problem,
        ECOPlanCandidate<R> candidate
    ) {
        Map<K, Long> inventory = ECOPlanningBalances.copyInventory(problem);
        Map<R, Long> remaining = new LinkedHashMap<>(candidate.executions());
        List<ECOScheduleEntry<R>> steps = new ArrayList<>();
        Set<R> cycleOperations = cycleOperations(problem);
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
                long executable = maxExecutable(problem, operation, inventory, pending);
                if (executable <= 0L) continue;
                if (cycleOperations.contains(operation.reference())) {
                    long seedPreserving = seedPreservingExecutable(
                        problem, operation, inventory, remaining, cycleOperations, executable
                    );
                    executable = seedPreserving > 0L
                        ? seedPreserving
                        : unlockingExecutable(problem, operation, inventory, remaining, executable);
                    if (executable <= 0L) continue;
                }
                apply(problem, operation.inputs(), inventory, executable, false);
                apply(problem, operation.outputs(), inventory, executable, true);
                long left = pending - executable;
                remaining.put(operation.reference(), left);
                appendStep(steps, operation.reference(), executable);
                progress = true;
                if (fastForwardRepeatedBlock(problem, byReference, remaining, inventory, steps)) {
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
                progress = executeBootstrapStep(problem, remaining, inventory, steps, cycleOperations);
            }
            if (steps.size() >= MAX_SCHEDULE_STEPS) {
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
                if (missing > 0) blockedBy.merge(key, missing, Math::max);
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

    private static <K, R> Set<R> cycleOperations(ECOPlanningProblem<K, R> problem) {
        ECOPlanningGraph<K, R> graph = new ECOPlanningGraph<>(problem.operations());
        Set<K> cycleMaterials = new HashSet<>();
        for (Set<K> component : ECOStrongComponents.find(graph)) {
            if (component.size() > 1 || graph.operations().stream().anyMatch(operation ->
                component.stream().anyMatch(material -> operation.inputs().containsKey(material)
                    && operation.outputs().containsKey(material)))) {
                cycleMaterials.addAll(component);
            }
        }
        Set<R> result = new HashSet<>();
        for (var operation : problem.operations()) {
            if (operation.selectableOutputs().stream().anyMatch(cycleMaterials::contains)
                || operation.inputs().keySet().stream().anyMatch(cycleMaterials::contains)) {
                result.add(operation.reference());
            }
        }
        return result;
    }

    private static <K, R> long maxExecutable(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> inventory,
        long pending
    ) {
        long result = pending;
        for (var input : operation.inputs().entrySet()) {
            long available = ECOPlanningBalances.available(problem, inventory, input.getKey());
            long output = operation.outputs().getOrDefault(input.getKey(), 0L);
            if (!operation.stateTransitionInputs().contains(input.getKey()) && output >= input.getValue()) {
                if (available < input.getValue()) {
                    return 0L;
                }
                continue;
            }
            result = Math.min(result, available / input.getValue());
        }
        return result;
    }

    /**
     * Keeps the smallest input seed required by another pending operation in the same cycle.
     * Re-evaluating the reserve after the batch is reduced reaches a stable cap without
     * expanding large cyclic plans one craft at a time.
     */
    private static <K, R> long seedPreservingExecutable(
        ECOPlanningProblem<K, R> problem,
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> inventory,
        Map<R, Long> remaining,
        Set<R> cycleOperations,
        long executable
    ) {
        long result = executable;
        for (int pass = 0; pass < 2; pass++) {
            long previous = result;
            for (var input : operation.inputs().entrySet()) {
                if (problem.isUnlimited(input.getKey())
                    || operation.outputs().getOrDefault(input.getKey(), 0L) >= input.getValue()) {
                    continue;
                }
                long reserve = minimumPendingSeed(
                    problem, input.getKey(), operation.reference(), result, remaining, cycleOperations
                );
                long available = inventory.getOrDefault(input.getKey(), 0L);
                if (available <= reserve) {
                    result = 0L;
                    break;
                }
                result = Math.min(result, (available - reserve) / input.getValue());
            }
            if (result == previous) {
                break;
            }
        }
        return result;
    }

    private static <K, R> long minimumPendingSeed(
        ECOPlanningProblem<K, R> problem,
        K material,
        R current,
        long currentBatch,
        Map<R, Long> remaining,
        Set<R> cycleOperations
    ) {
        long reserve = Long.MAX_VALUE;
        for (var candidate : problem.operations()) {
            if (!cycleOperations.contains(candidate.reference())) {
                continue;
            }
            long pending = remaining.getOrDefault(candidate.reference(), 0L);
            if (candidate.reference().equals(current)) {
                pending -= currentBatch;
            }
            if (pending > 0L) {
                long input = candidate.inputs().getOrDefault(material, 0L);
                if (input > 0L) {
                    reserve = Math.min(reserve, input);
                }
            }
        }
        return reserve == Long.MAX_VALUE ? 0L : reserve;
    }

    private static <K, R> boolean executeBootstrapStep(
        ECOPlanningProblem<K, R> problem,
        Map<R, Long> remaining,
        Map<K, Long> inventory,
        List<ECOScheduleEntry<R>> steps,
        Set<R> cycleOperations
    ) {
        if (steps.size() >= MAX_SCHEDULE_STEPS) {
            return false;
        }
        for (var operation : problem.operations()) {
            long pending = remaining.getOrDefault(operation.reference(), 0L);
            if (pending <= 0L || !cycleOperations.contains(operation.reference())) {
                continue;
            }
            long executable = maxExecutable(problem, operation, inventory, pending);
            long unlocking = unlockingExecutable(problem, operation, inventory, remaining, executable);
            if (unlocking <= 0L) {
                continue;
            }
            apply(problem, operation.inputs(), inventory, unlocking, false);
            apply(problem, operation.outputs(), inventory, unlocking, true);
            remaining.put(operation.reference(), pending - unlocking);
            appendStep(steps, operation.reference(), unlocking);
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
                    remaining.getOrDefault(consumer.reference(), 0L)) > 0L) {
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
                steps.set(steps.size() - 1, new ECOScheduledStep<>(
                    operation, Math.addExact(previous.batches(), batches)
                ));
                return;
            }
        }
        steps.add(new ECOScheduledStep<>(operation, batches));
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
            bodyExecutions.forEach((reference, count) -> remaining.merge(
                reference, -Math.multiplyExact(count, repetitionsToAppend), Math::addExact
            ));
            delta.forEach((material, amount) -> ECOPlanningBalances.merge(
                problem, inventory, material, Math.multiplyExact(amount, repetitionsToAppend)
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
}
