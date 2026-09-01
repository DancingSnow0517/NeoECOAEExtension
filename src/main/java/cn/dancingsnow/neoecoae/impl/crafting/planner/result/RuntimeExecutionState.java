package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Mutable task accounting with dependency-aware multi-ready phases. */
public final class RuntimeExecutionState {
    private final ECOExecutionPlan plan;
    private final long[] remaining;
    private final int[] stepIndex;
    private final long[] stepRemaining;
    private final int[] unmetDependencies;
    private final int[] unfinishedTasks;
    private final int[][] dependentPhases;
    private final boolean[] complete;
    private final LinkedHashSet<Integer> readyTaskIds = new LinkedHashSet<>();
    private final TreeSet<Integer> readyPhases = new TreeSet<>();
    private int completedPhases;
    private int phaseIndex;

    public RuntimeExecutionState(ECOExecutionPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        remaining = plan.tasks().stream().mapToLong(ECOExecutionPlan.TaskSpec::totalCount).toArray();
        stepIndex = new int[plan.phases().size()];
        stepRemaining = new long[plan.phases().size()];
        unmetDependencies = new int[plan.phases().size()];
        unfinishedTasks = new int[plan.phases().size()];
        dependentPhases = buildDependentPhases(plan);
        complete = new boolean[plan.phases().size()];
        for (int phase = 0; phase < plan.phases().size(); phase++) loadStep(phase);
        rebuildFrontier();
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
        return readyPhases.isEmpty() ? null : plan.phases().get(readyPhases.first());
    }
    public List<Integer> eligibleTaskIds() {
        return new ArrayList<>(readyTaskIds);
    }
    public long dispatchLimit(int taskId) {
        checked(taskId);
        int phase = plan.task(taskId).phaseIndex();
        if (!readyTaskIds.contains(taskId) || complete[phase] || unmetDependencies[phase] != 0) return 0L;
        var spec = plan.phases().get(phase);
        if (stepIndex[phase] < spec.steps().size()) {
            return spec.steps().get(stepIndex[phase]).taskId() == taskId
                ? Math.min(remaining[taskId], stepRemaining[phase]) : 0L;
        }
        return remaining[taskId];
    }
    public List<Integer> applyAccepted(int taskId, long count) {
        long permitted = dispatchLimit(taskId);
        if (count <= 0 || count > permitted) {
            throw new IllegalArgumentException("Dispatch count exceeds runtime gate");
        }
        List<Integer> newlyReady = new ArrayList<>();
        remaining[taskId] -= count;
        int phase = plan.task(taskId).phaseIndex();
        var spec = plan.phases().get(phase);
        if (remaining[taskId] == 0L) {
            readyTaskIds.remove(taskId);
            unfinishedTasks[phase]--;
        }
        if (stepIndex[phase] < spec.steps().size()) {
            stepRemaining[phase] -= count;
            if (stepRemaining[phase] == 0) {
                readyTaskIds.remove(taskId);
                stepIndex[phase]++;
                loadStep(phase);
                exposePhaseTasks(phase, newlyReady);
            }
        }
        if (phaseDone(phase)) completePhase(phase, newlyReady);
        updateActivePhaseIndex();
        return newlyReady;
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
        rebuildFrontier();
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
    private boolean phaseDone(int phase) {
        var spec = plan.phases().get(phase);
        return stepIndex[phase] >= spec.steps().size() && unfinishedTasks[phase] == 0;
    }
    private boolean allComplete() {
        return completedPhases == complete.length;
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

    /** Rebuild is intentionally limited to construction and persistence restore. */
    private void rebuildFrontier() {
        Arrays.fill(complete, false);
        Arrays.fill(unfinishedTasks, 0);
        readyTaskIds.clear();
        readyPhases.clear();
        completedPhases = 0;
        for (int phase = 0; phase < plan.phases().size(); phase++) {
            unmetDependencies[phase] = plan.phases().get(phase).dependencies().size();
            for (int taskId : plan.phases().get(phase).taskIds()) {
                if (remaining[taskId] > 0L) unfinishedTasks[phase]++;
            }
        }
        // Stable topological phase ids let this single pass complete restored empty phases and release dependents.
        for (int phase = 0; phase < plan.phases().size(); phase++) {
            if (unmetDependencies[phase] != 0) continue;
            if (phaseDone(phase)) completePhase(phase, null);
            else activatePhase(phase, null);
        }
        updateActivePhaseIndex();
    }

    private void completePhase(int phase, List<Integer> newlyReady) {
        if (complete[phase]) return;
        complete[phase] = true;
        completedPhases++;
        readyPhases.remove(phase);
        for (int taskId : plan.phases().get(phase).taskIds()) readyTaskIds.remove(taskId);
        for (int dependent : dependentPhases[phase]) {
            if (--unmetDependencies[dependent] != 0) continue;
            if (phaseDone(dependent)) completePhase(dependent, newlyReady);
            else activatePhase(dependent, newlyReady);
        }
    }

    private void activatePhase(int phase, List<Integer> newlyReady) {
        readyPhases.add(phase);
        exposePhaseTasks(phase, newlyReady);
    }

    private void exposePhaseTasks(int phase, List<Integer> newlyReady) {
        if (complete[phase] || unmetDependencies[phase] != 0) return;
        var spec = plan.phases().get(phase);
        if (stepIndex[phase] < spec.steps().size()) {
            exposeTask(spec.steps().get(stepIndex[phase]).taskId(), newlyReady);
        } else {
            for (int taskId : spec.taskIds()) exposeTask(taskId, newlyReady);
        }
    }

    private void exposeTask(int taskId, List<Integer> newlyReady) {
        if (remaining[taskId] <= 0L) return;
        boolean added = readyTaskIds.add(taskId);
        // A new ordered step may deliberately expose the same task again after its previous queue entry ran.
        if (newlyReady != null && (added || plan.task(taskId).kind() == ECOExecutionPlan.TaskKind.CYCLE_ORDERED)) {
            newlyReady.add(taskId);
        }
    }

    private void updateActivePhaseIndex() {
        phaseIndex = readyPhases.isEmpty() ? plan.phases().size() : readyPhases.first();
    }

    private static int[][] buildDependentPhases(ECOExecutionPlan plan) {
        List<List<Integer>> reverse = new ArrayList<>(plan.phases().size());
        for (int i = 0; i < plan.phases().size(); i++) reverse.add(new ArrayList<>());
        for (int phase = 0; phase < plan.phases().size(); phase++) {
            for (int dependency : plan.phases().get(phase).dependencies()) reverse.get(dependency).add(phase);
        }
        int[][] result = new int[reverse.size()][];
        for (int i = 0; i < reverse.size(); i++) result[i] = reverse.get(i).stream().mapToInt(Integer::intValue).toArray();
        return result;
    }
}
