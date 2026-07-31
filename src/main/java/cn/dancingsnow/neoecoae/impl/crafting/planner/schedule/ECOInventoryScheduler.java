package cn.dancingsnow.neoecoae.impl.crafting.planner.schedule;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningOperation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanningProblem;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solver.ECOSolveBudget;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Finds an inventory-enabled execution order for integer operation counts. */
public final class ECOInventoryScheduler {
    private static final long DEFAULT_MAX_STATES = 50_000L;
    private static final int MAX_RECURSION_DEPTH = 768;

    private ECOInventoryScheduler() {
    }

    public static <K, R> ECOInventorySchedule<K, R> schedule(
        ECOPlanningProblem<K, R> problem,
        ECOPlanCandidate<R> candidate
    ) {
        return schedule(problem, candidate, Long.MAX_VALUE, DEFAULT_MAX_STATES);
    }

    public static <K, R> ECOInventorySchedule<K, R> schedule(
        ECOPlanningProblem<K, R> problem,
        ECOPlanCandidate<R> candidate,
        long deadlineNanos,
        long maxExpandedStates
    ) {
        return new Search<>(problem, candidate, deadlineNanos, maxExpandedStates, false).run();
    }

    /** Schedules a simulated plan while recording every uncraftable source it must synthesize. */
    public static <K, R> ECOInventorySchedule<K, R> scheduleWithSyntheticSources(
        ECOPlanningProblem<K, R> problem,
        ECOPlanCandidate<R> candidate,
        long deadlineNanos,
        long maxExpandedStates
    ) {
        return new Search<>(problem, candidate, deadlineNanos, maxExpandedStates, true).run();
    }

    private static <K, R> long positiveNet(ECOPlanningOperation<K, R> operation, K material) {
        try {
            return operation.netOutput(material);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static final class Search<K, R> {
        private final ECOPlanningProblem<K, R> problem;
        private final long deadlineNanos;
        private final long maxExpandedStates;
        private final boolean allowSyntheticSources;
        private final List<K> materials;
        private final Map<K, Integer> materialIndices;
        private final List<CompiledOperation<R>> operations;
        private final long[] requested;
        private final long[] inventory;
        private final boolean[] externalSources;
        private final long[] syntheticSources;
        private final long[] remaining;
        private final Set<StateKey> visited = new HashSet<>();
        private final List<ECOScheduledStep<R>> path = new ArrayList<>();
        private List<ECOScheduledStep<R>> solutionSteps;
        private long[] solutionInventory;
        private long[] solutionSyntheticSources;
        private long[] bestBlockedInventory;
        private long[] bestBlockedCounts;
        private long bestBlockedRemaining = Long.MAX_VALUE;
        private long expandedStates;
        private boolean budgetExhausted;

        private Search(
            ECOPlanningProblem<K, R> problem,
            ECOPlanCandidate<R> candidate,
            long deadlineNanos,
            long maxExpandedStates,
            boolean allowSyntheticSources
        ) {
            if (maxExpandedStates <= 0L) {
                throw new IllegalArgumentException("maxExpandedStates must be positive");
            }
            this.problem = problem;
            this.deadlineNanos = deadlineNanos;
            this.maxExpandedStates = maxExpandedStates;
            this.allowSyntheticSources = allowSyntheticSources;

            Map<R, ECOPlanningOperation<K, R>> byReference = new LinkedHashMap<>();
            problem.operations().forEach(operation -> byReference.put(operation.reference(), operation));
            Set<K> materialSet = new LinkedHashSet<>(problem.requested().keySet());
            for (var entry : candidate.executions().entrySet()) {
                ECOPlanningOperation<K, R> operation = byReference.get(entry.getKey());
                if (operation == null || entry.getValue() <= 0L) {
                    throw new IllegalArgumentException("Candidate references an unknown planning operation");
                }
                materialSet.addAll(operation.inputs().keySet());
                materialSet.addAll(operation.outputs().keySet());
            }
            this.materials = List.copyOf(materialSet);
            this.materialIndices = new HashMap<>();
            for (int index = 0; index < materials.size(); index++) {
                materialIndices.put(materials.get(index), index);
            }

            this.requested = new long[materials.size()];
            problem.requested().forEach((key, amount) -> requested[materialIndices.get(key)] = amount);
            this.inventory = new long[materials.size()];
            this.syntheticSources = new long[materials.size()];
            problem.inventory().forEach((key, amount) -> {
                Integer index = materialIndices.get(key);
                if (index != null) {
                    inventory[index] = amount;
                }
            });

            Set<K> expandable = new HashSet<>();
            for (var operation : problem.operations()) {
                for (K output : operation.selectableOutputs()) {
                    if (positiveNet(operation, output) > 0L) {
                        expandable.add(output);
                    }
                }
            }
            this.externalSources = new boolean[materials.size()];
            for (int index = 0; index < materials.size(); index++) {
                externalSources[index] = !expandable.contains(materials.get(index));
            }

            this.operations = new ArrayList<>(candidate.executions().size());
            this.remaining = new long[candidate.executions().size()];
            int operationIndex = 0;
            for (var operation : problem.operations()) {
                long count = candidate.executions().getOrDefault(operation.reference(), 0L);
                if (count <= 0L) {
                    continue;
                }
                operations.add(compile(operation));
                remaining[operationIndex++] = count;
            }
        }

        private CompiledOperation<R> compile(ECOPlanningOperation<K, R> operation) {
            int[] inputKeys = new int[operation.inputs().size()];
            long[] inputAmounts = new long[inputKeys.length];
            int cursor = 0;
            for (var input : operation.inputs().entrySet()) {
                inputKeys[cursor] = materialIndices.get(input.getKey());
                inputAmounts[cursor] = input.getValue();
                cursor++;
            }
            int[] outputKeys = new int[operation.outputs().size()];
            long[] outputAmounts = new long[outputKeys.length];
            cursor = 0;
            for (var output : operation.outputs().entrySet()) {
                outputKeys[cursor] = materialIndices.get(output.getKey());
                outputAmounts[cursor] = output.getValue();
                cursor++;
            }
            return new CompiledOperation<>(
                operation.reference(), inputKeys, inputAmounts, outputKeys, outputAmounts
            );
        }

        private ECOInventorySchedule<K, R> run() {
            boolean executable = search(0);
            long[] finalInventory = executable
                ? solutionInventory
                : bestBlockedInventory == null ? inventory : bestBlockedInventory;
            List<ECOScheduledStep<R>> steps = executable ? compact(solutionSteps) : List.of();
            Map<K, Long> remainingInventory = toInventory(finalInventory);
            Map<K, Long> suppliedSources = executable
                ? toInventory(solutionSyntheticSources)
                : Map.of();
            Map<K, Long> blockedBy = executable ? Map.of() : blockedBy(
                finalInventory,
                bestBlockedCounts == null ? remaining : bestBlockedCounts
            );
            return new ECOInventorySchedule<>(
                executable,
                steps,
                remainingInventory,
                blockedBy,
                suppliedSources,
                budgetExhausted,
                expandedStates
            );
        }

        private boolean search(int depth) {
            if (shouldStop() || expandedStates >= maxExpandedStates || depth > MAX_RECURSION_DEPTH) {
                budgetExhausted = true;
                return false;
            }
            StateKey signature = new StateKey(remaining, inventory);
            if (!visited.add(signature)) {
                return false;
            }
            expandedStates++;

            if (allOperationsFinished()) {
                if (satisfyRequest()) {
                    solutionSteps = List.copyOf(path);
                    solutionInventory = inventory.clone();
                    solutionSyntheticSources = syntheticSources.clone();
                    return true;
                }
                recordBlocked();
                return false;
            }

            List<Integer> executable = executableOperations();
            if (executable.isEmpty()) {
                recordBlocked();
                return false;
            }
            executable.sort(
                Comparator.comparingLong(this::requiredSyntheticForOne)
                    .thenComparing(Comparator.comparingLong(this::unlockScore).reversed())
            );

            for (int operationIndex : executable) {
                if (shouldStop()) {
                    budgetExhausted = true;
                    return false;
                }
                CompiledOperation<R> operation = operations.get(operationIndex);
                long maximum = maxExecutable(operationIndex);
                for (long batches : batchChoices(operationIndex, executable, maximum)) {
                    long[] previous = captureTouched(operation);
                    long oldRemaining = remaining[operationIndex];
                    if (!apply(operation, batches)) {
                        restoreTouched(operation, previous);
                        budgetExhausted = true;
                        continue;
                    }
                    remaining[operationIndex] = oldRemaining - batches;
                    path.add(new ECOScheduledStep<>(operation.reference(), batches));
                    if (search(depth + 1)) {
                        return true;
                    }
                    path.removeLast();
                    remaining[operationIndex] = oldRemaining;
                    restoreTouched(operation, previous);
                }
            }
            return false;
        }

        private List<Integer> executableOperations() {
            List<Integer> result = new ArrayList<>();
            for (int index = 0; index < operations.size(); index++) {
                if (remaining[index] > 0L && maxExecutable(index) > 0L) {
                    result.add(index);
                }
            }
            return result;
        }

        private long maxExecutable(int operationIndex) {
            CompiledOperation<R> operation = operations.get(operationIndex);
            long result = remaining[operationIndex];
            for (int input = 0; input < operation.inputKeys().length; input++) {
                int material = operation.inputKeys()[input];
                if (allowSyntheticSources && externalSources[material]) {
                    continue;
                }
                long inputAmount = operation.inputAmounts()[input];
                long available = inventory[material];
                if (available < inputAmount) {
                    return 0L;
                }
                long outputAmount = operation.outputAmount(material);
                if (outputAmount < inputAmount) {
                    long loss = inputAmount - outputAmount;
                    result = Math.min(result, 1L + (available - inputAmount) / loss);
                }
            }
            return result;
        }

        private List<Long> batchChoices(int operationIndex, List<Integer> executable, long maximum) {
            TreeSet<Long> choices = new TreeSet<>(Comparator.reverseOrder());
            choices.add(maximum);
            choices.add(1L);

            CompiledOperation<R> operation = operations.get(operationIndex);
            long leaveOneBatch = maximum;
            for (int input = 0; input < operation.inputKeys().length; input++) {
                int material = operation.inputKeys()[input];
                long reserve = 0L;
                for (int otherIndex : executable) {
                    if (otherIndex == operationIndex || remaining[otherIndex] <= 0L) {
                        continue;
                    }
                    CompiledOperation<R> other = operations.get(otherIndex);
                    reserve = Math.max(reserve, other.inputAmount(material));
                }
                long available = Math.max(0L, inventory[material] - reserve);
                leaveOneBatch = Math.min(leaveOneBatch, available / operation.inputAmounts()[input]);
            }
            if (leaveOneBatch > 0L) {
                choices.add(leaveOneBatch);
            }
            return List.copyOf(choices);
        }

        private long unlockScore(int operationIndex) {
            CompiledOperation<R> operation = operations.get(operationIndex);
            long score = 0L;
            for (int output = 0; output < operation.outputKeys().length; output++) {
                int material = operation.outputKeys()[output];
                long consumers = 0L;
                for (int other = 0; other < operations.size(); other++) {
                    if (remaining[other] > 0L && operations.get(other).inputAmount(material) > 0L) {
                        consumers++;
                    }
                }
                score = saturatedAdd(score, saturatedMultiply(operation.outputAmounts()[output], 1L + consumers));
            }
            for (int input = 0; input < operation.inputKeys().length; input++) {
                int material = operation.inputKeys()[input];
                if (operation.outputAmount(material) < operation.inputAmounts()[input]) {
                    score = saturatedAdd(score, -Math.min(1_000_000L, operation.inputAmounts()[input]));
                }
            }
            return score;
        }

        private long requiredSyntheticForOne(int operationIndex) {
            if (!allowSyntheticSources) {
                return 0L;
            }
            CompiledOperation<R> operation = operations.get(operationIndex);
            long required = 0L;
            for (int input = 0; input < operation.inputKeys().length; input++) {
                int material = operation.inputKeys()[input];
                if (!externalSources[material]) {
                    continue;
                }
                long missing = operation.inputAmounts()[input] - inventory[material];
                if (missing > 0L) {
                    required = saturatedAdd(required, missing);
                }
            }
            return required;
        }

        private boolean apply(CompiledOperation<R> operation, long batches) {
            try {
                for (int input = 0; input < operation.inputKeys().length; input++) {
                    int material = operation.inputKeys()[input];
                    long minimum = minimumInventory(
                        operation.inputAmounts()[input], operation.outputAmount(material), batches
                    );
                    if (inventory[material] < minimum) {
                        if (!allowSyntheticSources || !externalSources[material]) {
                            return false;
                        }
                        long supplied = minimum - inventory[material];
                        inventory[material] = Math.addExact(inventory[material], supplied);
                        syntheticSources[material] = Math.addExact(syntheticSources[material], supplied);
                    }
                }
                for (int material : operation.touchedKeys()) {
                    long net = Math.subtractExact(operation.outputAmount(material), operation.inputAmount(material));
                    inventory[material] = Math.addExact(inventory[material], Math.multiplyExact(net, batches));
                }
                return true;
            } catch (ArithmeticException overflow) {
                return false;
            }
        }

        private long[] captureTouched(CompiledOperation<R> operation) {
            int[] touched = operation.touchedKeys();
            long[] result = new long[touched.length * 2];
            for (int index = 0; index < touched.length; index++) {
                result[index] = inventory[touched[index]];
                result[touched.length + index] = syntheticSources[touched[index]];
            }
            return result;
        }

        private void restoreTouched(CompiledOperation<R> operation, long[] previous) {
            int[] touched = operation.touchedKeys();
            for (int index = 0; index < touched.length; index++) {
                inventory[touched[index]] = previous[index];
                syntheticSources[touched[index]] = previous[touched.length + index];
            }
        }

        private boolean allOperationsFinished() {
            for (long count : remaining) {
                if (count > 0L) {
                    return false;
                }
            }
            return true;
        }

        private boolean satisfyRequest() {
            long[] previousInventory = inventory.clone();
            long[] previousSynthetic = syntheticSources.clone();
            for (int material = 0; material < requested.length; material++) {
                if (inventory[material] < requested[material]) {
                    if (!allowSyntheticSources || !externalSources[material]) {
                        System.arraycopy(previousInventory, 0, inventory, 0, inventory.length);
                        System.arraycopy(previousSynthetic, 0, syntheticSources, 0, syntheticSources.length);
                        return false;
                    }
                    try {
                        long supplied = requested[material] - inventory[material];
                        inventory[material] = Math.addExact(inventory[material], supplied);
                        syntheticSources[material] = Math.addExact(syntheticSources[material], supplied);
                    } catch (ArithmeticException overflow) {
                        System.arraycopy(previousInventory, 0, inventory, 0, inventory.length);
                        System.arraycopy(previousSynthetic, 0, syntheticSources, 0, syntheticSources.length);
                        budgetExhausted = true;
                        return false;
                    }
                }
            }
            return true;
        }

        private static long minimumInventory(long input, long output, long batches) {
            if (output >= input) {
                return input;
            }
            return Math.addExact(input, Math.multiplyExact(batches - 1L, input - output));
        }

        private void recordBlocked() {
            long totalRemaining = 0L;
            for (long count : remaining) {
                totalRemaining = saturatedAdd(totalRemaining, count);
            }
            if (totalRemaining < bestBlockedRemaining) {
                bestBlockedRemaining = totalRemaining;
                bestBlockedInventory = inventory.clone();
                bestBlockedCounts = remaining.clone();
            }
        }

        private Map<K, Long> blockedBy(long[] blockedInventory, long[] blockedCounts) {
            Map<K, Long> result = new LinkedHashMap<>();
            for (int operationIndex = 0; operationIndex < operations.size(); operationIndex++) {
                if (blockedCounts[operationIndex] <= 0L) {
                    continue;
                }
                CompiledOperation<R> operation = operations.get(operationIndex);
                for (int input = 0; input < operation.inputKeys().length; input++) {
                    long missing = operation.inputAmounts()[input] - blockedInventory[operation.inputKeys()[input]];
                    if (missing > 0L) {
                        result.merge(materials.get(operation.inputKeys()[input]), missing, Math::max);
                    }
                }
            }
            if (result.isEmpty()) {
                for (int material = 0; material < requested.length; material++) {
                    long missing = requested[material] - blockedInventory[material];
                    if (missing > 0L) {
                        result.put(materials.get(material), missing);
                    }
                }
            }
            return result;
        }

        private Map<K, Long> toInventory(long[] values) {
            Map<K, Long> result = new LinkedHashMap<>();
            for (int index = 0; index < values.length; index++) {
                if (values[index] > 0L) {
                    result.put(materials.get(index), values[index]);
                }
            }
            return result;
        }

        private boolean shouldStop() {
            return ECOSolveBudget.shouldStop(deadlineNanos);
        }

        private static long saturatedAdd(long left, long right) {
            try {
                return Math.addExact(left, right);
            } catch (ArithmeticException ignored) {
                return right < 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
            }
        }

        private static long saturatedMultiply(long left, long right) {
            try {
                return Math.multiplyExact(left, right);
            } catch (ArithmeticException ignored) {
                return Long.MAX_VALUE;
            }
        }
    }

    private record CompiledOperation<R>(
        R reference,
        int[] inputKeys,
        long[] inputAmounts,
        int[] outputKeys,
        long[] outputAmounts,
        int[] touchedKeys
    ) {
        private CompiledOperation(
            R reference,
            int[] inputKeys,
            long[] inputAmounts,
            int[] outputKeys,
            long[] outputAmounts
        ) {
            this(reference, inputKeys, inputAmounts, outputKeys, outputAmounts, touched(inputKeys, outputKeys));
        }

        private long inputAmount(int material) {
            for (int index = 0; index < inputKeys.length; index++) {
                if (inputKeys[index] == material) {
                    return inputAmounts[index];
                }
            }
            return 0L;
        }

        private long outputAmount(int material) {
            for (int index = 0; index < outputKeys.length; index++) {
                if (outputKeys[index] == material) {
                    return outputAmounts[index];
                }
            }
            return 0L;
        }

        private static int[] touched(int[] inputs, int[] outputs) {
            return java.util.stream.IntStream.concat(Arrays.stream(inputs), Arrays.stream(outputs))
                .distinct()
                .toArray();
        }
    }

    private static final class StateKey {
        private final long[] remaining;
        private final long[] inventory;
        private final int hash;

        private StateKey(long[] remaining, long[] inventory) {
            this.remaining = remaining.clone();
            this.inventory = inventory.clone();
            this.hash = 31 * Arrays.hashCode(this.remaining) + Arrays.hashCode(this.inventory);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof StateKey state
                && Arrays.equals(remaining, state.remaining)
                && Arrays.equals(inventory, state.inventory);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static <R> List<ECOScheduledStep<R>> compact(List<ECOScheduledStep<R>> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<ECOScheduledStep<R>> result = new ArrayList<>();
        for (ECOScheduledStep<R> step : source) {
            ECOScheduledStep<R> previous = result.isEmpty() ? null : result.getLast();
            if (previous != null && previous.operation().equals(step.operation())) {
                result.set(
                    result.size() - 1,
                    new ECOScheduledStep<>(previous.operation(), Math.addExact(previous.batches(), step.batches()))
                );
            } else {
                result.add(step);
            }
        }
        return List.copyOf(result);
    }
}
