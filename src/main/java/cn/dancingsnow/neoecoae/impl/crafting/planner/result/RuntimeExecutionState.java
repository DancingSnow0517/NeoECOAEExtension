package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mutable task accounting with dependency-aware multi-ready phases. */
public final class RuntimeExecutionState {
    private final ECOExecutionPlan plan;
    private final long[] remaining;
    private final int[] stepIndex;
    private final long[] stepRemaining;
    private final int[] unmetDependencies;
    private final boolean[] complete;
    private int phaseIndex;

    public RuntimeExecutionState(ECOExecutionPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        remaining = plan.tasks().stream().mapToLong(ECOExecutionPlan.TaskSpec::totalCount).toArray();
        stepIndex = new int[plan.phases().size()];
        stepRemaining = new long[plan.phases().size()];
        unmetDependencies = new int[plan.phases().size()];
        complete = new boolean[plan.phases().size()];
        for (int i = 0; i < plan.phases().size(); i++) {
            unmetDependencies[i] = plan.phases().get(i).dependencies().size();
            loadStep(i);
        }
        refreshCompletion();
    }
    public ECOExecutionPlan plan() { return plan; }
    public int phaseIndex() { return phaseIndex; }
    public int stepIndex() { return activePhase() == null ? 0 : stepIndex[phaseIndex]; }
    public long stepRemaining() { return activePhase() == null ? 0L : stepRemaining[phaseIndex]; }
    public boolean finished() { return allComplete(); }
    public long remaining(int taskId) { return remaining[checked(taskId)]; }
    public long[] remainingSnapshot() { return remaining.clone(); }
    public int stepIndex(int phaseId) { return stepIndex[phaseId]; }
    public long stepRemaining(int phaseId) { return stepRemaining[phaseId]; }
    public ECOExecutionPlan.PhaseSpec activePhase() {
        for (int i = 0; i < plan.phases().size(); i++) {
            if (!complete[i] && unmetDependencies[i] == 0) return plan.phases().get(i);
        }
        return null;
    }
    public List<Integer> eligibleTaskIds() {
        refreshCompletion();
        List<Integer> result = new ArrayList<>();
        for (int phase = 0; phase < plan.phases().size(); phase++) {
            if (complete[phase] || unmetDependencies[phase] != 0) continue;
            var spec = plan.phases().get(phase);
            if (stepIndex[phase] < spec.steps().size()) {
                int id = spec.steps().get(stepIndex[phase]).taskId();
                if (remaining[id] > 0) result.add(id);
            } else {
                for (int id : spec.taskIds()) if (remaining[id] > 0) result.add(id);
            }
        }
        return List.copyOf(result);
    }
    public long dispatchLimit(int taskId) {
        checked(taskId);
        refreshCompletion();
        int phase = plan.task(taskId).phaseIndex();
        if (complete[phase] || unmetDependencies[phase] != 0) return 0L;
        var spec = plan.phases().get(phase);
        if (stepIndex[phase] < spec.steps().size()) {
            return spec.steps().get(stepIndex[phase]).taskId() == taskId
                ? Math.min(remaining[taskId], stepRemaining[phase]) : 0L;
        }
        return remaining[taskId];
    }
    public void applyAccepted(int taskId, long count) {
        long permitted = dispatchLimit(taskId);
        if (count <= 0 || count > permitted) {
            throw new IllegalArgumentException("Dispatch count exceeds runtime gate");
        }
        remaining[taskId] -= count;
        int phase = plan.task(taskId).phaseIndex();
        var spec = plan.phases().get(phase);
        if (stepIndex[phase] < spec.steps().size()) {
            stepRemaining[phase] -= count;
            if (stepRemaining[phase] == 0) {
                stepIndex[phase]++;
                loadStep(phase);
            }
        }
        refreshCompletion();
    }

    public void restore(long[] restoredRemaining, int[] restoredSteps, long[] restoredStepRemaining) {
        if (restoredRemaining == null || restoredRemaining.length != remaining.length) {
            throw new IllegalArgumentException("Persisted task vector length mismatch");
        }
        if (restoredSteps.length != stepIndex.length || restoredStepRemaining.length != stepRemaining.length) {
            throw new IllegalArgumentException("Persisted phase cursor length mismatch");
        }
        for (int i = 0; i < remaining.length; i++) {
            if (restoredRemaining[i] < 0 || restoredRemaining[i] > plan.task(i).totalCount()) {
                throw new IllegalArgumentException("Invalid persisted remaining");
            }
            remaining[i] = restoredRemaining[i];
        }
        for (int phase = 0; phase < stepIndex.length; phase++) {
            var steps = plan.phases().get(phase).steps();
            int index = restoredSteps[phase];
            long cursorRemaining = restoredStepRemaining[phase];
            if (index < 0 || index > steps.size()) {
                throw new IllegalArgumentException("Invalid persisted step cursor");
            }
            if (index == steps.size() ? cursorRemaining != 0L
                    : cursorRemaining <= 0L || cursorRemaining > steps.get(index).count()) {
                throw new IllegalArgumentException("Invalid persisted step remainder");
            }
            stepIndex[phase] = index;
            stepRemaining[phase] = cursorRemaining;
            validateCursor(phase);
        }
        java.util.Arrays.fill(complete, false);
        for (int i = 0; i < unmetDependencies.length; i++) {
            unmetDependencies[i] = plan.phases().get(i).dependencies().size();
        }
        refreshCompletion();
    }

    private void validateCursor(int phase) {
        var spec = plan.phases().get(phase);
        long[] consumed = new long[remaining.length];
        for (int i = 0; i < stepIndex[phase]; i++) {
            var step = spec.steps().get(i);
            consumed[step.taskId()] = Math.addExact(consumed[step.taskId()], step.count());
        }
        if (stepIndex[phase] < spec.steps().size()) {
            var step = spec.steps().get(stepIndex[phase]);
            consumed[step.taskId()] = Math.addExact(
                consumed[step.taskId()], step.count() - stepRemaining[phase]);
        }
        boolean traceActive = stepIndex[phase] < spec.steps().size();
        for (int taskId : spec.taskIds()) {
            long actual = plan.task(taskId).totalCount() - remaining[taskId];
            if (actual < consumed[taskId] || traceActive && actual != consumed[taskId]) {
                throw new IllegalArgumentException("Persisted cursor does not match task accounting");
            }
        }
    }
    private void refreshCompletion() {
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < complete.length; i++) {
                if (!complete[i] && unmetDependencies[i] == 0 && phaseDone(i)) {
                    complete[i] = true;
                    changed = true;
                    for (int j = 0; j < complete.length; j++) {
                        if (plan.phases().get(j).dependencies().contains(i)) unmetDependencies[j]--;
                    }
                }
            }
        } while (changed);
        for (int i = 0; i < complete.length; i++) {
            if (!complete[i] && unmetDependencies[i] == 0) {
                phaseIndex = i;
                break;
            }
        }
    }
    private boolean phaseDone(int phase) {
        var spec = plan.phases().get(phase);
        if (stepIndex[phase] < spec.steps().size()) return false;
        for (int id : spec.taskIds()) if (remaining[id] > 0) return false;
        return true;
    }
    private boolean allComplete() {
        refreshCompletion();
        for (boolean value : complete) if (!value) return false;
        return true;
    }
    private void loadStep(int phase) {
        var steps = plan.phases().get(phase).steps();
        stepRemaining[phase] = stepIndex[phase] < steps.size()
            ? steps.get(stepIndex[phase]).count() : 0L;
    }
    private int checked(int id) {
        if (id < 0 || id >= remaining.length) throw new IllegalArgumentException("Unknown task " + id);
        return id;
    }
}
