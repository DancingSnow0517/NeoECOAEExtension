package cn.dancingsnow.neoecoae.api.me;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.util.NEMath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mutable server-side cursor for one immutable ECO execution plan.
 *
 * <p>The plan describes what may be dispatched. This class owns the small amount of state that changes while a
 * job runs: ordered-step progress, dynamic firing counts, phase completion and the startup seed still reserved in
 * the CPU inventory. A failed provider attempt never reaches {@link #onAccepted} and therefore never advances any
 * scheduler state.</p>
 */
public final class ECOExecutionRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae.dispatch");
    private static final int MAX_DIAGNOSTIC_PHASES = 16;
    private static final int MAX_DIAGNOSTIC_TASKS = 8;
    private static final int MAX_DIAGNOSTIC_LENGTH = 4096;

    private final ECOExecutionPlan plan;
    private final IPatternDetails[] patternsById;
    @Nullable
    private final ExecutingCraftingJob.TaskProgress[] progressByTaskId;
    private final int[][] progressAliasesByTaskId;
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
    // Reused by the dispatch loop; callers consume the snapshot before requesting the next one.
    private final List<DispatchCandidate> candidateBuffer = new ArrayList<>();
    private final List<Map<AEKey, Long>> startupSeedRemainingByPhase;
    private final Map<AEKey, Long> startupSeedGenerations = new HashMap<>();
    private long startupSeedGeneration;
    private boolean reconciledEmptyCandidates;

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
        this.startupSeedRemainingByPhase = new ArrayList<>(plan.phases().size());

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
        this.progressAliasesByTaskId = createProgressAliases(this.progressByTaskId, plan.tasks().size());
        rejectSharedProgressAliases();
        for (var phase : plan.phases()) {
            long[] steps = new long[phase.steps().size()];
            for (int i = 0; i < steps.length; i++) steps[i] = phase.steps().get(i).count();
            remainingSteps.add(steps);
            remainingDynamicFirings.add(new LinkedHashMap<>(phase.dynamicFirings()));
            startupSeedRemainingByPhase.add(new LinkedHashMap<>(phase.initialSeed()));
        }
        for (IPatternDetails pattern : this.patternsById) {
            inputKeysByTaskId.add(Collections.unmodifiableSet(inputKeys(pattern)));
        }
        rebuildProgressState();
        logSharedProgressAliases();
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
        return candidatesInternal(null, true);
    }

    /** Compatibility entry point for callers that have not bound live task progress. */
    public List<DispatchCandidate> candidates(Map<IPatternDetails, Long> remainingTasks) {
        return candidatesInternal(Objects.requireNonNull(remainingTasks, "remainingTasks"), false);
    }

    private List<DispatchCandidate> candidatesInternal(@Nullable Map<IPatternDetails, Long> remainingTasks,
            boolean allowReconciliation) {
        if (remainingTasks != null) refreshCompleted(remainingTasks);
        List<DispatchCandidate> result = candidateBuffer;
        result.clear();
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

            int candidatesBeforePhase = result.size();
            addAllPhaseTasks(result, phaseIndex, phase.taskIds(), remainingTasks, false);
            if (progressByTaskId != null && result.size() == candidatesBeforePhase) {
                // TaskProgress is the source of truth. If a ready DAG phase produces no candidate, reconcile its
                // cached unfinished count before deciding that it must remain open. This repairs a stale phase cache
                // without scanning the complete execution plan on every successful dispatch.
                refreshPhaseTaskState(phaseIndex, phase.taskIds());
                maybeCompletePhase(phaseIndex);
            }
        }
        if (!result.isEmpty()) return result;
        if (allowReconciliation && !reconciledEmptyCandidates
                && completedPhases.cardinality() < plan.phases().size()) {
            reconciledEmptyCandidates = true;
            String before = NEConfig.ecoDispatchWatchdogDebug ? describeProgressCache() : null;
            rebuildProgressState();
            if (NEConfig.ecoDispatchWatchdogDebug) {
                String after = describeProgressCache();
                if (!Objects.equals(before, after)) {
                    LOGGER.warn("[ECO Execution Cache Reconciliation] repaired stale cache before=[{}] after=[{}]",
                        before, after);
                }
            }
            return candidatesInternal(null, false);
        }
        return List.of();
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
            // Once the exact cycle firing vector is exhausted, remaining AE2 task progress is the aggregate
            // non-cycle remainder. It is legal work, but must not be charged to the already-finished vector.
            if (before > 0L) {
                if (count > before) throw new IllegalStateException("Accepted task exceeds dynamic firing vector");
                dynamic.put(candidate.taskId(), before - count);
                if (before - count <= 0L) activeDynamicTasksByPhase[phaseIndex]--;
                int taskPosition = phase.taskIds().indexOf(candidate.taskId());
                if (taskPosition >= 0 && !phase.taskIds().isEmpty()) {
                    dynamicCursor[phaseIndex] = (taskPosition + 1) % phase.taskIds().size();
                }
            }
        }
        reconciledEmptyCandidates = false;
        refreshTaskState(candidate.taskId());
        maybeCompletePhase(phaseIndex);
        consumeStartupSeed(candidate, inputs, count);
    }

    long startupSeedGeneration(AEKey key) {
        return key == null ? 0L : startupSeedGenerations.getOrDefault(key, 0L);
    }

    long startupSeedGeneration() {
        return startupSeedGeneration;
    }

    Set<AEKey> inputKeys(int taskId) {
        if (taskId < 0 || taskId >= inputKeysByTaskId.size()) return Set.of();
        return inputKeysByTaskId.get(taskId);
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

    /** Read-only, bounded snapshot used only after dispatch has already been classified as stalled. */
    String describeStallState() {
        var text = new StringBuilder()
            .append("completedPhases=").append(completedPhases.cardinality())
            .append('/').append(plan.phases().size())
            .append(" progressBinding=").append(progressByTaskId != null)
            .append(" sharedProgressAliases=").append(describeProgressAliases())
            .append(" phases=[");
        int included = 0;
        int unfinished = 0;
        for (int phaseIndex = 0; phaseIndex < plan.phases().size(); phaseIndex++) {
            if (completedPhases.get(phaseIndex)) continue;
            unfinished++;
            if (included >= MAX_DIAGNOSTIC_PHASES) continue;
            if (included++ > 0) text.append(", ");
            var phase = plan.phases().get(phaseIndex);
            text.append("phase=").append(phaseIndex)
                .append(" type=").append(phase.type())
                .append(" ready=").append(remainingDependencies[phaseIndex] == 0)
                .append(" remainingDependencies=").append(remainingDependencies[phaseIndex])
                .append(" dependencies=").append(phase.dependencies())
                .append(" unfinishedTasks=").append(unfinishedTasksByPhase[phaseIndex]);

            if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps().isEmpty()) {
                int cursor = stepCursor[phaseIndex];
                text.append(" stepCursor=").append(cursor).append('/').append(phase.steps().size());
                if (cursor < phase.steps().size()) {
                    var step = phase.steps().get(cursor);
                    text.append(" currentStep={task=").append(step.taskId())
                        .append(" remainingStep=").append(remainingSteps.get(phaseIndex)[cursor])
                        .append(" taskRemaining=").append(taskRemaining(step.taskId(), null)).append('}');
                }
            } else if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
                text.append(" dynamicCursor=").append(dynamicCursor[phaseIndex])
                    .append(" activeDynamicTasks=").append(activeDynamicTasksByPhase[phaseIndex])
                    .append(" remainingFirings=");
                appendDynamicFirings(text, phaseIndex);
            }
            text.append(" taskRemaining=");
            appendTaskRemaining(text, phase.taskIds());
        }
        if (unfinished > included) {
            text.append(", ... ").append(unfinished - included).append(" phases omitted");
        }
        text.append(']');
        return text.length() <= MAX_DIAGNOSTIC_LENGTH
            ? text.toString() : text.substring(0, MAX_DIAGNOSTIC_LENGTH) + "...";
    }

    private void appendDynamicFirings(StringBuilder text, int phaseIndex) {
        text.append('[');
        int included = 0;
        int remainingEntries = 0;
        for (var entry : remainingDynamicFirings.get(phaseIndex).entrySet()) {
            if (entry.getValue() <= 0L) continue;
            remainingEntries++;
            if (included >= MAX_DIAGNOSTIC_TASKS) continue;
            if (included++ > 0) text.append(',');
            text.append("task=").append(entry.getKey())
                .append(" firing=").append(entry.getValue())
                .append(" taskRemaining=").append(taskRemaining(entry.getKey(), null));
        }
        if (remainingEntries > included) {
            text.append(",...").append(remainingEntries - included).append(" omitted");
        }
        text.append(']');
    }

    private void appendTaskRemaining(StringBuilder text, List<Integer> taskIds) {
        text.append('[');
        int included = 0;
        int unfinished = 0;
        for (int taskId : taskIds) {
            long remaining = taskRemaining(taskId, null);
            if (remaining <= 0L) continue;
            unfinished++;
            if (included >= MAX_DIAGNOSTIC_TASKS) continue;
            if (included++ > 0) text.append(',');
            text.append(taskId).append('=').append(remaining);
        }
        if (unfinished > included) text.append(",...").append(unfinished - included).append(" omitted");
        text.append(']');
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
        long result = totalStartupSeedRemaining(key);
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
        Map<AEKey, Long> totals = protectedStartupSeed(null);
        for (var entry : totals.entrySet()) {
            if (inventory.extract(entry.getKey(), entry.getValue(), Actionable.SIMULATE) < entry.getValue()) {
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
        for (int phaseIndex = 0; phaseIndex < startupSeedRemainingByPhase.size(); phaseIndex++) {
            for (var entry : startupSeedRemainingByPhase.get(phaseIndex).entrySet()) {
                CompoundTag seed = GenericStack.writeTag(registries,
                    new GenericStack(entry.getKey(), entry.getValue()));
                seed.putInt("phase", phaseIndex);
                seeds.add(seed);
            }
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
        runtime.startupSeedRemainingByPhase.forEach(Map::clear);
        ListTag seeds = data.getList("startupSeeds", Tag.TAG_COMPOUND);
        Map<AEKey, Long> legacySeeds = new LinkedHashMap<>();
        for (int i = 0; i < seeds.size(); i++) {
            CompoundTag seed = seeds.getCompound(i);
            GenericStack stack = GenericStack.readTag(registries, seed);
            if (stack != null && stack.amount() > 0L) {
                if (seed.contains("phase", Tag.TAG_INT)) {
                    int phaseIndex = seed.getInt("phase");
                    if (phaseIndex < 0 || phaseIndex >= runtime.plan.phases().size()) {
                        throw new IllegalArgumentException("Persisted startup seed has invalid phase owner");
                    }
                    runtime.startupSeedRemainingByPhase.get(phaseIndex)
                        .merge(stack.what(), stack.amount(), NEMath::saturatingAdd);
                } else {
                    legacySeeds.merge(stack.what(), stack.amount(), NEMath::saturatingAdd);
                }
            }
        }
        runtime.restoreLegacyStartupSeeds(legacySeeds);
        runtime.validateStartupSeedOwnership();
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

    Map<AEKey, Long> protectedStartupSeed(@Nullable DispatchCandidate candidate) {
        int ownerPhase = candidate == null ? -1 : candidate.phaseIndex();
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (int phaseIndex = 0; phaseIndex < startupSeedRemainingByPhase.size(); phaseIndex++) {
            if (phaseIndex == ownerPhase) continue;
            startupSeedRemainingByPhase.get(phaseIndex).forEach(
                (key, amount) -> result.merge(key, amount, NEMath::saturatingAdd));
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    boolean preservesStartupSeeds(DispatchCandidate candidate, List<GenericStack> inputs,
            appeng.crafting.inv.ListCraftingInventory inventory) {
        Map<AEKey, Long> protectedAmounts = protectedStartupSeed(candidate);
        if (protectedAmounts.isEmpty() || inputs.isEmpty()) return true;
        Map<AEKey, Long> required = new LinkedHashMap<>();
        for (GenericStack input : inputs) {
            if (input != null && input.amount() > 0L) {
                required.merge(input.what(), input.amount(), NEMath::saturatingAdd);
            }
        }
        for (var entry : required.entrySet()) {
            long available = Math.max(0L,
                inventory.list.get(entry.getKey()) - protectedAmounts.getOrDefault(entry.getKey(), 0L));
            if (available < entry.getValue()) return false;
        }
        return true;
    }

    private long totalStartupSeedRemaining(AEKey key) {
        long result = 0L;
        for (Map<AEKey, Long> phaseSeeds : startupSeedRemainingByPhase) {
            result = NEMath.saturatingAdd(result, phaseSeeds.getOrDefault(key, 0L));
        }
        return result;
    }

    private void consumeStartupSeed(DispatchCandidate candidate, KeyCounter[] inputs, long count) {
        if (inputs == null) return;
        Map<AEKey, Long> ownedSeeds = startupSeedRemainingByPhase.get(candidate.phaseIndex());
        if (ownedSeeds.isEmpty()) return;
        for (KeyCounter input : inputs) {
            if (input == null) continue;
            for (var entry : input) {
                long consumed = NEMath.saturatingMultiply(entry.getLongValue(), count);
                long reserved = ownedSeeds.getOrDefault(entry.getKey(), 0L);
                if (reserved > 0L && consumed > 0L) {
                    long remaining = Math.max(0L, reserved - consumed);
                    if (remaining != reserved) {
                        ownedSeeds.put(entry.getKey(), remaining);
                        incrementStartupSeedGeneration(entry.getKey());
                    }
                }
            }
        }
        ownedSeeds.entrySet().removeIf(entry -> entry.getValue() <= 0L);
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
        BitSet affectedPhases = new BitSet(plan.phases().size());
        for (int aliasedTaskId : progressAliasesByTaskId[taskId]) {
            refreshSingleTaskState(aliasedTaskId);
            affectedPhases.set(plan.task(aliasedTaskId).phaseIndex());
        }
        for (int phaseIndex = affectedPhases.nextSetBit(0); phaseIndex >= 0;
                phaseIndex = affectedPhases.nextSetBit(phaseIndex + 1)) {
            maybeCompletePhase(phaseIndex);
        }
    }

    private void refreshSingleTaskState(int taskId) {
        boolean unfinished = taskRemaining(taskId, null) > 0L;
        if (unfinished == unfinishedTasks[taskId]) return;
        unfinishedTasks[taskId] = unfinished;
        int phaseIndex = plan.task(taskId).phaseIndex();
        unfinishedTasksByPhase[phaseIndex] += unfinished ? 1 : -1;
    }

    private void refreshPhaseTaskState(int phaseIndex, List<Integer> taskIds) {
        int unfinished = 0;
        for (int taskId : taskIds) {
            if (plan.task(taskId).phaseIndex() != phaseIndex) {
                throw new IllegalStateException("Execution task is assigned to the wrong phase: " + taskId);
            }
            boolean liveUnfinished = taskRemaining(taskId, null) > 0L;
            unfinishedTasks[taskId] = liveUnfinished;
            if (liveUnfinished) unfinished++;
        }
        unfinishedTasksByPhase[phaseIndex] = unfinished;
    }

    private String describeProgressCache() {
        var text = new StringBuilder("completed=")
            .append(completedPhases.cardinality()).append('/').append(plan.phases().size())
            .append(" mismatches=[");
        int mismatches = 0;
        int included = 0;
        for (int phaseIndex = 0; phaseIndex < plan.phases().size(); phaseIndex++) {
            int actual = actualUnfinishedTasks(phaseIndex);
            int cached = unfinishedTasksByPhase[phaseIndex];
            if (actual == cached) continue;
            mismatches++;
            if (included >= MAX_DIAGNOSTIC_PHASES) continue;
            if (included++ > 0) text.append(',');
            text.append("phase=").append(phaseIndex)
                .append(" cached=").append(cached).append(" actual=").append(actual);
        }
        if (mismatches > included) text.append(",...").append(mismatches - included).append(" omitted");
        text.append("] remainingDependencies=").append(Arrays.toString(remainingDependencies));
        return text.length() <= MAX_DIAGNOSTIC_LENGTH
            ? text.toString() : text.substring(0, MAX_DIAGNOSTIC_LENGTH) + "...";
    }

    private int actualUnfinishedTasks(int phaseIndex) {
        int actual = 0;
        for (int taskId : plan.phases().get(phaseIndex).taskIds()) {
            if (taskRemaining(taskId, null) > 0L) actual++;
        }
        return actual;
    }

    private String describeProgressAliases() {
        if (progressByTaskId == null) return "[]";
        var text = new StringBuilder("[");
        int groups = 0;
        int included = 0;
        for (int taskId = 0; taskId < progressAliasesByTaskId.length; taskId++) {
            int[] aliases = progressAliasesByTaskId[taskId];
            if (aliases.length <= 1 || aliases[0] != taskId) continue;
            groups++;
            if (included >= MAX_DIAGNOSTIC_TASKS) continue;
            if (included++ > 0) text.append(',');
            appendProgressAlias(text, aliases);
        }
        if (groups > included) text.append(",...").append(groups - included).append(" groups omitted");
        return text.append(']').toString();
    }

    private void appendProgressAlias(StringBuilder text, int[] aliases) {
        text.append("identity=").append(System.identityHashCode(progressByTaskId[aliases[0]]))
            .append(" tasks=[");
        int limit = Math.min(aliases.length, MAX_DIAGNOSTIC_TASKS);
        for (int index = 0; index < limit; index++) {
            if (index > 0) text.append(',');
            text.append(aliases[index]);
        }
        if (aliases.length > limit) text.append(",...").append(aliases.length - limit).append(" omitted");
        text.append("] phases=[");
        for (int index = 0; index < limit; index++) {
            if (index > 0) text.append(',');
            text.append(plan.task(aliases[index]).phaseIndex());
        }
        if (aliases.length > limit) text.append(",...");
        text.append(']');
    }

    private void logSharedProgressAliases() {
        if (!NEConfig.ecoDispatchWatchdogDebug || progressByTaskId == null) return;
        String aliases = describeProgressAliases();
        if (!"[]".equals(aliases)) LOGGER.warn("[ECO Execution] shared TaskProgress aliases={}", aliases);
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
        Map<AEKey, Long> releasedSeeds = startupSeedRemainingByPhase.get(phaseIndex);
        if (!releasedSeeds.isEmpty()) {
            for (AEKey key : releasedSeeds.keySet()) incrementStartupSeedGeneration(key);
            releasedSeeds.clear();
        }
        for (int dependent : dependentsByPhase.get(phaseIndex)) {
            if (remainingDependencies[dependent] > 0) remainingDependencies[dependent]--;
        }
    }

    private void incrementStartupSeedGeneration(AEKey key) {
        if (key == null) return;
        long current = startupSeedGenerations.getOrDefault(key, 0L);
        if (current != Long.MAX_VALUE) startupSeedGenerations.put(key, current + 1L);
        if (startupSeedGeneration != Long.MAX_VALUE) startupSeedGeneration++;
    }

    private void rebuildProgressState() {
        reconcileCycleProgress();
        // With a live binding, completion is derived from task progress and the rebuilt witness rather than trusted
        // as an independent persisted source of truth. The compatibility runtime has no such authoritative counter.
        if (progressByTaskId != null) completedPhases.clear();
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

    private static int[][] createProgressAliases(
            @Nullable ExecutingCraftingJob.TaskProgress[] progressByTaskId, int taskCount) {
        int[][] result = new int[taskCount][];
        if (progressByTaskId == null) {
            for (int taskId = 0; taskId < taskCount; taskId++) result[taskId] = new int[] {taskId};
            return result;
        }

        var aliasesByProgress = new IdentityHashMap<ExecutingCraftingJob.TaskProgress, List<Integer>>();
        for (int taskId = 0; taskId < progressByTaskId.length; taskId++) {
            aliasesByProgress.computeIfAbsent(progressByTaskId[taskId], ignored -> new ArrayList<>()).add(taskId);
        }
        for (var aliases : aliasesByProgress.values()) {
            int[] taskIds = aliases.stream().mapToInt(Integer::intValue).toArray();
            for (int taskId : taskIds) result[taskId] = taskIds;
        }
        return result;
    }

    private void rejectSharedProgressAliases() {
        if (progressByTaskId == null) return;
        for (int taskId = 0; taskId < progressAliasesByTaskId.length; taskId++) {
            int[] aliases = progressAliasesByTaskId[taskId];
            if (aliases.length > 1 && aliases[0] == taskId) {
                throw new IllegalArgumentException("Execution tasks share one aggregate TaskProgress: "
                    + Arrays.toString(aliases));
            }
        }
    }

    /** Rebuilds cycle-only progress from the one-to-one live task counters that record accepted provider pushes. */
    private void reconcileCycleProgress() {
        if (progressByTaskId == null) return;
        boolean changed = false;
        for (int phaseIndex = 0; phaseIndex < plan.phases().size(); phaseIndex++) {
            var phase = plan.phases().get(phaseIndex);
            if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps().isEmpty()) {
                long[] steps = remainingSteps.get(phaseIndex);
                long[] completedByTask = new long[plan.tasks().size()];
                for (int taskId : phase.taskIds()) {
                    completedByTask[taskId] = Math.max(0L,
                        plan.task(taskId).totalCount() - taskRemaining(taskId, null));
                }
                boolean prefixComplete = true;
                for (int index = 0; index < phase.steps().size(); index++) {
                    var step = phase.steps().get(index);
                    long consumed = prefixComplete
                        ? Math.min(step.count(), completedByTask[step.taskId()]) : 0L;
                    long rebuilt = step.count() - consumed;
                    completedByTask[step.taskId()] -= consumed;
                    if (rebuilt > 0L) prefixComplete = false;
                    changed |= steps[index] != rebuilt;
                    steps[index] = rebuilt;
                }
                stepCursor[phaseIndex] = 0;
                advanceFinishedSteps(phaseIndex);
            } else if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
                Map<Integer, Long> dynamic = remainingDynamicFirings.get(phaseIndex);
                dynamic.keySet().removeIf(taskId -> !phase.dynamicFirings().containsKey(taskId));
                for (var initial : phase.dynamicFirings().entrySet()) {
                    int taskId = initial.getKey();
                    long completed = Math.max(0L,
                        plan.task(taskId).totalCount() - taskRemaining(taskId, null));
                    long rebuilt = Math.max(0L, initial.getValue() - completed);
                    if (dynamic.getOrDefault(taskId, -1L) != rebuilt) {
                        dynamic.put(taskId, rebuilt);
                        changed = true;
                    }
                }
            }
        }
        if (changed && NEConfig.ecoDispatchWatchdogDebug) {
            LOGGER.warn("[ECO Execution] reconciled cycle witness counters to accepted AE2 task progress");
        }
    }

    private void restoreLegacyStartupSeeds(Map<AEKey, Long> legacySeeds) {
        for (var entry : legacySeeds.entrySet()) {
            List<Integer> owners = new ArrayList<>();
            long plannedTotal = 0L;
            for (int phaseIndex = 0; phaseIndex < plan.phases().size(); phaseIndex++) {
                boolean hasLiveWork = progressByTaskId == null ? !completedPhases.get(phaseIndex) : false;
                if (progressByTaskId != null) {
                    for (int taskId : plan.phases().get(phaseIndex).taskIds()) {
                        if (taskRemaining(taskId, null) > 0L) {
                            hasLiveWork = true;
                            break;
                        }
                    }
                }
                if (!hasLiveWork) continue;
                long planned = plan.phases().get(phaseIndex).initialSeed().getOrDefault(entry.getKey(), 0L);
                if (planned <= 0L) continue;
                owners.add(phaseIndex);
                plannedTotal = NEMath.saturatingAdd(plannedTotal, planned);
            }
            if (owners.isEmpty() || entry.getValue() > plannedTotal) {
                throw new IllegalArgumentException("Persisted startup seed exceeds execution-plan ownership");
            }
            if (owners.size() > 1 && entry.getValue() != plannedTotal) {
                // The legacy format aggregated equal keys across phases. Once any owner consumed only part of that
                // total, its remaining ownership is unknowable; suspending is safer than assigning another phase's
                // seed and recreating the cross-phase theft this format is being replaced to prevent.
                throw new IllegalArgumentException("Legacy startup seed has ambiguous phase ownership");
            }
            long remaining = entry.getValue();
            for (int phaseIndex : owners) {
                long assigned = Math.min(remaining,
                    plan.phases().get(phaseIndex).initialSeed().getOrDefault(entry.getKey(), 0L));
                startupSeedRemainingByPhase.get(phaseIndex).put(entry.getKey(), assigned);
                remaining -= assigned;
            }
        }
    }

    private void validateStartupSeedOwnership() {
        for (int phaseIndex = 0; phaseIndex < startupSeedRemainingByPhase.size(); phaseIndex++) {
            Map<AEKey, Long> planned = plan.phases().get(phaseIndex).initialSeed();
            for (var entry : startupSeedRemainingByPhase.get(phaseIndex).entrySet()) {
                if (entry.getValue() <= 0L || entry.getValue() > planned.getOrDefault(entry.getKey(), 0L)) {
                    throw new IllegalArgumentException("Persisted startup seed exceeds its phase ownership");
                }
            }
        }
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
