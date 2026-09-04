package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable task accounting with dependency-aware multi-ready phases. */
public final class RuntimeExecutionState {
    private final ECOExecutionPlan plan;
    private final java.util.Map<Integer, CycleResourceLedger> cycleLedgers = new java.util.LinkedHashMap<>();
    private final long[] remaining;
    private final long[] dynamicRemaining;
    private final long[] dynamicPhaseRemaining;
    private final int[] stepIndex;
    private final long[] stepRemaining;
    private final int[] unmetDependencies;
    private final int[] unfinishedTasks;
    private final int[][] dependentPhases;
    private final boolean[] complete;
    private final LinkedHashSet<Integer> readyTaskIds = new LinkedHashSet<>();
    private final TreeSet<Integer> readyPhases = new TreeSet<>();
    private final LinkedHashMap<AEKey, Integer> resourceIds = new LinkedHashMap<>();
    private final AEKey[] resourceKeys;
    private final long[] onHand;
    private final long[] futureNeed;
    private final int[] consumedOffset;
    private final int[] consumedResource;
    private final long[] consumedAmount;
    private final int[] committedReadyTasks;
    private final boolean[] committedReady;
    private int committedReadyCount;
    private boolean collectCommittedReady;
    private int completedPhases;
    private int phaseIndex;

    public RuntimeExecutionState(ECOExecutionPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
        for (var phase : plan.phases()) {
            if (phase.type() != ECOExecutionSchedule.Type.DAG) {
                cycleLedgers.put(phase.componentId(), new CycleResourceLedger(phase.componentId(), phase.initialSeed()));
            }
        }
        remaining = plan.tasks().stream().mapToLong(ECOExecutionPlan.TaskSpec::totalCount).toArray();
        dynamicRemaining = new long[remaining.length];
        dynamicPhaseRemaining = new long[plan.phases().size()];
        for (var phase : plan.phases()) {
            phase.dynamicFirings().forEach((taskId, count) -> {
                dynamicRemaining[taskId] = count;
                dynamicPhaseRemaining[phase.index()] = Math.addExact(dynamicPhaseRemaining[phase.index()], count);
            });
        }
        stepIndex = new int[plan.phases().size()];
        stepRemaining = new long[plan.phases().size()];
        unmetDependencies = new int[plan.phases().size()];
        unfinishedTasks = new int[plan.phases().size()];
        dependentPhases = buildDependentPhases(plan);
        complete = new boolean[plan.phases().size()];
        for (var phase : plan.phases()) phase.initialSeed().keySet().forEach(this::resourceId);
        for (var task : plan.tasks()) {
            var semantics = ECOPhaseScheduler.semantic(task.pattern());
            semantics.consumedInputs().forEach(input -> resourceId(input.key()));
            semantics.producedOutputs().forEach(output -> { if (output != null) resourceId(output.what()); });
            semantics.returnedOutputs().forEach(output -> { if (output != null) resourceId(output.what()); });
        }
        resourceKeys = resourceIds.keySet().toArray(AEKey[]::new);
        onHand = new long[resourceIds.size()];
        futureNeed = new long[resourceIds.size()];
        List<Integer> consumedResources = new ArrayList<>();
        List<Long> consumedAmounts = new ArrayList<>();
        consumedOffset = new int[remaining.length + 1];
        for (var task : plan.tasks()) {
            var semantics = ECOPhaseScheduler.semantic(task.pattern());
            Map<Integer, Long> taskConsumed = new LinkedHashMap<>();
            for (var input : semantics.consumedInputs()) {
                taskConsumed.merge(resourceId(input.key()), input.amountPerPattern().longValueExact(), Math::addExact);
            }
            taskConsumed.forEach((resource, amount) -> {
                consumedResources.add(resource);
                consumedAmounts.add(amount);
                futureNeed[resource] = Math.addExact(futureNeed[resource], Math.multiplyExact(amount, task.totalCount()));
            });
            consumedOffset[task.id() + 1] = consumedResources.size();
        }
        consumedResource = consumedResources.stream().mapToInt(Integer::intValue).toArray();
        consumedAmount = consumedAmounts.stream().mapToLong(Long::longValue).toArray();
        committedReadyTasks = new int[remaining.length];
        committedReady = new boolean[remaining.length];
        for (int phase = 0; phase < plan.phases().size(); phase++) loadStep(phase);
        rebuildFrontier();
    }
    public ECOExecutionPlan plan() { return plan; }
    public CycleResourceLedger cycleLedger(int componentId) { return cycleLedgers.get(componentId); }
    public java.util.Map<Integer, CycleResourceLedger> cycleLedgers() { return java.util.Map.copyOf(cycleLedgers); }
    public int phaseIndex() { return phaseIndex; }
    public int stepIndex() { return activePhase() == null ? 0 : stepIndex[phaseIndex]; }
    public long stepRemaining() { return activePhase() == null ? 0L : stepRemaining[phaseIndex]; }
    public boolean finished() { return allComplete(); }
    public long remaining(int taskId) { return remaining[checked(taskId)]; }
    public long[] remainingSnapshot() { return remaining.clone(); }
    public long dynamicRemaining(int taskId) { return dynamicRemaining[checked(taskId)]; }
    public long[] dynamicRemainingSnapshot() { return dynamicRemaining.clone(); }
    public int resourceCount() { return resourceKeys.length; }
    public int resourceIdIfKnown(AEKey key) { return key == null ? -1 : resourceIds.getOrDefault(key, -1); }
    public AEKey keyByResourceId(int resourceId) {
        if (resourceId < 0 || resourceId >= resourceKeys.length) {
            throw new IllegalArgumentException("Unknown resource " + resourceId);
        }
        return resourceKeys[resourceId];
    }
    public long onHand(AEKey key) { int id = resourceIds.getOrDefault(key, -1); return id < 0 ? 0L : onHand[id]; }
    public long futureNeed(AEKey key) { int id = resourceIds.getOrDefault(key, -1); return id < 0 ? 0L : futureNeed[id]; }
    public long reserve(AEKey key) { return futureNeed(key); }
    public long releasable(AEKey key) { return Math.max(0L, onHand(key) - reserve(key)); }
    public Map<AEKey, Long> ownershipSnapshot() { Map<AEKey, Long> result = new LinkedHashMap<>(); resourceIds.forEach((k,v) -> { if (onHand[v] != 0) result.put(k, onHand[v]); }); return Map.copyOf(result); }
    private int resourceId(AEKey key) { if (key == null) return -1; return resourceIds.computeIfAbsent(key, ignored -> resourceIds.size()); }
    public void acceptOutput(AEKey key, long amount) { if (amount <= 0 || key == null) return; int id = resourceIds.getOrDefault(key, -1); ensureCapacity(id); onHand[id] = Math.addExact(onHand[id], amount); }
    public void releaseExternal(AEKey key, long amount) { if (amount <= 0 || key == null) return; int id = resourceIds.getOrDefault(key, -1); if (id < 0 || amount > onHand[id]) throw new IllegalArgumentException("Ownership release exceeds on-hand"); onHand[id] -= amount; }
    public void restoreOwnership(Map<AEKey, Long> restored) {
        Arrays.fill(onHand, 0L);
        restored.forEach((key, amount) -> {
            int id = resourceIds.getOrDefault(key, -1);
            ensureCapacity(id);
            if (amount == null || amount < 0L) throw new IllegalArgumentException("Invalid persisted ownership");
            onHand[id] = amount;
        });
    }
    public void flushTick() {
        for (int i = 0; i < committedReadyCount; i++) committedReady[committedReadyTasks[i]] = false;
        committedReadyCount = 0;
    }
    private void ensureCapacity(int id) { if (id < 0 || id >= onHand.length) throw new IllegalStateException("Resource was not compiled into kernel"); }
    public int stepIndex(int phaseId) { return stepIndex[phaseId]; }
    public long stepRemaining(int phaseId) { return stepRemaining[phaseId]; }
    public ECOExecutionPlan.PhaseSpec activePhase() {
        return readyPhases.isEmpty() ? null : plan.phases().get(readyPhases.first());
    }
    public boolean activeDynamicCycle() {
        var phase = activePhase();
        return phase != null && phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE;
    }
    public List<Integer> eligibleTaskIds() {
        return new ArrayList<>(readyTaskIds);
    }
    public long dispatchLimit(int taskId) {
        checked(taskId);
        int phase = plan.task(taskId).phaseIndex();
        if (!readyTaskIds.contains(taskId) || complete[phase] || unmetDependencies[phase] != 0) return 0L;
        var spec = plan.phases().get(phase);
        if (spec.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE && dynamicPhaseRemaining[phase] > 0L) {
            return Math.min(remaining[taskId], dynamicRemaining[taskId]);
        }
        if (stepIndex[phase] < spec.steps().size()) {
            return spec.steps().get(stepIndex[phase]).taskId() == taskId
                ? Math.min(remaining[taskId], stepRemaining[phase]) : 0L;
        }
        return remaining[taskId];
    }

    /**
     * Amount of a feedback key that must remain CPU-owned while any ordered cycle still needs it.
     *
     * <p>This deliberately includes cycle phases whose DAG dependencies have not completed yet. Looking only at
     * {@link #eligibleTaskIds()} loses a network-provided startup seed before the cycle becomes runnable: the final
     * output release path sees it as surplus and delivers it to the requester. The conservative upper bound mirrors
     * the established active-cycle policy and naturally drops to zero as cycle task counts are accepted.</p>
     */
    public long pendingCycleFeedbackReserve(AEKey key) {
        if (key == null) return 0L;
        long reserve = 0L;
        for (var task : plan.tasks()) {
            if (remaining[task.id()] <= 0L) continue;
            var phase = plan.phases().get(task.phaseIndex());
            if (phase.type() == ECOExecutionSchedule.Type.DAG) continue;
            if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
                if (dynamicPhaseRemaining[phase.index()] > 0L) {
                    var ledger = cycleLedgers.get(phase.componentId());
                    reserve = Math.max(reserve, ledger == null ? phase.initialSeed().getOrDefault(key, 0L)
                        : Math.max(phase.initialSeed().getOrDefault(key, 0L), ledger.reserve(key)));
                }
                continue;
            }
            long perCraft = 0L;
            for (var input : task.pattern().getInputs()) {
                if (input == null || input.getPossibleInputs() == null) continue;
                for (var possible : input.getPossibleInputs()) {
                    if (possible != null && key.equals(possible.what())) {
                        perCraft = Math.addExact(perCraft,
                            Math.multiplyExact(possible.amount(), input.getMultiplier()));
                        break;
                    }
                }
            }
            try {
                reserve = Math.addExact(reserve, Math.multiplyExact(perCraft, remaining[task.id()]));
            } catch (ArithmeticException overflow) {
                return Long.MAX_VALUE;
            }
        }
        return reserve;
    }
    public List<Integer> applyAccepted(int taskId, long count) {
        commitAccepted(taskId, count);
        List<Integer> result = new ArrayList<>(committedReadyCount);
        for (int i = 0; i < committedReadyCount; i++) result.add(committedReadyTasks[i]);
        for (int i = 0; i < committedReadyCount; i++) committedReady[committedReadyTasks[i]] = false;
        committedReadyCount = 0;
        return result;
    }

    /** Commits dispatch consumption. Outputs are intentionally accounted only by acceptOutput. */
    public void commitAccepted(int taskId, long count) {
        long permitted = dispatchLimit(taskId);
        if (count <= 0 || count > permitted) {
            throw new IllegalArgumentException("Dispatch count exceeds runtime gate");
        }
        for (int row = consumedOffset[taskId]; row < consumedOffset[taskId + 1]; row++) {
            long amount = Math.multiplyExact(consumedAmount[row], count);
            if (amount > onHand[consumedResource[row]]) {
                throw new IllegalArgumentException("Dispatch consumption exceeds CPU ownership");
            }
        }
        for (int row = consumedOffset[taskId]; row < consumedOffset[taskId + 1]; row++) {
            long amount = Math.multiplyExact(consumedAmount[row], count);
            int id = consumedResource[row];
            onHand[id] -= amount;
            futureNeed[id] = Math.max(0L, futureNeed[id] - amount);
        }
        collectCommittedReady = true;
        remaining[taskId] -= count;
        int phase = plan.task(taskId).phaseIndex();
        var spec = plan.phases().get(phase);
        boolean dynamicVectorCompleted = false;
        if (spec.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE && dynamicPhaseRemaining[phase] > 0L) {
            if (count > dynamicRemaining[taskId]) {
                throw new IllegalArgumentException("Dispatch exceeds dynamic cycle firing vector");
            }
            dynamicRemaining[taskId] -= count;
            dynamicPhaseRemaining[phase] -= count;
            if (dynamicRemaining[taskId] == 0L) readyTaskIds.remove(taskId);
            dynamicVectorCompleted = dynamicPhaseRemaining[phase] == 0L;
        }
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
                exposePhaseTasks(phase, null);
            }
        }
        if (dynamicVectorCompleted) exposePhaseTasks(phase, null);
        if (phaseDone(phase)) completePhase(phase, null);
        updateActivePhaseIndex();
        collectCommittedReady = false;
    }


    public void restore(long[] restoredRemaining, long[] restoredDynamicRemaining,
            int[] restoredSteps, long[] restoredStepRemaining) {
        if (restoredRemaining == null || restoredRemaining.length != remaining.length) {
            throw new IllegalArgumentException("Persisted task vector length mismatch");
        }
        if (restoredSteps.length != stepIndex.length || restoredStepRemaining.length != stepRemaining.length) {
            throw new IllegalArgumentException("Persisted phase cursor length mismatch");
        }
        if (restoredDynamicRemaining == null || restoredDynamicRemaining.length != dynamicRemaining.length) {
            throw new IllegalArgumentException("Persisted dynamic firing vector length mismatch");
        }
        for (int i = 0; i < remaining.length; i++) {
            if (restoredRemaining[i] < 0 || restoredRemaining[i] > plan.task(i).totalCount()) {
                throw new IllegalArgumentException("Invalid persisted remaining");
            }
            remaining[i] = restoredRemaining[i];
            long plannedDynamic = plan.phases().get(plan.task(i).phaseIndex()).dynamicFirings().getOrDefault(i, 0L);
            if (restoredDynamicRemaining[i] < 0L || restoredDynamicRemaining[i] > plannedDynamic
                    || restoredDynamicRemaining[i] > remaining[i]) {
                throw new IllegalArgumentException("Invalid persisted dynamic firing remainder");
            }
            dynamicRemaining[i] = restoredDynamicRemaining[i];
        }
        Arrays.fill(dynamicPhaseRemaining, 0L);
        for (int taskId = 0; taskId < dynamicRemaining.length; taskId++) {
            int phase = plan.task(taskId).phaseIndex();
            dynamicPhaseRemaining[phase] = Math.addExact(dynamicPhaseRemaining[phase], dynamicRemaining[taskId]);
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

    /** Source-compatible restore for plans created before dynamic cycle phases existed. */
    public void restore(long[] restoredRemaining, int[] restoredSteps, long[] restoredStepRemaining) {
        if (plan.phases().stream().anyMatch(phase -> !phase.dynamicFirings().isEmpty())) {
            throw new IllegalArgumentException("Dynamic cycle restore requires its persisted firing vector");
        }
        restore(restoredRemaining, new long[remaining.length], restoredSteps, restoredStepRemaining);
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
            if (spec.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
                long dynamicConsumed = spec.dynamicFirings().getOrDefault(taskId, 0L) - dynamicRemaining[taskId];
                if (actual < dynamicConsumed || dynamicPhaseRemaining[phase] > 0L && actual != dynamicConsumed) {
                    throw new IllegalArgumentException("Persisted dynamic vector does not match task accounting");
                }
            }
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
        if (spec.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE && dynamicPhaseRemaining[phase] > 0L) {
            for (int taskId : spec.taskIds()) {
                if (dynamicRemaining[taskId] > 0L) exposeTask(taskId, newlyReady);
            }
        } else if (stepIndex[phase] < spec.steps().size()) {
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
        } else if (collectCommittedReady && (added
                || plan.task(taskId).kind() == ECOExecutionPlan.TaskKind.CYCLE_ORDERED)) {
            if (committedReady[taskId]) return;
            if (committedReadyCount >= committedReadyTasks.length) {
                throw new IllegalStateException("Ready task queue overflow");
            }
            committedReady[taskId] = true;
            committedReadyTasks[committedReadyCount++] = taskId;
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
