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
    private ECOInventoryScheduler() {
    }

    public static <K, R> ECOInventorySchedule<K, R> schedule(
        ECOPlanningProblem<K, R> problem,
        ECOPlanCandidate<R> candidate
    ) {
        Map<K, Long> inventory = ECOPlanningBalances.copyInventory(problem);
        Map<R, Long> remaining = new LinkedHashMap<>(candidate.executions());
        List<ECOScheduledStep<R>> steps = new ArrayList<>();
        Set<R> cycleOperations = cycleOperations(problem);

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
                apply(problem, operation.inputs(), inventory, executable, false);
                apply(problem, operation.outputs(), inventory, executable, true);
                long left = pending - executable;
                remaining.put(operation.reference(), left);
                steps.add(new ECOScheduledStep<>(operation.reference(), executable));
                progress = true;
                if (left > 0L) enqueue(operation, pendingOperations, queued, cycleOperations);
                for (K output : operation.outputs().keySet()) {
                    for (var consumer : consumers.getOrDefault(output, List.of())) {
                        if (remaining.getOrDefault(consumer.reference(), 0L) > 0L) {
                            enqueue(consumer, pendingOperations, queued, cycleOperations);
                        }
                    }
                }
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
            if (cycleOperations.contains(operation.reference())) {
                pendingOperations.addFirst(operation);
            } else {
                pendingOperations.addLast(operation);
            }
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
            if (operation.selectableOutputs().stream().anyMatch(cycleMaterials::contains)) {
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
