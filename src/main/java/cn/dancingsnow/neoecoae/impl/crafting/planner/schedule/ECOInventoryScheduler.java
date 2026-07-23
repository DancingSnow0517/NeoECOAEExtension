package cn.dancingsnow.neoecoae.impl.crafting.planner.schedule;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
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
        return new ECOInventorySchedule<>(blockedBy.isEmpty(), steps, inventory, blockedBy);
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
            result = Math.min(result, inventory.getOrDefault(input.getKey(), 0L) / input.getValue());
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
