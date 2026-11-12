package cn.dancingsnow.neoecoae.crafting.planner.result;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.RepeatLayout;
import java.util.List;
import java.util.Objects;

/** Fully validated hand-off from the planner to the crafting CPU. */
public record ECOExecutionPlan(
        PlanIdentity.Signature signature,
        ExecutionMode mode,
        List<TaskSpec> tasks,
        List<PhaseSpec> phases,
        ECOExecutionSchedule schedule) {

    public ECOExecutionPlan {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(mode, "mode");


        Objects.requireNonNull(schedule, "schedule");
        validateShape(tasks, phases, mode);
    }

    public TaskSpec task(int taskId) {
        if (taskId < 0 || taskId >= tasks.size() || tasks.get(taskId).id() != taskId) {
            throw new IllegalArgumentException("Unknown execution task " + taskId);
        }
        return tasks.get(taskId);
    }

    public record TaskSpec(int id, PlanIdentity.PatternIdentity identity, IPatternDetails pattern,
            PatternRuntimeInfo runtimeInfo, PlannerAmount plannerTotalCount, int phaseIndex, TaskKind kind,
            List<PlannedInputAllocation> inputAllocations) {
        public TaskSpec {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(runtimeInfo, "runtimeInfo");
            Objects.requireNonNull(kind, "kind");

            if (id < 0 || plannerTotalCount.signum() <= 0 || phaseIndex < 0) {
                throw new IllegalArgumentException("Invalid execution task");
            }
        }
        public TaskSpec(int id, PlanIdentity.PatternIdentity identity, IPatternDetails pattern,
                PatternRuntimeInfo runtimeInfo, long totalCount, int phaseIndex, TaskKind kind) {
            this(id, identity, pattern, runtimeInfo, PlannerAmount.of(totalCount), phaseIndex, kind, List.of());
        }
        public TaskSpec(int id, PlanIdentity.PatternIdentity identity, IPatternDetails pattern,
                PatternRuntimeInfo runtimeInfo, long totalCount, int phaseIndex, TaskKind kind,
                List<PlannedInputAllocation> allocations) {
            this(id, identity, pattern, runtimeInfo, PlannerAmount.of(totalCount), phaseIndex, kind, allocations);
        }
        public long totalCount() { return plannerTotalCount.longValueSaturated(); }
    }

    /** Frozen fields needed by dispatch/UI without reinterpreting the planner graph. */
    public record PatternRuntimeInfo(@org.jetbrains.annotations.Nullable Object definition, int inputSlots,
            List<GenericStack> outputs) {
        public PatternRuntimeInfo {
            if (inputSlots < 0) throw new IllegalArgumentException("Negative input slot count");

        }

        public static PatternRuntimeInfo from(IPatternDetails pattern) {
            var inputs = pattern.getInputs();
            return new PatternRuntimeInfo(pattern.getDefinition(), inputs == null ? 0 : inputs.length,
                pattern.getOutputs());
        }
    }

    public record PhaseSpec(java.util.Map<Integer, PlannerAmount> plannerDynamicFirings,
            java.util.Map<AEKey, PlannerAmount> plannerInitialSeed, int index, int componentId, ECOExecutionSchedule.Type type,
            List<Integer> taskIds, List<ExecutionStep> steps, List<Integer> dependencies) {
        public PhaseSpec {
            if (index < 0) throw new IllegalArgumentException("Negative phase index");
            Objects.requireNonNull(type, "type");





            for (var entry : plannerDynamicFirings.entrySet()) {
                Integer taskId = entry.getKey();
                PlannerAmount count = entry.getValue();
                if (taskId == null || !taskIds.contains(taskId) || count == null || count.signum() <= 0) {
                    throw new IllegalArgumentException("Invalid dynamic cycle firing vector");
                }
            }
            plannerInitialSeed.forEach((key, amount) -> {
                if (key == null || amount == null || amount.signum() <= 0) {
                    throw new IllegalArgumentException("Invalid dynamic cycle seed");
                }
            });
        }

        public PhaseSpec(int index, int componentId, ECOExecutionSchedule.Type type,
                List<Integer> taskIds, List<ExecutionStep> steps, List<Integer> dependencies) {
            this(java.util.Map.of(), java.util.Map.of(), index, componentId, type, taskIds, steps, dependencies);
        }
        public PhaseSpec(int index, int componentId, ECOExecutionSchedule.Type type,
                List<Integer> taskIds, List<ExecutionStep> steps, List<Integer> dependencies,
                java.util.Map<Integer, Long> dynamicFirings, java.util.Map<AEKey, Long> initialSeed) {
            this(exact(dynamicFirings), exact(initialSeed), index, componentId, type, taskIds, steps, dependencies);
        }
        public java.util.Map<Integer, Long> dynamicFirings() { return bounded(plannerDynamicFirings); }
        public java.util.Map<AEKey, Long> initialSeed() { return bounded(plannerInitialSeed); }
    }

    /** One ordered run. Count is deliberately compressed and may be much larger than an int. */
    public record ExecutionStep(int taskId, PlannerAmount plannerCount, int repeatWidth, PlannerAmount plannerRepetitions)
            implements RepeatLayout.Run {
        public ExecutionStep(int taskId, long count) { this(taskId, PlannerAmount.of(count), 1, PlannerAmount.ONE); }
        public ExecutionStep(int taskId, long count, int repeatWidth, long repetitions) {
            this(taskId, PlannerAmount.of(count), repeatWidth, PlannerAmount.of(repetitions));
        }
        public long count() { return plannerCount.longValueSaturated(); }
        @Override public long repetitions() { return plannerRepetitions.longValueSaturated(); }
        public ExecutionStep {
            if (taskId < 0 || plannerCount.signum() <= 0) throw new IllegalArgumentException("Invalid execution step");
            if (repeatWidth < 1 || plannerRepetitions.signum() <= 0 || plannerRepetitions.equals(PlannerAmount.ONE) && repeatWidth != 1) {
                throw new IllegalArgumentException("Invalid execution circuit repeat");
            }
        }
    }

    public enum TaskKind { DAG, CYCLE_REMAINDER, CYCLE_ORDERED, CYCLE_DYNAMIC }

    private static <K> java.util.Map<K, PlannerAmount> exact(java.util.Map<K, Long> values) {
        var result = new java.util.LinkedHashMap<K, PlannerAmount>();
        values.forEach((key, value) -> result.put(key, PlannerAmount.of(value)));
        return result;
    }

    private static <K> java.util.Map<K, Long> bounded(java.util.Map<K, PlannerAmount> values) {
        var result = new java.util.LinkedHashMap<K, Long>();
        values.forEach((key, value) -> result.put(key, value.longValueSaturated()));
        return result;
    }

    private static void validateShape(List<TaskSpec> tasks, List<PhaseSpec> phases, ExecutionMode mode) {
        boolean[] taskOwned = new boolean[tasks.size()];
        for (int i = 0; i < tasks.size(); i++) {
            TaskSpec task = tasks.get(i);
            if (task.id() != i) throw new IllegalArgumentException("Task ids must be dense and stable");
            if (task.phaseIndex() >= phases.size()) throw new IllegalArgumentException("Task phase is absent");
            boolean[] allocatedSlots = new boolean[task.runtimeInfo().inputSlots()];
            for (PlannedInputAllocation allocation : task.inputAllocations()) {
                if (allocation.slot() >= allocatedSlots.length || allocatedSlots[allocation.slot()]) {
                    throw new IllegalArgumentException("Invalid or duplicate task input allocation slot");
                }
                allocatedSlots[allocation.slot()] = true;
                if (!allocation.plannerTotalCrafts().equals(task.plannerTotalCount())) {
                    throw new IllegalArgumentException("Task input allocation does not cover its complete count");
                }
            }
        }
        for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
            PhaseSpec phase = phases.get(phaseIndex);
            if (phase.index() != phaseIndex) throw new IllegalArgumentException("Phase ids must be dense");
            for (int taskId : phase.taskIds()) {
                TaskSpec task = tasks.get(taskId);
                if (task.phaseIndex() != phaseIndex || taskOwned[taskId]) {
                    throw new IllegalArgumentException("Every task must have exactly one runtime owner");
                }
                taskOwned[taskId] = true;
            }
            if (phase.type() == ECOExecutionSchedule.Type.DAG && !phase.steps().isEmpty()) {
                throw new IllegalArgumentException("A DAG phase cannot contain ordered cycle steps");
            }
            if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE && !phase.steps().isEmpty()) {
                throw new IllegalArgumentException("A dynamic cycle phase cannot contain ordered steps");
            }
            if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE && phase.dynamicFirings().isEmpty()) {
                throw new IllegalArgumentException("A dynamic cycle phase requires an exact firing vector");
            }
            if (phase.type() != ECOExecutionSchedule.Type.DYNAMIC_CYCLE && !phase.dynamicFirings().isEmpty()) {
                throw new IllegalArgumentException("Only a dynamic cycle phase can contain dynamic firings");
            }
            phase.plannerDynamicFirings().forEach((taskId, count) -> {
                if (count.compareTo(tasks.get(taskId).plannerTotalCount()) > 0) {
                    throw new IllegalArgumentException("Dynamic firing count exceeds its task total");
                }
            });
            if (phase.type() == ECOExecutionSchedule.Type.DAG && !phase.initialSeed().isEmpty()) {
                throw new IllegalArgumentException("Only a cycle phase can retain startup seed metadata");
            }
            java.util.HashSet<Integer> uniqueDependencies = new java.util.HashSet<>();
            for (int dependency : phase.dependencies()) {
                if (dependency < 0 || dependency >= phaseIndex) {
                    throw new IllegalArgumentException("Phase dependencies must follow stable topological order");
                }
                if (!uniqueDependencies.add(dependency)) {
                    throw new IllegalArgumentException("Duplicate phase dependency");
                }
            }
            PlannerAmount[] repeats = RepeatLayout.plannerMultipliers(phase.steps());
            java.util.Map<Integer, PlannerAmount> orderedCounts = new java.util.HashMap<>();
            for (int stepIndex = 0; stepIndex < phase.steps().size(); stepIndex++) {
                ExecutionStep step = phase.steps().get(stepIndex);
                if (!phase.taskIds().contains(step.taskId())) {
                    throw new IllegalArgumentException("Cycle step references a task outside its phase");
                }
                orderedCounts.merge(step.taskId(), step.plannerCount().multiply(repeats[stepIndex]), PlannerAmount::add);
            }
            orderedCounts.forEach((task, count) -> {
                if (count.compareTo(tasks.get(task).plannerTotalCount()) > 0) {
                    throw new IllegalArgumentException("Ordered circuit exceeds its task total");
                }
            });
        }
        for (boolean owned : taskOwned) if (!owned) {
            throw new IllegalArgumentException("Execution plan contains an unowned task");
        }
        if (mode == ExecutionMode.ORDERED_CYCLE && phases.stream()
                .noneMatch(p -> p.type() == ECOExecutionSchedule.Type.CYCLE && !p.steps().isEmpty())) {
            throw new IllegalArgumentException("Ordered-cycle mode requires an ordered cycle trace");
        }
        if (mode == ExecutionMode.DYNAMIC_CYCLE && phases.stream()
                .noneMatch(p -> p.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE && !p.taskIds().isEmpty())) {
            throw new IllegalArgumentException("Dynamic-cycle mode requires a dynamic cycle phase");
        }
    }
}
