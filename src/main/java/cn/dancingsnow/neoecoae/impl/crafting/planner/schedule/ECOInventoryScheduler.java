package cn.dancingsnow.neoecoae.impl.crafting.planner.schedule;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
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
    private ECOInventoryScheduler() {
    }

    public static <K, R> ECOInventorySchedule<K, R> schedule(
        ECOPlanningProblem<K, R> problem,
        ECOPlanCandidate<R> candidate
    ) {
        Map<K, Long> inventory = new LinkedHashMap<>(problem.inventory());
        Map<R, Long> remaining = new LinkedHashMap<>(candidate.executions());
        List<ECOScheduledStep<R>> steps = new ArrayList<>();

        Map<K, List<ECOPlanningOperation<K, R>>> consumers = new LinkedHashMap<>();
        for (var operation : problem.operations()) {
            for (K input : operation.inputs().keySet()) {
                consumers.computeIfAbsent(input, ignored -> new ArrayList<>()).add(operation);
            }
        }

        // Outputs wake only the operations that consume them. This keeps a long, reverse-ordered
        // dependency chain linear instead of rescanning every operation once per dependency level.
        ArrayDeque<ECOPlanningOperation<K, R>> pendingOperations = new ArrayDeque<>();
        Set<R> queued = new HashSet<>();
        for (var operation : problem.operations()) {
            pendingOperations.addLast(operation);
            queued.add(operation.reference());
        }
        while (!pendingOperations.isEmpty()) {
            ECOPlanningOperation<K, R> operation = pendingOperations.removeFirst();
            queued.remove(operation.reference());
            long pending = remaining.getOrDefault(operation.reference(), 0L);
            if (pending <= 0) {
                continue;
            }
            long executable = maxExecutable(operation, inventory, pending);
            if (executable <= 0) {
                continue;
            }
            apply(operation.inputs(), inventory, executable, false);
            apply(operation.outputs(), inventory, executable, true);
            long left = pending - executable;
            remaining.put(operation.reference(), left);
            steps.add(new ECOScheduledStep<>(operation.reference(), executable));

            if (left > 0) {
                enqueue(operation, pendingOperations, queued);
            }
            for (K output : operation.outputs().keySet()) {
                for (var consumer : consumers.getOrDefault(output, List.of())) {
                    if (remaining.getOrDefault(consumer.reference(), 0L) > 0) {
                        enqueue(consumer, pendingOperations, queued);
                    }
                }
            }
        }

        Map<K, Long> blockedBy = new LinkedHashMap<>();
        for (ECOPlanningOperation<K, R> operation : problem.operations()) {
            if (remaining.getOrDefault(operation.reference(), 0L) <= 0) {
                continue;
            }
            operation.inputs().forEach((key, amount) -> {
                long missing = amount - inventory.getOrDefault(key, 0L);
                if (missing > 0) blockedBy.merge(key, missing, Math::max);
            });
        }
        if (remaining.values().stream().noneMatch(value -> value > 0)) {
            problem.requested().forEach((key, amount) -> {
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
        Map<K, Long> inventory = new LinkedHashMap<>(problem.inventory());
        Map<K, Long> synthetic = new LinkedHashMap<>();
        for (var operation : problem.operations()) {
            if (candidate.executions().getOrDefault(operation.reference(), 0L) <= 0L) {
                continue;
            }
            for (var input : operation.inputs().entrySet()) {
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
            new ECOPlanningProblem<>(problem.operations(), inventory, problem.requested()), candidate
        );
        return new ECOInventorySchedule<>(
            scheduled.executable(), scheduled.steps(), scheduled.remainingInventory(),
            scheduled.blockedBy(), synthetic
        );
    }

    private static <K, R> void enqueue(
        ECOPlanningOperation<K, R> operation,
        ArrayDeque<ECOPlanningOperation<K, R>> pendingOperations,
        Set<R> queued
    ) {
        if (queued.add(operation.reference())) {
            pendingOperations.addLast(operation);
        }
    }

    private static <K, R> long maxExecutable(
        ECOPlanningOperation<K, R> operation,
        Map<K, Long> inventory,
        long pending
    ) {
        long result = pending;
        for (var input : operation.inputs().entrySet()) {
            long available = inventory.getOrDefault(input.getKey(), 0L);
            long output = operation.outputs().getOrDefault(input.getKey(), 0L);
            if (output >= input.getValue()) {
                if (available < input.getValue()) {
                    return 0L;
                }
                continue;
            }
            result = Math.min(result, available / input.getValue());
        }
        return result;
    }

    private static <K> void apply(
        Map<K, Long> amounts,
        Map<K, Long> inventory,
        long batches,
        boolean add
    ) {
        amounts.forEach((key, amount) -> {
            long delta = Math.multiplyExact(amount, batches);
            inventory.merge(key, add ? delta : -delta, Math::addExact);
        });
    }
}
