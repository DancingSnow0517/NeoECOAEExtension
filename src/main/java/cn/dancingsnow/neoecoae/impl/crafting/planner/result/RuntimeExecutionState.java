package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The sole mutable source of task counts and phase/step cursors for an ECO execution plan. */
public final class RuntimeExecutionState {
    private final ECOExecutionPlan plan;
    private final long[] remaining;
    private int phaseIndex;
    private int stepIndex;
    private long stepRemaining;

    public RuntimeExecutionState(ECOExecutionPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.remaining = plan.tasks().stream().mapToLong(ECOExecutionPlan.TaskSpec::totalCount).toArray();
        loadStepRemaining();
        advanceCompletedPhases();
    }

    public ECOExecutionPlan plan() { return plan; }
    public int phaseIndex() { return phaseIndex; }
    public int stepIndex() { return stepIndex; }
    public long stepRemaining() { return stepRemaining; }
    public boolean finished() { return phaseIndex >= plan.phases().size(); }
    public long remaining(int taskId) { return remaining[checkedTaskId(taskId)]; }
    public long[] remainingSnapshot() { return remaining.clone(); }

    public ECOExecutionPlan.PhaseSpec activePhase() {
        return finished() ? null : plan.phases().get(phaseIndex);
    }

    /** Tasks eligible now. An ordered run exposes exactly one task until its compressed count is consumed. */
    public List<Integer> eligibleTaskIds() {
        advanceCompletedPhases();
        ECOExecutionPlan.PhaseSpec phase = activePhase();
        if (phase == null) return List.of();
        if (stepIndex < phase.steps().size()) {
            int taskId = phase.steps().get(stepIndex).taskId();
            return remaining[taskId] > 0 ? List.of(taskId) : List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (int taskId : phase.taskIds()) if (remaining[taskId] > 0) result.add(taskId);
        return List.copyOf(result);
    }

    /** Maximum count the current gate permits for this task, including a compressed ordered run. */
    public long dispatchLimit(int taskId) {
        checkedTaskId(taskId);
        ECOExecutionPlan.PhaseSpec phase = activePhase();
        if (phase == null || !phase.taskIds().contains(taskId)) return 0L;
        if (stepIndex < phase.steps().size()) {
            return phase.steps().get(stepIndex).taskId() == taskId
                ? Math.min(remaining[taskId], stepRemaining) : 0L;
        }
        return remaining[taskId];
    }

    /** Applies one accepted provider ownership transfer and advances steps/phases atomically. */
    public void applyAccepted(int taskId, long count) {
        long permitted = dispatchLimit(taskId);
        if (count <= 0 || count > permitted) {
            throw new IllegalArgumentException("Dispatch count " + count + " exceeds runtime gate " + permitted);
        }
        remaining[taskId] -= count;
        ECOExecutionPlan.PhaseSpec phase = activePhase();
        if (phase != null && stepIndex < phase.steps().size()) {
            stepRemaining -= count;
            if (stepRemaining == 0) {
                stepIndex++;
                loadStepRemaining();
            }
        }
        advanceCompletedPhases();
    }

    public void restore(long[] restoredRemaining, int restoredPhaseIndex, int restoredStepIndex,
            long restoredStepRemaining) {
        if (restoredRemaining == null || restoredRemaining.length != remaining.length) {
            throw new IllegalArgumentException("Persisted task vector length does not match the execution plan");
        }
        for (int i = 0; i < remaining.length; i++) {
            if (restoredRemaining[i] < 0 || restoredRemaining[i] > plan.task(i).totalCount()) {
                throw new IllegalArgumentException("Invalid persisted remaining count for task " + i);
            }
            remaining[i] = restoredRemaining[i];
        }
        if (restoredPhaseIndex < 0 || restoredPhaseIndex > plan.phases().size()) {
            throw new IllegalArgumentException("Invalid persisted phase cursor");
        }
        phaseIndex = restoredPhaseIndex;
        if (finished()) {
            if (restoredStepIndex != 0 || restoredStepRemaining != 0) {
                throw new IllegalArgumentException("A finished plan cannot retain an ordered step cursor");
            }
            stepIndex = 0;
            stepRemaining = 0;
            return;
        }
        var steps = activePhase().steps();
        if (restoredStepIndex < 0 || restoredStepIndex > steps.size()) {
            throw new IllegalArgumentException("Invalid persisted step cursor");
        }
        stepIndex = restoredStepIndex;
        if (stepIndex == steps.size()) {
            if (restoredStepRemaining != 0) throw new IllegalArgumentException("Completed steps retain work");
            stepRemaining = 0;
        } else {
            long full = steps.get(stepIndex).count();
            if (restoredStepRemaining <= 0 || restoredStepRemaining > full) {
                throw new IllegalArgumentException("Invalid persisted compressed-step remainder");
            }
            stepRemaining = restoredStepRemaining;
        }
        validateCursorAgainstRemaining();
        advanceCompletedPhases();
    }

    private void advanceCompletedPhases() {
        while (!finished()) {
            var phase = activePhase();
            if (stepIndex < phase.steps().size()) return;
            boolean workLeft = false;
            for (int taskId : phase.taskIds()) if (remaining[taskId] > 0) { workLeft = true; break; }
            if (workLeft) return;
            phaseIndex++;
            stepIndex = 0;
            loadStepRemaining();
        }
    }

    private void loadStepRemaining() {
        var phase = activePhase();
        stepRemaining = phase != null && stepIndex < phase.steps().size()
            ? phase.steps().get(stepIndex).count() : 0L;
    }

    private void validateCursorAgainstRemaining() {
        if (finished()) return;
        var phase = activePhase();
        long[] consumedByTask = new long[remaining.length];
        for (int i = 0; i < stepIndex; i++) {
            var step = phase.steps().get(i);
            consumedByTask[step.taskId()] = Math.addExact(consumedByTask[step.taskId()], step.count());
        }
        if (stepIndex < phase.steps().size()) {
            var step = phase.steps().get(stepIndex);
            consumedByTask[step.taskId()] = Math.addExact(consumedByTask[step.taskId()], step.count() - stepRemaining);
        }
        for (int taskId : phase.taskIds()) {
            long actuallyConsumed = plan.task(taskId).totalCount() - remaining[taskId];
            boolean orderedTraceActive = stepIndex < phase.steps().size();
            if (actuallyConsumed < consumedByTask[taskId]
                    || (orderedTraceActive && actuallyConsumed != consumedByTask[taskId])) {
                throw new IllegalArgumentException("Persisted cursor is ahead of task accounting");
            }
        }
    }

    private int checkedTaskId(int taskId) {
        if (taskId < 0 || taskId >= remaining.length) throw new IllegalArgumentException("Unknown task " + taskId);
        return taskId;
    }
}
