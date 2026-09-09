package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.util.NEMath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Mutable server-side cursor for one immutable ECO execution plan.
 *
 * <p>The plan describes what may be dispatched. This class owns the small amount of state that changes while a
 * job runs: ordered-step progress, dynamic firing counts, phase completion and the startup seed still reserved in
 * the CPU inventory. A failed provider attempt never reaches {@link #onAccepted} and therefore never advances any
 * scheduler state.</p>
 */
public final class ECOExecutionRuntime {
    private final ECOExecutionPlan plan;
    private final IPatternDetails[] patternsById;
    @Nullable
    private final ExecutingCraftingJob.TaskProgress[] progressByTaskId;
    private final List<Set<AEKey>> inputKeysByTaskId;
    private final int[] stepCursor;
    private final int[] dynamicCursor;
    private final List<long[]> remainingSteps;
    private final List<Map<Integer, Long>> remainingDynamicFirings;
    private final BitSet completedPhases;
    private final int[] unfinishedTasksByPhase;
    private final boolean[] unfinishedTasks;
    private final int[] activeDynamicTasksByPhase;
    private final int[] remainingDependencies;
    private final int[] activeTaskBuffer;
    private final List<List<Integer>> dependentsByPhase;
    private final Map<AEKey, Long> startupSeedRemaining = new LinkedHashMap<>();

    public ECOExecutionRuntime(ECOExecutionPlan plan, Map<Integer, IPatternDetails> patternsById) {
        this(plan, toPatternArray(plan, patternsById), null);
    }

    ECOExecutionRuntime(ECOExecutionPlan plan, Map<Integer, IPatternDetails> patternsById,
            ExecutingCraftingJob.TaskProgress[] progressByTaskId) {
        this(plan, toPatternArray(plan, patternsById), progressByTaskId);
    }

    ECOExecutionRuntime(ECOExecutionPlan plan, IPatternDetails[] patternsById,
            ExecutingCraftingJob.TaskProgress[] progressByTaskId) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.patternsById = Objects.requireNonNull(patternsById, "patternsById").clone();
        if (this.patternsById.length != plan.tasks().size()) {
            throw new IllegalArgumentException("Execution pattern binding shape changed");
        }
        this.progressByTaskId = progressByTaskId == null ? null : progressByTaskId.clone();
        if (this.progressByTaskId != null && this.progressByTaskId.length != plan.tasks().size()) {
            throw new IllegalArgumentException("Execution progress binding shape changed");
        }
        this.stepCursor = new int[plan.phases().size()];
        this.dynamicCursor = new int[plan.phases().size()];
        this.remainingSteps = new ArrayList<>(plan.phases().size());
        this.remainingDynamicFirings = new ArrayList<>(plan.phases().size());
        this.completedPhases = new BitSet(plan.phases().size());
        this.inputKeysByTaskId = new ArrayList<>(plan.tasks().size());
        this.unfinishedTasksByPhase = new int[plan.phases().size()];
        this.unfinishedTasks = new boolean[plan.tasks().size()];
        this.activeDynamicTasksByPhase = new int[plan.phases().size()];
        this.remainingDependencies = new int[plan.phases().size()];
        this.activeTaskBuffer = new int[plan.tasks().size()];
        this.dependentsByPhase = createDependents(plan);

        for (var task : plan.tasks()) {
            IPatternDetails actual = pattern(task.id());
            if (actual == null || !ECOPhaseScheduler.samePattern(task.pattern(), actual)) {
                throw new IllegalArgumentException("Execution task is not bound to the submitted pattern vector: "
                    + task.id());
            }
            if (this.progressByTaskId != null && this.progressByTaskId[task.id()] == null) {
                throw new IllegalArgumentException("Execution task has no bound progress: " + task.id());
            }
        }
        for (var phase : plan.phases()) {
            long[] steps = new long[phase.steps().size()];
            for (int i = 0; i < steps.length; i++) steps[i] = phase.steps().get(i).count();
            remainingSteps.add(steps);
            remainingDynamicFirings.add(new LinkedHashMap<>(phase.dynamicFirings()));
            phase.initialSeed().forEach((key, amount) -> startupSeedRemaining.merge(key, amount, NEMath::saturatingAdd));
        }
        for (IPatternDetails pattern : this.patternsById) {
            inputKeysByTaskId.add(Collections.unmodifiableSet(inputKeys(pattern)));
        }
        rebuildProgressState();
    }

    public ECOExecutionPlan plan() {
        return plan;
    }

    /** Candidate returned by the scheduler for one provider attempt. */
    public record DispatchCandidate(int taskId, int phaseIndex, IPatternDetails pattern,
            long maxDispatchCount, boolean blocksOrderedPhase) {
        public DispatchCandidate {
            Objects.requireNonNull(pattern, "pattern");
            if (taskId < 0 || phaseIndex < 0 || maxDispatchCount <= 0L) {
                throw new IllegalArgumentException("Invalid execution candidate");
            }
        }
    }

    /**
     * Returns all currently legal candidates in deterministic phase/task order.
     *
     * <p>An ordered phase contributes only its current step. Other ready phases remain eligible, so an unrelated
     * independent phase is not held hostage by a busy provider in one ordered phase. Dynamic phases rotate after a
     * successful firing; this prevents one branch from consuming every copy of a shared startup seed before another
     * currently runnable branch gets a chance.</p>
     */
    public List<DispatchCandidate> candidates() {
        requireProgressBinding();
        return candidatesInternal(null);
    }

    /** Compatibility entry point for callers that have not bound live task progress. */
    public List<DispatchCandidate> candidates(Map<IPatternDetails, Long> remainingTasks) {
        return candidatesInternal(Objects.requireNonNull(remainingTasks, "remainingTasks"));
    }

    private List<DispatchCandidate> candidatesInternal(@Nullable Map<IPatternDetails, Long> remainingTasks) {
        if (remainingTasks != null) refreshCompleted(remainingTasks);
        List<DispatchCandidate> result = new ArrayList<>(plan.tasks().size());
        for (int phaseIndex = 0; phaseIndex < plan.phases().size(); phaseIndex++) {
            if (completedPhases.get(phaseIndex) || !dependenciesComplete(phaseIndex)) continue;
            var phase = plan.phases().get(phaseIndex);
            if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps().isEmpty()) {
                advanceFinishedSteps(phaseIndex);
                if (progressByTaskId != null) maybeCompletePhase(phaseIndex);
                if (completedPhases.get(phaseIndex)) continue;
                if (stepCursor[phaseIndex] < phase.steps().size()) {
                    var step = phase.steps().get(stepCursor[phaseIndex]);
                    long remaining = taskRemaining(step.taskId(), remainingTasks);
                    long allowed = Math.min(remainingSteps.get(phaseIndex)[stepCursor[phaseIndex]], remaining);
                    if (allowed > 0L) {
                        result.add(candidate(phaseIndex, step.taskId(), allowed, true));
                    }
                } else {
                    addAllPhaseTasks(result, phaseIndex, phase.taskIds(), remainingTasks, false);
                }
                continue;
            }

            if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
                Map<Integer, Long> dynamic = remainingDynamicFirings.get(phaseIndex);
                int activeCount = 0;
                for (int taskId : phase.taskIds()) {
                    if (dynamic.getOrDefault(taskId, 0L) > 0L
                            && taskRemaining(taskId, remainingTasks) > 0L) {
                        activeTaskBuffer[activeCount++] = taskId;
                    }
                }
                if (progressByTaskId != null) maybeCompletePhase(phaseIndex);
                if (completedPhases.get(phaseIndex)) continue;
                if (activeCount == 0 && !hasDynamicFirings(dynamic)) {
                    addAllPhaseTasks(result, phaseIndex, phase.taskIds(), remainingTasks, false);
                    continue;
                }
                if (activeCount == 0) continue;
                int start = Math.floorMod(dynamicCursor[phaseIndex], activeCount);
                boolean sharedInput = activeCount > 1;
                for (int offset = 0; offset < activeCount; offset++) {
                    int taskId = activeTaskBuffer[(start + offset) % activeCount];
                    long allowed = Math.min(dynamic.getOrDefault(taskId, 0L), taskRemaining(taskId, remainingTasks));
                    if (sharedInput && sharesInputWithAnother(taskId, activeTaskBuffer, activeCount)) {
                        allowed = Math.min(allowed, 1L);
                    }
                    if (allowed > 0L) result.add(candidate(phaseIndex, taskId, allowed, false));
                }
                continue;
            }

            addAllPhaseTasks(result, phaseIndex, phase.taskIds(), remainingTasks, false);
        }
        return result.isEmpty() ? List.of() : Collections.unmodifiableList(result);
    }

    /** Commit scheduler state only after the provider has accepted the extracted inputs. */
    public void onAccepted(DispatchCandidate candidate, long count, KeyCounter[] inputs) {
        if (count <= 0L || count > candidate.maxDispatchCount()) {
            throw new IllegalArgumentException("Accepted dispatch exceeds scheduler allowance");
        }
        int phaseIndex = candidate.phaseIndex();
        var phase = plan.phases().get(phaseIndex);
        if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps().isEmpty()
                && stepCursor[phaseIndex] < phase.steps().size()) {
            var step = phase.steps().get(stepCursor[phaseIndex]);
            if (step.taskId() != candidate.taskId()) {
                throw new IllegalStateException("Accepted task is not the current ordered step");
            }
            long[] steps = remainingSteps.get(phaseIndex);
            steps[stepCursor[phaseIndex]] -= count;
            advanceFinishedSteps(phaseIndex);
        } else if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
            Map<Integer, Long> dynamic = remainingDynamicFirings.get(phaseIndex);
            long before = dynamic.getOrDefault(candidate.taskId(), 0L);
            if (count > before) throw new IllegalStateException("Accepted task exceeds dynamic firing vector");
            dynamic.put(candidate.taskId(), before - count);
            if (before > 0L && before - count <= 0L) activeDynamicTasksByPhase[phaseIndex]--;
            int taskPosition = phase.taskIds().indexOf(candidate.taskId());
            if (taskPosition >= 0 && !phase.taskIds().isEmpty()) {
                dynamicCursor[phaseIndex] = (taskPosition + 1) % phase.taskIds().size();
            }
        }
        refreshTaskState(candidate.taskId());
        maybeCompletePhase(phaseIndex);
        consumeStartupSeed(inputs, count);
    }

    public boolean isComplete() {
        requireProgressBinding();
        refreshCompleted();
        return completedPhases.cardinality() == plan.phases().size();
    }

    public boolean isComplete(Map<IPatternDetails, Long> remainingTasks) {
        refreshCompleted(Objects.requireNonNull(remainingTasks, "remainingTasks"));
        return completedPhases.cardinality() == plan.phases().size();
    }

    /** Amount of planned input that must stay in the CPU for future executions of {@code key}. */
    public long reservedInputAmount(AEKey key) {
        requireProgressBinding();
        return reservedInputAmount(key, null);
    }

    /** Compatibility entry point for callers that have not bound live task progress. */
    public long reservedInputAmount(AEKey key, Map<IPatternDetails, Long> remainingTasks) {
        if (key == null) return 0L;
        if (progressByTaskId == null) Objects.requireNonNull(remainingTasks, "remainingTasks");
        long result = startupSeedRemaining.getOrDefault(key, 0L);
        for (var task : plan.tasks()) {
            long remaining = taskRemaining(task.id(), remainingTasks);
            if (remaining <= 0L) continue;
            IPatternDetails pattern = pattern(task.id());
            result = NEMath.saturatingAdd(result, inputAmount(pattern, key, remaining));
        }
        return result;
    }

    @Nullable
    public AEKey startupSeedShortfall(appeng.crafting.inv.ListCraftingInventory inventory) {
        for (var entry : startupSeedRemaining.entrySet()) {
            if (inventory.extract(entry.getKey(), entry.getValue(), Actionable.SIMULATE)
                    < entry.getValue()) {
                return entry.getKey();
            }
        }
        return null;
    }

    void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        data.putIntArray("stepCursor", stepCursor);
        data.putIntArray("dynamicCursor", dynamicCursor);
        ListTag phases = new ListTag();
        for (int phaseIndex = 0; phaseIndex < plan.phases().size(); phaseIndex++) {
            CompoundTag phase = new CompoundTag();
            phase.putLongArray("steps", remainingSteps.get(phaseIndex));
            ListTag dynamic = new ListTag();
            for (var entry : remainingDynamicFirings.get(phaseIndex).entrySet()) {
                CompoundTag firing = new CompoundTag();
                firing.putInt("task", entry.getKey());
                firing.putLong("count", entry.getValue());
                dynamic.add(firing);
            }
            phase.put("dynamic", dynamic);
            phases.add(phase);
        }
        data.put("phases", phases);
        data.putIntArray("completed", completedPhases.stream().toArray());
        ListTag seeds = new ListTag();
        for (var entry : startupSeedRemaining.entrySet()) {
            seeds.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getValue())));
        }
        data.put("startupSeeds", seeds);
    }

    static ECOExecutionRuntime fromNBT(ECOExecutionPlan plan, Map<Integer, IPatternDetails> patternsById,
            CompoundTag data, HolderLookup.Provider registries) {
        return fromNBT(plan, patternsById, null, data, registries);
    }

    static ECOExecutionRuntime fromNBT(ECOExecutionPlan plan, Map<Integer, IPatternDetails> patternsById,
            ExecutingCraftingJob.TaskProgress[] progressByTaskId, CompoundTag data,
            HolderLookup.Provider registries) {
        ECOExecutionRuntime runtime = new ECOExecutionRuntime(plan, patternsById, progressByTaskId);
        int[] stepCursor = data.getIntArray("stepCursor");
        int[] dynamicCursor = data.getIntArray("dynamicCursor");
        if (stepCursor.length != runtime.stepCursor.length || dynamicCursor.length != runtime.dynamicCursor.length) {
            throw new IllegalArgumentException("Execution runtime cursor shape changed");
        }
        System.arraycopy(stepCursor, 0, runtime.stepCursor, 0, stepCursor.length);
        System.arraycopy(dynamicCursor, 0, runtime.dynamicCursor, 0, dynamicCursor.length);

        ListTag phases = data.getList("phases", Tag.TAG_COMPOUND);
        if (phases.size() != runtime.plan.phases().size()) {
            throw new IllegalArgumentException("Execution runtime phase count changed");
        }
        for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
            CompoundTag phase = phases.getCompound(phaseIndex);
            long[] steps = phase.getLongArray("steps");
            if (steps.length != runtime.remainingSteps.get(phaseIndex).length) {
                throw new IllegalArgumentException("Execution runtime step count changed");
            }
            System.arraycopy(steps, 0, runtime.remainingSteps.get(phaseIndex), 0, steps.length);
            Map<Integer, Long> dynamic = runtime.remainingDynamicFirings.get(phaseIndex);
            dynamic.clear();
            ListTag dynamicTag = phase.getList("dynamic", Tag.TAG_COMPOUND);
            for (int i = 0; i < dynamicTag.size(); i++) {
                CompoundTag firing = dynamicTag.getCompound(i);
                dynamic.put(firing.getInt("task"), firing.getLong("count"));
            }
        }
        runtime.completedPhases.clear();
        for (int phase : data.getIntArray("completed")) {
            if (phase >= 0 && phase < runtime.plan.phases().size()) runtime.completedPhases.set(phase);
        }
        runtime.startupSeedRemaining.clear();
        ListTag seeds = data.getList("startupSeeds", Tag.TAG_COMPOUND);
        for (int i = 0; i < seeds.size(); i++) {
            GenericStack stack = GenericStack.readTag(registries, seeds.getCompound(i));
            if (stack != null && stack.amount() > 0L) {
                runtime.startupSeedRemaining.merge(stack.what(), stack.amount(), NEMath::saturatingAdd);
            }
        }
        runtime.rebuildProgressState();
        return runtime;
    }

    private void refreshCompleted(Map<IPatternDetails, Long> remainingTasks) {
        for (int phaseIndex = 0; phaseIndex < plan.phases().size(); phaseIndex++) {
            if (!completedPhases.get(phaseIndex) && phaseComplete(phaseIndex, remainingTasks)) {
                markPhaseCompleted(phaseIndex);
            }
        }
    }

    private void refreshCompleted() {
        for (int phaseIndex = 0; phaseIndex < plan.phases().size(); phaseIndex++) {
            if (!completedPhases.get(phaseIndex) && phaseComplete(phaseIndex, null)) {
                markPhaseCompleted(phaseIndex);
            }
        }
    }

    private boolean phaseComplete(int phaseIndex, @Nullable Map<IPatternDetails, Long> remainingTasks) {
        var phase = plan.phases().get(phaseIndex);
        if (progressByTaskId != null && remainingTasks == null) {
            if (unfinishedTasksByPhase[phaseIndex] > 0) return false;
            if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps().isEmpty()) {
                return stepCursor[phaseIndex] >= phase.steps().size();
            }
            if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
                return activeDynamicTasksByPhase[phaseIndex] == 0;
            }
            return true;
        }
        for (int taskId : phase.taskIds()) if (taskRemaining(taskId, remainingTasks) > 0L) return false;
        if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps().isEmpty()) {
            return stepCursor[phaseIndex] >= phase.steps().size();
        }
        if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
            return !hasDynamicFirings(remainingDynamicFirings.get(phaseIndex));
        }
        return true;
    }

    private boolean dependenciesComplete(int phaseIndex) {
        return remainingDependencies[phaseIndex] == 0;
    }

    private void advanceFinishedSteps(int phaseIndex) {
        var steps = plan.phases().get(phaseIndex).steps();
        long[] remaining = remainingSteps.get(phaseIndex);
        while (stepCursor[phaseIndex] < steps.size() && remaining[stepCursor[phaseIndex]] == 0L) {
            stepCursor[phaseIndex]++;
        }
    }

    private void addAllPhaseTasks(List<DispatchCandidate> result, int phaseIndex, List<Integer> taskIds,
            @Nullable Map<IPatternDetails, Long> remainingTasks, boolean blocksOrderedPhase) {
        for (int taskId : taskIds) {
            long remaining = taskRemaining(taskId, remainingTasks);
            if (remaining > 0L) result.add(candidate(phaseIndex, taskId, remaining, blocksOrderedPhase));
        }
    }

    private DispatchCandidate candidate(int phaseIndex, int taskId, long allowed, boolean blocksOrderedPhase) {
        IPatternDetails pattern = pattern(taskId);
        if (pattern == null) throw new IllegalStateException("Execution task is not bound: " + taskId);
        return new DispatchCandidate(taskId, phaseIndex, pattern, allowed, blocksOrderedPhase);
    }

    private long taskRemaining(int taskId, @Nullable Map<IPatternDetails, Long> remainingTasks) {
        if (progressByTaskId != null) {
            var progress = progressByTaskId[taskId];
            return progress == null ? 0L : Math.max(0L, progress.value);
        }
        if (remainingTasks == null) return 0L;
        IPatternDetails pattern = pattern(taskId);
        if (pattern == null) return 0L;
        Long remaining = remainingTasks.get(pattern);
        return remaining == null ? 0L : Math.max(0L, remaining);
    }

    private boolean sharesInputWithAnother(int taskId, int[] active, int activeCount) {
        Set<AEKey> keys = inputKeysByTaskId.get(taskId);
        if (keys.isEmpty()) return false;
        for (int index = 0; index < activeCount; index++) {
            int otherId = active[index];
            if (otherId == taskId) continue;
            Set<AEKey> otherKeys = inputKeysByTaskId.get(otherId);
            for (AEKey key : keys) if (otherKeys.contains(key)) return true;
        }
        return false;
    }

    private static boolean hasDynamicFirings(Map<Integer, Long> dynamic) {
        for (long value : dynamic.values()) if (value > 0L) return true;
        return false;
    }

    private static java.util.Set<AEKey> inputKeys(IPatternDetails pattern) {
        java.util.Set<AEKey> result = new java.util.HashSet<>();
        try {
            for (var input : pattern.getInputs()) {
                if (input == null || input.getPossibleInputs() == null) continue;
                for (GenericStack stack : input.getPossibleInputs()) if (stack != null && stack.what() != null) {
                    result.add(stack.what());
                }
            }
        } catch (RuntimeException ignored) {
            return java.util.Set.of();
        }
        return result;
    }

    private static long inputAmount(IPatternDetails pattern, AEKey key, long remaining) {
        long result = 0L;
        try {
            for (var input : pattern.getInputs()) {
                if (input == null || input.getPossibleInputs() == null || input.getPossibleInputs().length == 0) continue;
                GenericStack selected = input.getPossibleInputs()[0];
                if (selected != null && key.equals(selected.what())) {
                    result = NEMath.saturatingAdd(result,
                        NEMath.saturatingMultiply(selected.amount(), Math.max(0L, input.getMultiplier())));
                }
            }
        } catch (RuntimeException ignored) {
            return 0L;
        }
        return NEMath.saturatingMultiply(result, remaining);
    }

    private void consumeStartupSeed(KeyCounter[] inputs, long count) {
        if (inputs == null || startupSeedRemaining.isEmpty()) return;
        for (KeyCounter input : inputs) {
            if (input == null) continue;
            for (var entry : input) {
                long consumed = NEMath.saturatingMultiply(entry.getLongValue(), count);
                long reserved = startupSeedRemaining.getOrDefault(entry.getKey(), 0L);
                if (reserved > 0L) startupSeedRemaining.put(entry.getKey(), Math.max(0L, reserved - consumed));
            }
        }
        startupSeedRemaining.entrySet().removeIf(entry -> entry.getValue() <= 0L);
    }

    private void requireProgressBinding() {
        if (progressByTaskId == null) {
            throw new IllegalStateException("Execution runtime has no live task-progress binding");
        }
    }

    private IPatternDetails pattern(int taskId) {
        return taskId >= 0 && taskId < patternsById.length ? patternsById[taskId] : null;
    }

    private void refreshTaskState(int taskId) {
        if (progressByTaskId == null || taskId < 0 || taskId >= unfinishedTasks.length) return;
        boolean unfinished = taskRemaining(taskId, null) > 0L;
        if (unfinished == unfinishedTasks[taskId]) return;
        unfinishedTasks[taskId] = unfinished;
        int phaseIndex = plan.task(taskId).phaseIndex();
        unfinishedTasksByPhase[phaseIndex] += unfinished ? 1 : -1;
    }

    private void maybeCompletePhase(int phaseIndex) {
        if (progressByTaskId == null || completedPhases.get(phaseIndex)) return;
        var phase = plan.phases().get(phaseIndex);
        if (unfinishedTasksByPhase[phaseIndex] > 0) return;
        if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps().isEmpty()
                && stepCursor[phaseIndex] < phase.steps().size()) return;
        if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE
                && activeDynamicTasksByPhase[phaseIndex] > 0) return;
        markPhaseCompleted(phaseIndex);
    }

    private void markPhaseCompleted(int phaseIndex) {
        if (completedPhases.get(phaseIndex)) return;
        completedPhases.set(phaseIndex);
        for (int dependent : dependentsByPhase.get(phaseIndex)) {
            if (remainingDependencies[dependent] > 0) remainingDependencies[dependent]--;
        }
    }

    private void rebuildProgressState() {
        Arrays.fill(unfinishedTasksByPhase, 0);
        Arrays.fill(unfinishedTasks, false);
        Arrays.fill(activeDynamicTasksByPhase, 0);
        Arrays.fill(remainingDependencies, 0);
        for (var dependents : dependentsByPhase) dependents.clear();
        for (var phase : plan.phases()) {
            int phaseIndex = phase.index();
            for (int dependency : phase.dependencies()) {
                dependentsByPhase.get(dependency).add(phaseIndex);
                if (!completedPhases.get(dependency)) remainingDependencies[phaseIndex]++;
            }
            for (var entry : remainingDynamicFirings.get(phaseIndex).entrySet()) {
                if (entry.getValue() > 0L) activeDynamicTasksByPhase[phaseIndex]++;
            }
        }
        if (progressByTaskId != null) {
            for (int taskId = 0; taskId < progressByTaskId.length; taskId++) {
                boolean unfinished = taskRemaining(taskId, null) > 0L;
                unfinishedTasks[taskId] = unfinished;
                if (unfinished) unfinishedTasksByPhase[plan.task(taskId).phaseIndex()]++;
            }
            refreshCompleted();
        }
    }

    private static List<List<Integer>> createDependents(ECOExecutionPlan plan) {
        List<List<Integer>> result = new ArrayList<>(plan.phases().size());
        for (int i = 0; i < plan.phases().size(); i++) result.add(new ArrayList<>());
        return result;
    }

    private static IPatternDetails[] toPatternArray(ECOExecutionPlan plan,
            Map<Integer, IPatternDetails> patternsById) {
        IPatternDetails[] result = new IPatternDetails[plan.tasks().size()];
        for (var entry : patternsById.entrySet()) {
            if (entry.getKey() != null && entry.getKey() >= 0 && entry.getKey() < result.length) {
                result[entry.getKey()] = entry.getValue();
            }
        }
        return result;
    }
}
