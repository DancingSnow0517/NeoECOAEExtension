package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import java.util.List;
import java.util.Objects;

/** Immutable, fully validated hand-off from the planner to the crafting CPU. */
public record ECOExecutionPlan(
        PlanIdentity.Signature signature,
        ExecutionMode mode,
        List<TaskSpec> tasks,
        List<PhaseSpec> phases,
        ECOExecutionSchedule schedule) {

    public ECOExecutionPlan {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(mode, "mode");
        tasks = List.copyOf(tasks);
        phases = List.copyOf(phases);
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
            PatternRuntimeInfo runtimeInfo, long totalCount, int phaseIndex, TaskKind kind) {
        public TaskSpec {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(runtimeInfo, "runtimeInfo");
            Objects.requireNonNull(kind, "kind");
            if (id < 0 || totalCount <= 0 || phaseIndex < 0) {
                throw new IllegalArgumentException("Invalid execution task");
            }
        }
    }

    /** Frozen fields needed by dispatch/UI without reinterpreting the planner graph. */
    public record PatternRuntimeInfo(@org.jetbrains.annotations.Nullable Object definition, int inputSlots,
            List<GenericStack> outputs) {
        public PatternRuntimeInfo {
            if (inputSlots < 0) throw new IllegalArgumentException("Negative input slot count");
            outputs = List.copyOf(outputs);
        }

        public static PatternRuntimeInfo from(IPatternDetails pattern) {
            var inputs = pattern.getInputs();
            return new PatternRuntimeInfo(pattern.getDefinition(), inputs == null ? 0 : inputs.length,
                pattern.getOutputs());
        }
    }

    public record PhaseSpec(int index, int componentId, ECOExecutionSchedule.Type type,
            List<Integer> taskIds, List<ExecutionStep> steps) {
        public PhaseSpec {
            if (index < 0) throw new IllegalArgumentException("Negative phase index");
            Objects.requireNonNull(type, "type");
            taskIds = List.copyOf(taskIds);
            steps = List.copyOf(steps);
        }
    }

    /** One ordered run. Count is deliberately compressed and may be much larger than an int. */
    public record ExecutionStep(int taskId, long count) {
        public ExecutionStep {
            if (taskId < 0 || count <= 0) throw new IllegalArgumentException("Invalid execution step");
        }
    }

    public enum TaskKind { DAG, CYCLE_REMAINDER, CYCLE_ORDERED }

    private static void validateShape(List<TaskSpec> tasks, List<PhaseSpec> phases, ExecutionMode mode) {
        boolean[] taskOwned = new boolean[tasks.size()];
        for (int i = 0; i < tasks.size(); i++) {
            TaskSpec task = tasks.get(i);
            if (task.id() != i) throw new IllegalArgumentException("Task ids must be dense and stable");
            if (task.phaseIndex() >= phases.size()) throw new IllegalArgumentException("Task phase is absent");
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
            for (ExecutionStep step : phase.steps()) {
                if (!phase.taskIds().contains(step.taskId())) {
                    throw new IllegalArgumentException("Cycle step references a task outside its phase");
                }
            }
        }
        for (boolean owned : taskOwned) if (!owned) {
            throw new IllegalArgumentException("Execution plan contains an unowned task");
        }
        if (mode == ExecutionMode.ORDERED_CYCLE && phases.stream()
                .noneMatch(p -> p.type() == ECOExecutionSchedule.Type.CYCLE && !p.steps().isEmpty())) {
            throw new IllegalArgumentException("Ordered-cycle mode requires an ordered cycle trace");
        }
    }
}
