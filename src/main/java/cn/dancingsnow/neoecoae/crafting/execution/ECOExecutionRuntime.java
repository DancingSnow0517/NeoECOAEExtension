package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import it.unimi.dsi.fastutil.ints.Int2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
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
    private final List<long[]> remainingLaps;
    // Accepted future copies of each repeated step. They become current progress as laps advance.
    private final List<long[]> aheadSteps;
    private final List<int[]> batchGroupEnds;
    private final List<Int2LongLinkedOpenHashMap> remainingDynamicFirings;
    private final BitSet completedPhases;
    private final int[] unfinishedTasksByPhase;
    private final boolean[] unfinishedTasks;
    private final int[] activeDynamicTasksByPhase;
    private final int[] remainingDependencies;
    private final int[] activeTaskBuffer;
    private final List<IntList> dependentsByPhase;
    // Reused by the dispatch loop; callers consume the snapshot before requesting the next one.
    private final List<DispatchCandidate> candidateBuffer = new ArrayList<>();
    private final List<Object2LongLinkedOpenHashMap<AEKey>> startupSeedRemainingByPhase;
    // Seeds protected from each owner phase, indexed by owner phase + 1 (slot 0 is "no owner"). Dispatch asks for
    // the same snapshot once per candidate, so it is built lazily and dropped only when a seed amount changes.
    private final Map<AEKey, Long>[] protectedSeedByOwner;
    private boolean protectedSeedCacheStale;
    private final Object2LongOpenHashMap<AEKey> startupSeedGenerations = new Object2LongOpenHashMap<>();
    private long startupSeedGeneration;
    private boolean reconciledEmptyCandidates;

    public ECOExecutionRuntime(ECOExecutionPlan plan, Map<Integer, IPatternDetails> patternsById) {
        this(plan, toPatternArray(plan, patternsById), null);
    }

    ECOExecutionRuntime(ECOExecutionPlan plan, Map<Integer, IPatternDetails> patternsById,
            ExecutingCraftingJob.TaskProgress[] progressByTaskId) {
        this(plan, toPatternArray(plan, patternsById), progressByTaskId);
    }

    @SuppressWarnings("unchecked")
    ECOExecutionRuntime(ECOExecutionPlan plan, IPatternDetails[] patternsById,
            ExecutingCraftingJob.TaskProgress[] progressByTaskId) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.patternsById = Objects.requireNonNull(patternsById, "patternsById");
        if (this.patternsById.length != plan.tasks().size()) {
            throw new IllegalArgumentException("Execution pattern binding shape changed");
        }
        this.progressByTaskId = progressByTaskId;
        if (this.progressByTaskId != null && this.progressByTaskId.length != plan.tasks().size()) {
            throw new IllegalArgumentException("Execution progress binding shape changed");
        }
        this.stepCursor = new int[plan.phases().size()];
        this.dynamicCursor = new int[plan.phases().size()];
        this.remainingSteps = new ArrayList<>(plan.phases().size());
        this.remainingLaps = new ArrayList<>(plan.phases().size());
        this.aheadSteps = new ArrayList<>(plan.phases().size());
        this.batchGroupEnds = new ArrayList<>(plan.phases().size());
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
        this.protectedSeedByOwner = (Map<AEKey, Long>[]) new Map[plan.phases().size() + 1];

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
            long[] laps = new long[steps.length];
            for (int i = 0; i < laps.length; i++) laps[i] = phase.steps().get(i).repetitions();
            remainingLaps.add(laps);
            aheadSteps.add(new long[steps.length]);
            batchGroupEnds.add(batchGroupEnds(phase));
            remainingDynamicFirings.add(new Int2LongLinkedOpenHashMap(phase.dynamicFirings()));
            startupSeedRemainingByPhase.add(new Object2LongLinkedOpenHashMap<>(phase.initialSeed()));
        }
        for (IPatternDetails pattern : this.patternsById) {
            inputKeysByTaskId.add(inputKeys(pattern));
        }
        rebuildProgressState();
        logSharedProgressAliases();
    }

    public ECOExecutionPlan plan() {
        return plan;
    }

    /** Candidate returned by the scheduler for one provider attempt. */
    public record DispatchCandidate(int taskId, int phaseIndex, IPatternDetails pattern,
            long maxDispatchCount, boolean blocksOrderedPhase, int orderedStepIndex) {
        public DispatchCandidate(int taskId, int phaseIndex, IPatternDetails pattern,
                long maxDispatchCount, boolean blocksOrderedPhase) {
            this(taskId, phaseIndex, pattern, maxDispatchCount, blocksOrderedPhase, -1);
        }
        public DispatchCandidate {
            Objects.requireNonNull(pattern, "pattern");
            if (taskId < 0 || phaseIndex < 0 || maxDispatchCount <= 0L) {
                throw new IllegalArgumentException("Invalid execution candidate");
            }
        }
    }

    String describeSchedulingState() {
        var text = new StringBuilder("completedPhases=")
            .append(completedPhases.cardinality()).append('/').append(plan.phases().size()).append(" pending=[");
        int included = 0;
        for (var phase : plan.phases()) {
            int phaseIndex = phase.index();
            if (completedPhases.get(phaseIndex)) continue;
            if (included >= 16) {
                text.append(",...");
                break;
            }
            if (included++ > 0) text.append(',');
            text.append(phaseIndex).append(':').append(phase.type())
                .append(" dependencies=").append(remainingDependencies[phaseIndex])
                .append(" unfinishedTasks=").append(unfinishedTasksByPhase[phaseIndex])
                .append(" startupSeedKeys=").append(startupSeedRemainingByPhase.get(phaseIndex).size());
            if (phase.type() == ECOExecutionSchedule.Type.CYCLE) {
                text.append(" cycleStep=").append(stepCursor[phaseIndex]).append('/').append(phase.steps().size());
            } else if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
                text.append(" dynamicFirings=").append(remainingDynamicFirings.get(phaseIndex));
            }
        }
        text.append(']');
        return text.length() <= 2048 ? text.toString() : text.substring(0, 2048) + "...";
    }

    String describeDispatchState(DispatchCandidate candidate) {
        int phaseIndex = candidate.phaseIndex();
        if (phaseIndex < 0 || phaseIndex >= plan.phases().size()) return "phase=<invalid>";
        var phase = plan.phases().get(phaseIndex);
        var text = new StringBuilder("phase=").append(phaseIndex).append(':').append(phase.type())
            .append(" completed=").append(completedPhases.get(phaseIndex))
            .append(" dependencies=").append(remainingDependencies[phaseIndex])
            .append(" unfinishedTasks=").append(unfinishedTasksByPhase[phaseIndex])
            .append(" startupSeeds=").append(startupSeedRemainingByPhase.get(phaseIndex));
        if (phase.type() == ECOExecutionSchedule.Type.CYCLE) {
            text.append(" cycleStep=").append(stepCursor[phaseIndex]).append('/').append(phase.steps().size());
            if (stepCursor[phaseIndex] < remainingSteps.get(phaseIndex).length) {
                text.append(" stepRemaining=").append(remainingSteps.get(phaseIndex)[stepCursor[phaseIndex]]);
            }
        } else if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
            text.append(" dynamicFirings=").append(remainingDynamicFirings.get(phaseIndex));
        }
        return text.length() <= 2048 ? text.toString() : text.substring(0, 2048) + "...";
    }

    /**
     * Returns all currently legal candidates in deterministic phase/task order.
     *
     * <p>An ordered phase contributes its current step, including future copies in a fixed processing circuit.
     * Physical stock and competing consumers bound the batch at input resolution. Other ready phases remain
     * eligible when this provider is busy. Dynamic phases rotate after an accepted firing.</p>
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
                    long allowed = Math.min(orderedAllowance(phaseIndex, stepCursor[phaseIndex]), remaining);
                    if (allowed > 0L) {
                        result.add(new DispatchCandidate(step.taskId(), phaseIndex, pattern(step.taskId()),
                            allowed, true, stepCursor[phaseIndex]));
                    }
                } else {
                    addAllPhaseTasks(result, phaseIndex, phase.taskIds(), remainingTasks, false);
                }
                continue;
            }

            if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
                Int2LongMap dynamic = remainingDynamicFirings.get(phaseIndex);
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
                for (int offset = 0; offset < activeCount; offset++) {
                    int taskId = activeTaskBuffer[(start + offset) % activeCount];
                    long allowed = Math.min(dynamic.getOrDefault(taskId, 0L), taskRemaining(taskId, remainingTasks));
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
    java.math.BigInteger exactAllowance(DispatchCandidate candidate, java.math.BigInteger remaining) {
        var phase = plan.phases().get(candidate.phaseIndex());
        boolean witnessed = phase.type() == ECOExecutionSchedule.Type.CYCLE
            && stepCursor[candidate.phaseIndex()] < phase.steps().size()
            || phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE
                && hasDynamicFirings(remainingDynamicFirings.get(candidate.phaseIndex()));
        return witnessed ? remaining.min(java.math.BigInteger.valueOf(candidate.maxDispatchCount())) : remaining;
    }

    void onAcceptedExact(DispatchCandidate candidate, java.math.BigInteger count, KeyCounter[] inputs) {
        // Cycle witnesses are bounded by exactAllowance; unrestricted phase progress is owned by TaskProgress.
        onAccepted(candidate, count.min(java.math.BigInteger.valueOf(candidate.maxDispatchCount())).longValueExact(), inputs);
    }

    public void onAccepted(DispatchCandidate candidate, long count, KeyCounter[] inputs) {
        if (count <= 0L || count > candidate.maxDispatchCount()) {
            throw new IllegalArgumentException("Accepted dispatch exceeds scheduler allowance");
        }
        int phaseIndex = candidate.phaseIndex();
        var phase = plan.phases().get(phaseIndex);
        if (candidate.orderedStepIndex() >= 0 && stepCursor[phaseIndex] >= phase.steps().size()) {
            throw new IllegalArgumentException("Accepted ordered circuit is already complete");
        }
        if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps().isEmpty()
                && stepCursor[phaseIndex] < phase.steps().size()) {
            int index = candidate.orderedStepIndex() < 0 ? stepCursor[phaseIndex] : candidate.orderedStepIndex();
            if (index >= phase.steps().size()) throw new IllegalArgumentException("Invalid accepted ordered step");
            var step = phase.steps().get(index);
            if (step.taskId() != candidate.taskId()) {
                throw new IllegalStateException("Accepted task does not match its ordered step");
            }
            int groupEnd = batchGroupEnds.get(phaseIndex)[index];
            if (index != stepCursor[phaseIndex] && (groupEnd < 0
                    || batchGroupEnds.get(phaseIndex)[stepCursor[phaseIndex]] != groupEnd)) {
                throw new IllegalStateException("Accepted task is outside the active ordered circuit");
            }
            if (count > orderedAllowance(phaseIndex, index)) {
                throw new IllegalArgumentException("Accepted dispatch exceeds remaining ordered work");
            }
            long[] steps = remainingSteps.get(phaseIndex);
            long current = Math.min(steps[index], count);
            steps[index] -= current;
            aheadSteps.get(phaseIndex)[index] = Math.addExact(aheadSteps.get(phaseIndex)[index], count - current);
            advanceFinishedSteps(phaseIndex);
        } else if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
            Int2LongMap dynamic = remainingDynamicFirings.get(phaseIndex);
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

    /**
     * Commits a virtual execution together with its bound task progress. Virtual providers do not expose a
     * concrete input container at this boundary, so startup-seed accounting is deliberately left unchanged; the
     * provider has already performed its own logical input accounting.
     */
    public void onVirtualAccepted(DispatchCandidate candidate, long count) {
        requireProgressBinding();
        if (count <= 0L || count > candidate.maxDispatchCount()) {
            throw new IllegalArgumentException("Accepted virtual dispatch exceeds scheduler allowance");
        }
        var progress = progressByTaskId[candidate.taskId()];
        if (progress == null || progress.value < count) {
            throw new IllegalArgumentException("Accepted virtual dispatch exceeds task progress");
        }
        progress.accept(count);
        onAccepted(candidate, count, new KeyCounter[0]);
    }

    long startupSeedGeneration(AEKey key) {
        return key == null ? 0L : startupSeedGenerations.getLong(key);
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
        for (var entry : remainingDynamicFirings.get(phaseIndex).int2LongEntrySet()) {
            if (entry.getLongValue() <= 0L) continue;
            remainingEntries++;
            if (included >= MAX_DIAGNOSTIC_TASKS) continue;
            if (included++ > 0) text.append(',');
            text.append("task=").append(entry.getIntKey())
                .append(" firing=").append(entry.getLongValue())
                .append(" taskRemaining=").append(taskRemaining(entry.getIntKey(), null));
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
            phase.putLongArray("laps", remainingLaps.get(phaseIndex));
            phase.putLongArray("ahead", aheadSteps.get(phaseIndex));
            ListTag dynamic = new ListTag();
            for (var entry : remainingDynamicFirings.get(phaseIndex).int2LongEntrySet()) {
                CompoundTag firing = new CompoundTag();
                firing.putInt("task", entry.getIntKey());
                firing.putLong("count", entry.getLongValue());
                dynamic.add(firing);
            }
            phase.put("dynamic", dynamic);
            phases.add(phase);
        }
        data.put("phases", phases);
        data.putIntArray("completed", completedPhases.stream().toArray());
        ListTag seeds = new ListTag();
        for (int phaseIndex = 0; phaseIndex < startupSeedRemainingByPhase.size(); phaseIndex++) {
            for (var entry : startupSeedRemainingByPhase.get(phaseIndex).object2LongEntrySet()) {
                CompoundTag seed = GenericStack.writeTag(registries,
                    new GenericStack(entry.getKey(), entry.getLongValue()));
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
            if (phase.contains("laps")) {
                long[] laps = phase.getLongArray("laps");
                if (laps.length != steps.length) throw new IllegalArgumentException("Execution circuit shape changed");
                for (int i = 0; i < laps.length; i++) {
                    if (laps[i] < 1L || laps[i] > plan.phases().get(phaseIndex).steps().get(i).repetitions()) {
                        throw new IllegalArgumentException("Invalid remaining circuit laps");
                    }
                }
                System.arraycopy(laps, 0, runtime.remainingLaps.get(phaseIndex), 0, laps.length);
            } else if (plan.phases().get(phaseIndex).steps().stream().anyMatch(step -> step.repetitions() > 1L)) {
                throw new IllegalArgumentException("Repeated circuit is missing its runtime cursor");
            }
            if (phase.contains("ahead")) {
                long[] ahead = phase.getLongArray("ahead");
                if (ahead.length != steps.length) throw new IllegalArgumentException("Execution ahead-step shape changed");
                for (int i = 0; i < ahead.length; i++) {
                    int end = runtime.batchGroupEnds.get(phaseIndex)[i];
                    long maximum = end < 0 ? 0L : Math.multiplyExact(runtime.remainingLaps.get(phaseIndex)[end] - 1L,
                        plan.phases().get(phaseIndex).steps().get(i).count());
                    if (ahead[i] < 0L || ahead[i] > maximum) throw new IllegalArgumentException("Invalid accepted-ahead work");
                }
                System.arraycopy(ahead, 0, runtime.aheadSteps.get(phaseIndex), 0, ahead.length);
            }
            Int2LongMap dynamic = runtime.remainingDynamicFirings.get(phaseIndex);
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
                        .mergeLong(stack.what(), stack.amount(), NEMath::saturatingAdd);
                } else {
                    legacySeeds.merge(stack.what(), stack.amount(), NEMath::saturatingAdd);
                }
            }
        }
        runtime.restoreLegacyStartupSeeds(legacySeeds);
        runtime.protectedSeedCacheStale = true;
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
        if (progressByTaskId != null && progressByTaskId.length > 0 && progressByTaskId[0].isExact()) {
            // DAG producers stream into ready consumers in the same order. Physical input extraction gates
            // dispatch; solved cycles retain their completion barrier so their feedback seed cannot escape.
            for (int dependency : plan.phases().get(phaseIndex).dependencies()) {
                if (plan.phases().get(dependency).type() != ECOExecutionSchedule.Type.DAG
                        && !completedPhases.get(dependency)) return false;
            }
            return true;
        }
        return remainingDependencies[phaseIndex] == 0;
    }

    private void advanceFinishedSteps(int phaseIndex) {
        var steps = plan.phases().get(phaseIndex).steps();
        long[] remaining = remainingSteps.get(phaseIndex);
        while (stepCursor[phaseIndex] < steps.size() && remaining[stepCursor[phaseIndex]] == 0L) {
            int end = stepCursor[phaseIndex];
            var step = steps.get(end);
            long[] laps = remainingLaps.get(phaseIndex);
            if (laps[end] > 1L) {
                int start = end - step.repeatWidth() + 1;
                long[] ahead = aheadSteps.get(phaseIndex);
                long skip = laps[end] - 1L;
                for (int i = start; i <= end; i++) skip = Math.min(skip, ahead[i] / steps.get(i).count());
                for (int i = start; i <= end; i++) ahead[i] -= skip * steps.get(i).count();
                laps[end] -= skip;
                if (laps[end] == 1L) {
                    stepCursor[phaseIndex]++;
                    continue;
                }
                laps[end]--;
                for (int i = start; i <= end; i++) {
                    long consumed = Math.min(ahead[i], steps.get(i).count());
                    remaining[i] = steps.get(i).count() - consumed;
                    ahead[i] -= consumed;
                }
                stepCursor[phaseIndex] = start;
            } else {
                stepCursor[phaseIndex]++;
            }
        }
    }

    private long orderedAllowance(int phaseIndex, int index) {
        long current = remainingSteps.get(phaseIndex)[index];
        int end = batchGroupEnds.get(phaseIndex)[index];
        if (end < 0) return current;
        long future = Math.multiplyExact(remainingLaps.get(phaseIndex)[end] - 1L,
            plan.phases().get(phaseIndex).steps().get(index).count());
        return Math.addExact(current, future - aheadSteps.get(phaseIndex)[index]);
    }

    private int[] batchGroupEnds(ECOExecutionPlan.PhaseSpec phase) {
        int[] ends = new int[phase.steps().size()];
        Arrays.fill(ends, -1);
        for (int end = 0; end < ends.length; end++) {
            var last = phase.steps().get(end);
            if (last.repetitions() <= 1L) continue;
            int start = end - last.repeatWidth() + 1;
            var tasks = new java.util.HashSet<Integer>();
            boolean safe = true;
            for (int i = start; i <= end; i++) {
                int taskId = phase.steps().get(i).taskId();
                safe &= tasks.add(taskId) && fixedProcessingInputs(pattern(taskId));
            }
            if (safe) Arrays.fill(ends, start, end + 1, end);
        }
        return ends;
    }

    // Reordering fixed processing copies is safe only while competing consumers retain their inputs.
    // Unknown substitutions and crafting/tool mutations continue to follow the original ordered witness.
    private static boolean fixedProcessingInputs(IPatternDetails pattern) {
        try {
            if (!pattern.supportsPushInputsToExternalInventory() || pattern.getInputs() == null) return false;
            for (var input : pattern.getInputs()) {
                if (input == null) return false;
                var possible = input.getPossibleInputs();
                if (possible == null || possible.length != 1 || possible[0] == null
                        || possible[0].amount() <= 0L || input.getMultiplier() <= 0L) return false;
            }
        } catch (RuntimeException unavailable) {
            return false;
        }
        return true;
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

    /** Keep the witnessed first firing, but let surplus stock fund additional copies without starving peers. */
    long limitCycleBatch(DispatchCandidate candidate, KeyCounter[] inputs,
            appeng.crafting.inv.ListCraftingInventory inventory, long upper) {
        int phaseIndex = candidate.phaseIndex();
        var phase = plan.phases().get(phaseIndex);
        if (phase.type() == ECOExecutionSchedule.Type.DAG) return upper;
        int index = candidate.orderedStepIndex();
        int end = index < 0 ? -1 : batchGroupEnds.get(phaseIndex)[index];
        if (phase.type() == ECOExecutionSchedule.Type.CYCLE && end < 0) return upper;
        long witnessed = phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE ? 1L
            : remainingSteps.get(phaseIndex)[index];
        var totals = new Object2LongOpenHashMap<AEKey>();
        for (var input : inputs) {
            for (var entry : input) {
                if (entry.getLongValue() > 0L) totals.mergeLong(entry.getKey(), entry.getLongValue(), Math::addExact);
            }
        }
        var reserved = new LinkedHashMap<AEKey, java.math.BigInteger>();
        boolean dynamic = phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE;
        int start = dynamic ? 0 : end - phase.steps().get(end).repeatWidth() + 1;
        int finish = dynamic ? phase.taskIds().size() : end + 1;
        for (int position = start; position < finish; position++) {
            int otherId = dynamic ? phase.taskIds().get(position) : phase.steps().get(position).taskId();
            if (otherId == candidate.taskId()) continue;
            long pending = dynamic ? remainingDynamicFirings.get(phaseIndex).getOrDefault(otherId, 0L)
                : orderedAllowance(phaseIndex, position);
            if (pending <= 0L) continue;
            var other = pattern(otherId);
            for (AEKey key : totals.keySet()) {
                if (!inputKeysByTaskId.get(otherId).contains(key)) continue;
                if (!fixedProcessingInputs(other)) return Math.min(upper, witnessed);
                for (var input : other.getInputs()) {
                    var selected = input.getPossibleInputs()[0];
                    if (key.equals(selected.what())) {
                        var amount = java.math.BigInteger.valueOf(selected.amount())
                            .multiply(java.math.BigInteger.valueOf(input.getMultiplier()))
                            .multiply(java.math.BigInteger.valueOf(pending));
                        reserved.merge(key, amount, java.math.BigInteger::add);
                    }
                }
            }
        }
        if (reserved.isEmpty()) return upper;
        var protectedSeeds = protectedStartupSeed(candidate);
        long safe = upper;
        for (var entry : reserved.entrySet()) {
            var available = cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory.amount(inventory, entry.getKey())
                .subtract(java.math.BigInteger.valueOf(protectedSeeds.getOrDefault(entry.getKey(), 0L)))
                .subtract(entry.getValue()).max(java.math.BigInteger.ZERO);
            long crafts = available.divide(java.math.BigInteger.valueOf(totals.getLong(entry.getKey())))
                .min(java.math.BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
            safe = Math.min(safe, crafts);
        }
        return Math.min(upper, Math.max(witnessed, safe));
    }

    private static boolean hasDynamicFirings(Int2LongMap dynamic) {
        for (LongIterator it = dynamic.values().iterator(); it.hasNext(); ) if (it.nextLong() > 0L) return true;
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
        if (pattern == null || key == null) return 0L;
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

    private static long outputAmount(IPatternDetails pattern, AEKey key) {
        if (pattern == null || key == null) return 0L;
        long result = 0L;
        try {
            for (GenericStack output : pattern.getOutputs()) {
                if (output != null && key.equals(output.what()) && output.amount() > 0L) {
                    result = NEMath.saturatingAdd(result, output.amount());
                }
            }
        } catch (RuntimeException ignored) {
            return 0L;
        }
        return result;
    }

    Map<AEKey, Long> protectedStartupSeed(@Nullable DispatchCandidate candidate) {
        int ownerPhase = candidate == null ? -1 : candidate.phaseIndex();
        if (ownerPhase < -1 || ownerPhase >= startupSeedRemainingByPhase.size()) {
            return buildProtectedStartupSeed(ownerPhase);
        }
        // Dynamic cycles have no solver-provided witness order. Protect their startup stock from a
        // consumer until a currently live task that can produce that key gets the first chance to run.
        // Ordered cycles keep the old phase-level ownership rule because their witness already fixes
        // which task is allowed to consume the seed first.
        if (candidate != null
                && plan.phases().get(ownerPhase).type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
            return buildProtectedDynamicStartupSeed(candidate);
        }
        if (protectedSeedCacheStale) {
            Arrays.fill(protectedSeedByOwner, null);
            protectedSeedCacheStale = false;
        }
        Map<AEKey, Long> cached = protectedSeedByOwner[ownerPhase + 1];
        if (cached == null) {
            cached = buildProtectedStartupSeed(ownerPhase);
            protectedSeedByOwner[ownerPhase + 1] = cached;
        }
        return cached;
    }

    private Map<AEKey, Long> buildProtectedStartupSeed(int ownerPhase) {
        Object2LongLinkedOpenHashMap<AEKey> result = new Object2LongLinkedOpenHashMap<>();
        for (int phaseIndex = 0; phaseIndex < startupSeedRemainingByPhase.size(); phaseIndex++) {
            if (phaseIndex == ownerPhase) continue;
            for (var entry : startupSeedRemainingByPhase.get(phaseIndex).object2LongEntrySet()) {
                result.mergeLong(entry.getKey(), entry.getLongValue(), NEMath::saturatingAdd);
            }
        }
        return result.isEmpty() ? Map.of() : result;
    }

    private Map<AEKey, Long> buildProtectedDynamicStartupSeed(DispatchCandidate candidate) {
        Object2LongLinkedOpenHashMap<AEKey> result = new Object2LongLinkedOpenHashMap<>();
        int ownerPhase = candidate.phaseIndex();
        for (int phaseIndex = 0; phaseIndex < startupSeedRemainingByPhase.size(); phaseIndex++) {
            for (var entry : startupSeedRemainingByPhase.get(phaseIndex).object2LongEntrySet()) {
                if (phaseIndex != ownerPhase || !dynamicCandidateMayConsumeSeed(candidate, entry.getKey())) {
                    result.mergeLong(entry.getKey(), entry.getLongValue(), NEMath::saturatingAdd);
                }
            }
        }
        return result.isEmpty() ? Map.of() : result;
    }

    /**
     * Selects a safe first consumer for a dynamic cycle's startup key. If a live non-negative producer exists,
     * only such producers may consume the reserved copy. This prevents a terminal consumer from taking the only
     * seed before the producer that makes the rest of the cycle reachable. If no producer exists, the key is a
     * finite external input and may be consumed by the first runnable task.
     */
    private boolean dynamicCandidateMayConsumeSeed(DispatchCandidate candidate, AEKey key) {
        var phase = plan.phases().get(candidate.phaseIndex());
        IPatternDetails actual = pattern(candidate.taskId());
        long candidateInput = inputAmount(actual, key, 1L);
        if (candidateInput <= 0L) return true;
        long candidateOutput = outputAmount(actual, key);
        boolean activeProducer = false;
        boolean nonNegativeProducer = false;
        for (int taskId : phase.taskIds()) {
            long live = progressByTaskId == null
                    ? remainingDynamicFirings.get(candidate.phaseIndex()).getOrDefault(taskId, 0L)
                    : taskRemaining(taskId, null);
            if (remainingDynamicFirings.get(candidate.phaseIndex()).getOrDefault(taskId, 0L) <= 0L
                    || live <= 0L) continue;
            IPatternDetails other = pattern(taskId);
            long input = inputAmount(other, key, 1L);
            long output = outputAmount(other, key);
            if (output <= 0L) continue;
            activeProducer = true;
            if (output >= input) nonNegativeProducer = true;
        }
        if (!activeProducer) return true;
        return candidateOutput > 0L && (candidateOutput >= candidateInput || !nonNegativeProducer);
    }

    boolean preservesStartupSeeds(DispatchCandidate candidate, List<GenericStack> inputs,
            appeng.crafting.inv.ListCraftingInventory inventory) {
        Map<AEKey, Long> protectedAmounts = protectedStartupSeed(candidate);
        if (protectedAmounts.isEmpty() || inputs.isEmpty()) return true;
        // Input lists are short, so summing duplicates in place is cheaper than building a map per candidate.
        outer:
        for (int i = 0; i < inputs.size(); i++) {
            GenericStack input = inputs.get(i);
            if (input == null || input.amount() <= 0L) continue;
            AEKey key = input.what();
            for (int j = 0; j < i; j++) {
                GenericStack earlier = inputs.get(j);
                if (earlier != null && earlier.amount() > 0L && key.equals(earlier.what())) continue outer;
            }
            long required = input.amount();
            for (int j = i + 1; j < inputs.size(); j++) {
                GenericStack later = inputs.get(j);
                if (later != null && later.amount() > 0L && key.equals(later.what())) {
                    required = NEMath.saturatingAdd(required, later.amount());
                }
            }
            long available = Math.max(0L, inventory.list.get(key) - protectedAmounts.getOrDefault(key, 0L));
            if (available < required) return false;
        }
        return true;
    }

    boolean preservesStartupSeeds(DispatchCandidate candidate, Map<AEKey, java.math.BigInteger> inputs,
            appeng.crafting.inv.ListCraftingInventory inventory) {
        Map<AEKey, Long> protectedAmounts = protectedStartupSeed(candidate);
        if (protectedAmounts.isEmpty() || inputs.isEmpty()) return true;
        for (var entry : inputs.entrySet()) {
            var available = cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory.amount(inventory, entry.getKey())
                .subtract(java.math.BigInteger.valueOf(protectedAmounts.getOrDefault(entry.getKey(), 0L)));
            if (available.compareTo(entry.getValue()) < 0) return false;
        }
        return true;
    }

    private long totalStartupSeedRemaining(AEKey key) {
        long result = 0L;
        for (Object2LongMap<AEKey> phaseSeeds : startupSeedRemainingByPhase) {
            result = NEMath.saturatingAdd(result, phaseSeeds.getOrDefault(key, 0L));
        }
        return result;
    }

    private void consumeStartupSeed(DispatchCandidate candidate, KeyCounter[] inputs, long count) {
        if (inputs == null) return;
        Object2LongMap<AEKey> ownedSeeds = startupSeedRemainingByPhase.get(candidate.phaseIndex());
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
        ownedSeeds.object2LongEntrySet().removeIf(entry -> entry.getLongValue() <= 0L);
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
        Object2LongMap<AEKey> releasedSeeds = startupSeedRemainingByPhase.get(phaseIndex);
        if (!releasedSeeds.isEmpty()) {
            for (AEKey key : releasedSeeds.keySet()) incrementStartupSeedGeneration(key);
            releasedSeeds.clear();
        }
        for (var dependents = dependentsByPhase.get(phaseIndex).iterator(); dependents.hasNext();) {
            int dependent = dependents.nextInt();
            if (remainingDependencies[dependent] > 0) remainingDependencies[dependent]--;
        }
    }

    private void incrementStartupSeedGeneration(AEKey key) {
        if (key == null) return;
        protectedSeedCacheStale = true;
        long current = startupSeedGenerations.getLong(key);
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
            for (var entry : remainingDynamicFirings.get(phaseIndex).int2LongEntrySet()) {
                if (entry.getLongValue() > 0L) activeDynamicTasksByPhase[phaseIndex]++;
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

    private static List<IntList> createDependents(ECOExecutionPlan plan) {
        List<IntList> result = new ArrayList<>(plan.phases().size());
        for (int i = 0; i < plan.phases().size(); i++) result.add(new IntArrayList());
        return result;
    }

    private static int[][] createProgressAliases(
            @Nullable ExecutingCraftingJob.TaskProgress[] progressByTaskId, int taskCount) {
        int[][] result = new int[taskCount][];
        if (progressByTaskId == null) {
            for (int taskId = 0; taskId < taskCount; taskId++) result[taskId] = new int[] {taskId};
            return result;
        }

        var aliasesByProgress = new IdentityHashMap<ExecutingCraftingJob.TaskProgress, IntArrayList>();
        for (int taskId = 0; taskId < progressByTaskId.length; taskId++) {
            aliasesByProgress.computeIfAbsent(progressByTaskId[taskId], ignored -> new IntArrayList()).add(taskId);
        }
        for (var aliases : aliasesByProgress.values()) {
            int[] taskIds = aliases.toIntArray();
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
                        completedTaskCount(taskId));
                }
                long[] before = steps.clone();
                restoreOrderedProgress(phaseIndex, completedByTask);
                changed |= !Arrays.equals(before, steps);
                stepCursor[phaseIndex] = 0;
                advanceFinishedSteps(phaseIndex);
            } else if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
                Int2LongMap dynamic = remainingDynamicFirings.get(phaseIndex);
                dynamic.keySet().removeIf((int taskId) -> !phase.dynamicFirings().containsKey(taskId));
                for (var initial : phase.dynamicFirings().entrySet()) {
                    int taskId = initial.getKey();
                    long completed = Math.max(0L,
                        completedTaskCount(taskId));
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

    private long completedTaskCount(int taskId) {
        return progressByTaskId[taskId].completedBounded(plan.task(taskId).totalCount());
    }

    /** Skip completed laps arithmetically when reloading; never replay one operation per craft. */
    private void restoreOrderedProgress(int phaseIndex, long[] completedByTask) {
        var steps = plan.phases().get(phaseIndex).steps();
        long[] remaining = remainingSteps.get(phaseIndex);
        long[] laps = remainingLaps.get(phaseIndex);
        long[] ahead = aheadSteps.get(phaseIndex);
        Arrays.fill(ahead, 0L);
        int[] groupEnd = new int[steps.size()];
        for (int i = 0; i < groupEnd.length; i++) groupEnd[i] = i;
        for (int end = 0; end < steps.size(); end++) {
            var last = steps.get(end);
            if (last.repetitions() > 1L) groupEnd[end - last.repeatWidth() + 1] = end;
        }
        boolean prefix = true;
        for (int start = 0; start < steps.size();) {
            int end = groupEnd[start];
            long repetitions = steps.get(end).repetitions();
            Map<Integer, Long> perLap = new LinkedHashMap<>();
            for (int i = start; i <= end; i++) perLap.merge(steps.get(i).taskId(), steps.get(i).count(), Math::addExact);
            long complete = prefix ? repetitions : 0L;
            for (var entry : perLap.entrySet()) complete = Math.min(complete, completedByTask[entry.getKey()] / entry.getValue());
            for (var entry : perLap.entrySet()) completedByTask[entry.getKey()] -= complete * entry.getValue();
            for (int i = start; i <= end; i++) {
                laps[i] = 1L;
                remaining[i] = complete == repetitions ? 0L : steps.get(i).count();
            }
            laps[end] = Math.max(1L, repetitions - complete);
            if (complete < repetitions) {
                boolean batchable = batchGroupEnds.get(phaseIndex)[start] == end;
                boolean groupPending = false;
                for (int i = start; i <= end; i++) {
                    var step = steps.get(i);
                    long limit = batchable ? Math.multiplyExact(step.count(), laps[end]) : step.count();
                    long consumed = prefix ? Math.min(limit, completedByTask[step.taskId()]) : 0L;
                    long current = Math.min(step.count(), consumed);
                    remaining[i] -= current;
                    ahead[i] = consumed - current;
                    completedByTask[step.taskId()] -= consumed;
                    if (remaining[i] > 0L) {
                        groupPending = true;
                        if (!batchable) prefix = false;
                    }
                }
                if (groupPending) prefix = false;
            }
            start = end + 1;
        }
    }

    private void restoreLegacyStartupSeeds(Map<AEKey, Long> legacySeeds) {
        for (var entry : legacySeeds.entrySet()) {
            IntArrayList owners = new IntArrayList();
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
            for (var ownerIterator = owners.iterator(); ownerIterator.hasNext();) {
                int phaseIndex = ownerIterator.nextInt();
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
            for (var entry : startupSeedRemainingByPhase.get(phaseIndex).object2LongEntrySet()) {
                if (entry.getLongValue() <= 0L || entry.getLongValue() > planned.getOrDefault(entry.getKey(), 0L)) {
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
