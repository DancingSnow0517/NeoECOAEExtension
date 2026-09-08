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
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final Map<Integer, IPatternDetails> patternsById;
    private final int[] stepCursor;
    private final int[] dynamicCursor;
    private final List<long[]> remainingSteps;
    private final List<Map<Integer, Long>> remainingDynamicFirings;
    private final BitSet completedPhases;
    private final Map<AEKey, Long> startupSeedRemaining = new LinkedHashMap<>();

    public ECOExecutionRuntime(ECOExecutionPlan plan, Map<Integer, IPatternDetails> patternsById) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.patternsById = Map.copyOf(patternsById);
        this.stepCursor = new int[plan.phases().size()];
        this.dynamicCursor = new int[plan.phases().size()];
        this.remainingSteps = new ArrayList<>(plan.phases().size());
        this.remainingDynamicFirings = new ArrayList<>(plan.phases().size());
        this.completedPhases = new BitSet(plan.phases().size());

        for (var task : plan.tasks()) {
            IPatternDetails actual = this.patternsById.get(task.id());
            if (actual == null || !ECOPhaseScheduler.samePattern(task.pattern(), actual)) {
                throw new IllegalArgumentException("Execution task is not bound to the submitted pattern vector: "
                    + task.id());
            }
        }
        for (var phase : plan.phases()) {
            long[] steps = new long[phase.steps().size()];
            for (int i = 0; i < steps.length; i++) steps[i] = phase.steps().get(i).count();
            remainingSteps.add(steps);
            remainingDynamicFirings.add(new LinkedHashMap<>(phase.dynamicFirings()));
            phase.initialSeed().forEach((key, amount) -> startupSeedRemaining.merge(key, amount, NEMath::saturatingAdd));
        }
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
    public List<DispatchCandidate> candidates(Map<IPatternDetails, Long> remainingTasks) {
        refreshCompleted(remainingTasks);
        List<DispatchCandidate> result = new ArrayList<>();
        for (int phaseIndex = 0; phaseIndex < plan.phases().size(); phaseIndex++) {
            if (completedPhases.get(phaseIndex) || !dependenciesComplete(phaseIndex)) continue;
            var phase = plan.phases().get(phaseIndex);
            if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps().isEmpty()) {
                advanceFinishedSteps(phaseIndex);
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
                List<Integer> active = phase.taskIds().stream()
                    .filter(taskId -> dynamic.getOrDefault(taskId, 0L) > 0L)
                    .filter(taskId -> taskRemaining(taskId, remainingTasks) > 0L)
                    .toList();
                if (active.isEmpty() && dynamic.values().stream().noneMatch(value -> value > 0L)) {
                    addAllPhaseTasks(result, phaseIndex, phase.taskIds(), remainingTasks, false);
                    continue;
                }
                if (active.isEmpty()) continue;
                int start = Math.floorMod(dynamicCursor[phaseIndex], active.size());
                boolean sharedInput = active.size() > 1;
                for (int offset = 0; offset < active.size(); offset++) {
                    int taskId = active.get((start + offset) % active.size());
                    long allowed = Math.min(dynamic.getOrDefault(taskId, 0L), taskRemaining(taskId, remainingTasks));
                    if (sharedInput && sharesInputWithAnother(taskId, active)) allowed = Math.min(allowed, 1L);
                    if (allowed > 0L) result.add(candidate(phaseIndex, taskId, allowed, false));
                }
                continue;
            }

            addAllPhaseTasks(result, phaseIndex, phase.taskIds(), remainingTasks, false);
        }
        return List.copyOf(result);
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
            int taskPosition = phase.taskIds().indexOf(candidate.taskId());
            if (taskPosition >= 0 && !phase.taskIds().isEmpty()) {
                dynamicCursor[phaseIndex] = (taskPosition + 1) % phase.taskIds().size();
            }
        }
        consumeStartupSeed(inputs, count);
    }

    public boolean isComplete(Map<IPatternDetails, Long> remainingTasks) {
        refreshCompleted(remainingTasks);
        return completedPhases.cardinality() == plan.phases().size();
    }

    /** Amount of planned input that must stay in the CPU for future executions of {@code key}. */
    public long reservedInputAmount(AEKey key, Map<IPatternDetails, Long> remainingTasks) {
        if (key == null) return 0L;
        long result = startupSeedRemaining.getOrDefault(key, 0L);
        for (var task : plan.tasks()) {
            long remaining = taskRemaining(task.id(), remainingTasks);
            if (remaining <= 0L) continue;
            IPatternDetails pattern = patternsById.get(task.id());
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
        ECOExecutionRuntime runtime = new ECOExecutionRuntime(plan, patternsById);
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
        return runtime;
    }

    private void refreshCompleted(Map<IPatternDetails, Long> remainingTasks) {
        for (int phaseIndex = 0; phaseIndex < plan.phases().size(); phaseIndex++) {
            if (!completedPhases.get(phaseIndex) && phaseComplete(phaseIndex, remainingTasks)) {
                completedPhases.set(phaseIndex);
            }
        }
    }

    private boolean phaseComplete(int phaseIndex, Map<IPatternDetails, Long> remainingTasks) {
        var phase = plan.phases().get(phaseIndex);
        for (int taskId : phase.taskIds()) if (taskRemaining(taskId, remainingTasks) > 0L) return false;
        if (phase.type() == ECOExecutionSchedule.Type.CYCLE && !phase.steps().isEmpty()) {
            return stepCursor[phaseIndex] >= phase.steps().size();
        }
        if (phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE) {
            return remainingDynamicFirings.get(phaseIndex).values().stream().noneMatch(value -> value > 0L);
        }
        return true;
    }

    private boolean dependenciesComplete(int phaseIndex) {
        for (int dependency : plan.phases().get(phaseIndex).dependencies()) {
            if (!completedPhases.get(dependency)) return false;
        }
        return true;
    }

    private void advanceFinishedSteps(int phaseIndex) {
        var steps = plan.phases().get(phaseIndex).steps();
        long[] remaining = remainingSteps.get(phaseIndex);
        while (stepCursor[phaseIndex] < steps.size() && remaining[stepCursor[phaseIndex]] == 0L) {
            stepCursor[phaseIndex]++;
        }
    }

    private void addAllPhaseTasks(List<DispatchCandidate> result, int phaseIndex, List<Integer> taskIds,
            Map<IPatternDetails, Long> remainingTasks, boolean blocksOrderedPhase) {
        for (int taskId : taskIds) {
            long remaining = taskRemaining(taskId, remainingTasks);
            if (remaining > 0L) result.add(candidate(phaseIndex, taskId, remaining, blocksOrderedPhase));
        }
    }

    private DispatchCandidate candidate(int phaseIndex, int taskId, long allowed, boolean blocksOrderedPhase) {
        IPatternDetails pattern = patternsById.get(taskId);
        if (pattern == null) throw new IllegalStateException("Execution task is not bound: " + taskId);
        return new DispatchCandidate(taskId, phaseIndex, pattern, allowed, blocksOrderedPhase);
    }

    private long taskRemaining(int taskId, Map<IPatternDetails, Long> remainingTasks) {
        IPatternDetails pattern = patternsById.get(taskId);
        if (pattern == null) return 0L;
        long result = 0L;
        for (var entry : remainingTasks.entrySet()) {
            if (ECOPhaseScheduler.samePattern(pattern, entry.getKey())) {
                result = NEMath.saturatingAdd(result, Math.max(0L, entry.getValue() == null ? 0L : entry.getValue()));
            }
        }
        return result;
    }

    private boolean sharesInputWithAnother(int taskId, List<Integer> active) {
        IPatternDetails pattern = patternsById.get(taskId);
        if (pattern == null) return false;
        for (int otherId : active) {
            if (otherId == taskId) continue;
            IPatternDetails other = patternsById.get(otherId);
            if (other == null) continue;
            if (inputKeys(pattern).stream().anyMatch(inputKeys(other)::contains)) return true;
        }
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
}
