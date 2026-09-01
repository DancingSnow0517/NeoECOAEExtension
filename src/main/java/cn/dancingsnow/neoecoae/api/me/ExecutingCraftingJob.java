/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package cn.dancingsnow.neoecoae.api.me;

import java.util.HashMap;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionContract;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.RuntimeExecutionState;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import java.util.ArrayList;
import java.util.UUID;

public class ExecutingCraftingJob {
    private static final Logger LOGGER = LoggerFactory.getLogger("neoecoae");
    private static final String NBT_LINK = "link";
    private static final String NBT_PLAYER_ID = "playerId";
    private static final String NBT_FINAL_OUTPUT = "finalOutput";
    private static final String NBT_WAITING_FOR = "waitingFor";
    private static final String NBT_TIME_TRACKER = "timeTracker";
    private static final String NBT_REMAINING_AMOUNT = "remainingAmount";
    private static final String NBT_TASKS = "tasks";
    private static final String NBT_CRAFTING_PROGRESS = "#craftingProgress";
    private static final String NBT_SUSPENDED = "suspended";
    private static final String NBT_BUFFERED_FINAL_OUTPUT = "bufferedFinalOutput";
    private static final String NBT_CYCLE_WITNESS_INDEX = "cycleWitnessIndex";
    private static final String NBT_CYCLE_EXPECTED = "cycleExpected";
    private static final String NBT_REQUIRES_COMPONENT_SCHEDULING = "requiresComponentScheduling";
    /** Legacy key written before cycle safety and general component scheduling were separated. */
    private static final String NBT_REQUIRES_ORDERED_CYCLE = "requiresOrderedCycleExecution";
    private static final String NBT_CURRENT_COMPONENT = "currentComponentIndex";
    private static final String NBT_EXECUTION_STEP = "executionStepIndex";
    private static final String NBT_EXECUTION_SCHEDULE = "executionSchedule";
    private static final String NBT_EXECUTION_SCHEDULE_VERSION = "executionScheduleVersion";
    private static final int EXECUTION_SCHEDULE_VERSION = 3;
    private static final String NBT_COMPONENT_ID = "componentId";
    private static final String NBT_COMPONENT_TYPE = "type";
    private static final String NBT_COMPONENT_PATTERNS = "patterns";
    private static final String NBT_COMPONENT_WITNESS = "cycleWitness";
    private static final String NBT_EXECUTION_MODE = "executionMode";
    private static final String NBT_EXECUTION_CONTRACT_VERSION = "executionContractVersion";
    private static final String NBT_PLAN_SIGNATURE_HASH = "planSignatureHash";
    private static final int EXECUTION_CONTRACT_VERSION = 5;
    private static final String NBT_EXECUTION_PLAN = "executionPlanV5";
    private static final String NBT_PLAN_TASKS = "planTasks";
    private static final String NBT_PLAN_PHASES = "planPhases";
    private static final String NBT_TASK_ID = "taskId";
    private static final String NBT_TASK_TOTAL = "total";
    private static final String NBT_TASK_REMAINING = "remaining";
    private static final String NBT_TASK_PHASE = "phase";
    private static final String NBT_TASK_KIND = "kind";
    private static final String NBT_PHASE_TASK_IDS = "taskIds";
    private static final String NBT_PHASE_STEPS = "steps";
    private static final String NBT_PHASE_DEPENDENCIES = "dependencies";
    private static final String NBT_STEP_COUNT = "count";
    private static final String NBT_STEP_REMAINING = "stepRemaining";
    private static final String NBT_SIGNATURE_USED = "signatureUsed";
    private static final String NBT_SIGNATURE_EMITTED = "signatureEmitted";
    private static final String NBT_SIGNATURE_MISSING = "signatureMissing";

    final CraftingLink link;
    final ListCraftingInventory waitingFor;
    final Map<IPatternDetails, TaskProgress> tasks = new HashMap<>();
    int cycleWitnessIndex;
    int currentComponentIndex;
    int executionStepIndex;
    ExecutionMode executionMode = ExecutionMode.NATIVE;
    boolean cycleMetadataErrorLogged;
    @Nullable PermanentExecutionError permanentExecutionError;
    ECOExecutionSchedule executionSchedule;
    @Nullable ECOExecutionContract executionContract;
    @Nullable RuntimeExecutionState runtimeExecutionState;
    @Nullable Map<Integer, TaskProgress> runtimeProgressProjection;
    final ElapsedTimeTracker timeTracker;
    final ECOFinalOutputBuffer bufferedFinalOutput;
    GenericStack finalOutput;
    long remainingAmount;
    @Nullable
    Integer playerId;
    boolean suspended;

    /** Stable dispatch reference. Runtime plans use task ids; native jobs retain their compatibility progress. */
    record DispatchTask(int taskId, IPatternDetails pattern, TaskProgress progress) {}

    @Nullable ECOExecutionSchedule.ComponentExecutionPhase activePhase() {
        if (runtimeExecutionState != null) {
            var phase = runtimeExecutionState.activePhase();
            return phase == null ? null : executionSchedule.phases().get(phase.index());
        }
        if (!phased() || executionSchedule == null
                || currentComponentIndex >= executionSchedule.phases().size()) return null;
        return executionSchedule.phases().get(currentComponentIndex);
    }

    boolean phaseComplete(ECOExecutionSchedule.ComponentExecutionPhase phase) {
        return ECOPhaseScheduler.isComplete(phase, cycleWitnessIndex,
            this::remainingTasksFor);
    }

    boolean hasPermanentExecutionError() {
        return permanentExecutionError != null;
    }

    boolean phased() {
        return runtimeExecutionState != null || ECOPhaseScheduler.hasExecutionPhases(executionSchedule);
    }

    boolean orderedCycle() { return executionMode == ExecutionMode.ORDERED_CYCLE; }

    /** Shared runtime state. It is never reconstructed from the mutable AE2 compatibility map. */
    @Nullable RuntimeExecutionState runtimeExecutionState() {
        return runtimeExecutionState;
    }

    long remainingTasksFor(IPatternDetails pattern) {
        if (runtimeExecutionState != null) {
            for (var task : runtimeExecutionState.plan().tasks()) {
                if (ECOPhaseScheduler.samePattern(pattern, task.pattern())) {
                    return runtimeExecutionState.remaining(task.id());
                }
            }
            return 0L;
        }
        for (var task : tasks.entrySet()) {
            if (ECOPhaseScheduler.samePattern(pattern, task.getKey())) return task.getValue().value;
        }
        return 0L;
    }

    @Nullable Map.Entry<IPatternDetails, TaskProgress> taskFor(IPatternDetails pattern) {
        for (var task : tasks.entrySet()) {
            if (ECOPhaseScheduler.samePattern(pattern, task.getKey())) return task;
        }
        return null;
    }

    void advanceCompletedPhases() {
        if (runtimeExecutionState != null) {
            syncRuntimeProjection();
            return;
        }
        while (activePhase() != null && phaseComplete(activePhase())) {
            currentComponentIndex++;
            cycleWitnessIndex = 0;
            executionStepIndex = 0;
        }
    }

    void advanceRuntimeWitness() {
        if (runtimeExecutionState != null) {
            syncRuntimeProjection();
            return;
        }
        cycleWitnessIndex = ECOPhaseScheduler.witnessAfterDispatch(cycleWitnessIndex, true);
        executionStepIndex = cycleWitnessIndex;
    }

    long dispatchLimit(DispatchTask task) {
        return runtimeExecutionState == null ? task.progress().value : dispatchLimit(task.taskId());
    }

    long dispatchLimit(int taskId) {
        if (runtimeExecutionState == null) return 0L;
        return runtimeExecutionState.dispatchLimit(taskId);
    }

    long finalOutputFeedbackReserve(AEKey key) {
        if (key == null) return 0L;
        if (runtimeExecutionState == null) {
            return ECOPhaseScheduler.compactCycleFeedbackReserve(activePhase(), this::remainingTasksFor, key);
        }
        return runtimeExecutionState.pendingCycleFeedbackReserve(key);
    }

    List<DispatchTask> eligibleDispatchTasks() {
        if (runtimeExecutionState == null) return tasks.entrySet().stream()
            .filter(entry -> entry.getValue().value > 0)
            .map(entry -> new DispatchTask(-1, entry.getKey(), entry.getValue())).toList();
        ensureRuntimeProgressProjection();
        List<DispatchTask> result = new ArrayList<>();
        for (int taskId : runtimeExecutionState.eligibleTaskIds()) result.add(runtimeDispatchTask(taskId));
        return result;
    }

    private void ensureRuntimeProgressProjection() {
        if (runtimeProgressProjection == null) {
            Map<Integer, TaskProgress> projection = new HashMap<>();
            for (var spec : runtimeExecutionState.plan().tasks()) {
                var compatibility = tasks.get(spec.pattern());
                if (compatibility == null) {
                    var matched = taskFor(spec.pattern());
                    compatibility = matched == null ? new TaskProgress() : matched.getValue();
                }
                if (compatibility.value == 0) {
                    compatibility.value = runtimeExecutionState.remaining(spec.id());
                }
                projection.put(spec.id(), compatibility);
            }
            runtimeProgressProjection = projection;
        }
    }

    private DispatchTask runtimeDispatchTask(int taskId) {
        var spec = runtimeExecutionState.plan().task(taskId);
        return new DispatchTask(taskId, spec.pattern(), runtimeProgressProjection.get(taskId));
    }

    void applyAccepted(DispatchTask task, long count) {
        if (runtimeExecutionState == null) {
            if (count <= 0 || count > task.progress().value) {
                throw new IllegalArgumentException("Accepted dispatch does not match a remaining task");
            }
            task.progress().value -= count;
            return;
        }
        applyAccepted(task.taskId(), count);
        task.progress().value = runtimeExecutionState.remaining(task.taskId());
        syncRuntimeProjection();
    }

    void applyAccepted(int taskId, long count) {
        if (runtimeExecutionState == null) {
            throw new IllegalStateException("Task ids are unavailable for native execution");
        }
        runtimeExecutionState.applyAccepted(taskId, count);
        syncRuntimeProjection();
    }

    List<DispatchTask> applyDispatchResultAndGetNewlyReady(DispatchTask task, DispatchResult result) {
        if (!(result instanceof DispatchResult.Accepted accepted)) {
            applyDispatchResult(task, result);
            return List.of();
        }
        if (runtimeExecutionState == null) {
            applyAccepted(task, accepted.count());
            return List.of();
        }
        ensureRuntimeProgressProjection();
        List<Integer> newlyReadyIds = runtimeExecutionState.applyAccepted(task.taskId(), accepted.count());
        task.progress().value = runtimeExecutionState.remaining(task.taskId());
        syncRuntimeProjection();
        if (newlyReadyIds.isEmpty()) return List.of();
        List<DispatchTask> resultTasks = new ArrayList<>(newlyReadyIds.size());
        for (int taskId : newlyReadyIds) resultTasks.add(runtimeDispatchTask(taskId));
        return resultTasks;
    }

    long applyDispatchResult(DispatchTask task, DispatchResult result) {
        if (result instanceof DispatchResult.Accepted accepted) {
            applyAccepted(task, accepted.count());
            return accepted.count();
        }
        if (result instanceof DispatchResult.Fatal fatal) {
            permanentExecutionError = PermanentExecutionError.EXECUTION_PLAN_INVALID;
            LOGGER.error("[ECO-EXEC] fatal dispatch result: {}", fatal.reason());
        }
        return 0L;
    }

    long applyDispatchResult(int taskId, DispatchResult result) {
        if (result instanceof DispatchResult.Accepted accepted) {
            applyAccepted(taskId, accepted.count());
            return accepted.count();
        }
        if (result instanceof DispatchResult.Fatal fatal) {
            permanentExecutionError = PermanentExecutionError.EXECUTION_PLAN_INVALID;
            LOGGER.error("[ECO-EXEC] fatal dispatch result: {}", fatal.reason());
        }
        return 0L;
    }

    private void syncRuntimeProjection() {
        if (runtimeExecutionState == null) return;
        currentComponentIndex = runtimeExecutionState.phaseIndex();
        executionStepIndex = runtimeExecutionState.stepIndex();
        cycleWitnessIndex = executionStepIndex;
    }

    @FunctionalInterface
    interface CraftingDifferenceListener {
        void onCraftingDifference(AEKey what);
    }

    ExecutingCraftingJob(ICraftingPlan plan, CraftingDifferenceListener postCraftingDifference, CraftingLink link,
            @Nullable Integer playerId) {
        this.finalOutput = plan.finalOutput();
        this.remainingAmount = this.finalOutput.amount();
        this.waitingFor = new ListCraftingInventory(postCraftingDifference::onCraftingDifference);

        // Fill waiting for and tasks
        this.timeTracker = new ElapsedTimeTracker();
        this.bufferedFinalOutput = new ECOFinalOutputBuffer();
        for (var entry : plan.emittedItems()) {
            waitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            timeTracker.addMaxItems(entry.getLongValue(), entry.getKey().getType());
        }
        for (var entry : plan.patternTimes().entrySet()) {
            tasks.computeIfAbsent(entry.getKey(), p -> new TaskProgress()).value += entry.getValue();
            for (var output : entry.getKey().getOutputs()) {
                var amount = output.amount() * entry.getValue() * output.what().getAmountPerUnit();
                timeTracker.addMaxItems(amount, output.what().getType());
            }
        }
        ECOPlanningResult planningResult = plan instanceof ECOCraftingPlanDiagnostics d
            ? d.neoecoae$getPlanningResult()
            : null;
        ECOPlanningResultRegistry.SubmissionMetadata submissionMetadata =
            ECOPlanningResultRegistry.activeSubmissionMetadata(plan);
        this.executionContract = ECOPlanningResultRegistry.resolveContract(plan, planningResult);
        if (executionContract != null) executionSchedule = executionContract.schedule();
        boolean cycleExpected = submissionMetadata != null
            ? submissionMetadata.cycleExpected()
            : ECOPlanningResultRegistry.cycleSafetyRequired(plan, planningResult);
        ECOPlanningResultRegistry.RecoveredExecutionMetadata recoveredMetadata = null;
        if (submissionMetadata != null) {
            executionSchedule = submissionMetadata.executionSchedule();
        }
        if (executionSchedule == null && planningResult != null) {
            try {
                executionSchedule = planningResult.executionPlan().schedule();
            } catch (RuntimeException scheduleFailure) {
                LOGGER.error("[ECO-EXEC] failed to build execution schedule from attached planning result; "
                    + "preserving cycle expectation for fail-safe dispatch", scheduleFailure);
            }
        }
        if (executionSchedule == null || executionSchedule.phases().isEmpty()
                || (cycleExpected && !ECOPhaseScheduler.requiresComponentScheduling(executionSchedule))) {
            recoveredMetadata = ECOPlanningResultRegistry.recoverExecutionMetadata(plan);
        }
        if (recoveredMetadata != null) {
            cycleExpected = cycleExpected || recoveredMetadata.cycleExpected();
            if (recoveredMetadata.executionPlan() != null) {
                executionSchedule = recoveredMetadata.executionPlan().schedule();
                executionContract = new ECOExecutionContract(recoveredMetadata.planningId(),
                    recoveredMetadata.executionPlan().signature(), recoveredMetadata.executionPlan().mode(),
                    recoveredMetadata.executionPlan(), null);
            }
        }
        if (recoveredMetadata != null
                && recoveredMetadata.state() == ECOPlanningResultRegistry.RecoveryState.VALID_SCHEDULE) {
            LOGGER.info(
                "[ECO-EXEC] recovered execution schedule finalOutput={} phases={} matchMode={} planningId={}",
                plan.finalOutput(), executionSchedule.phases().size(), recoveredMetadata.matchMode(),
                recoveredMetadata.planningId());
        } else if (recoveredMetadata != null && recoveredMetadata.cycleExpected()) {
            LOGGER.error(
                "[ECO-EXEC] recovered fail-closed cycle expectation finalOutput={} state={} reason={} "
                    + "matchMode={} planningId={}",
                plan.finalOutput(), recoveredMetadata.state(), recoveredMetadata.rejectionReason(),
                recoveredMetadata.matchMode(), recoveredMetadata.planningId());
        } else if (executionSchedule == null || executionSchedule.phases().isEmpty()) {
            LOGGER.warn(
                "[ECO-EXEC] no registered execution schedule matched submitted plan finalOutput={} "
                    + "patternKinds={} emittedKinds={} registeredCycleSchedules={} registeredMetadata={} mismatch={}",
                plan.finalOutput(), plan.patternTimes().size(), plan.emittedItems().size(),
                ECOPlanningResultRegistry.registeredScheduleCount(),
                ECOPlanningResultRegistry.registeredMetadataCount(),
                ECOPlanningResultRegistry.mismatchDiagnostic(plan));
        }
        LOGGER.info("[ECO-SUBMISSION] selectedPlanner={} submittedSignature={} submittedExecutions={} "
                + "metadataPlanningId={} metadataMatch={} planReplaced=false cpuExecutions={}",
            submissionMetadata == null ? planningResult == null ? "unknown" : "ECO"
                : submissionMetadata.selectedPlanner(), PlanIdentity.describe(PlanIdentity.of(plan)),
            PlanIdentity.executionCount(plan.patternTimes()),
            submissionMetadata == null ? recoveredMetadata == null ? null : recoveredMetadata.planningId()
                : submissionMetadata.planningId(),
            submissionMetadata != null || recoveredMetadata != null,
            PlanIdentity.executionCount(plan.patternTimes()));
        if (executionContract == null) {
            var signature = PlanIdentity.of(plan);
            if (signature != null) {
                boolean missingCycle = cycleExpected && !ECOPhaseScheduler.requiresComponentScheduling(executionSchedule);
                ExecutionMode mode = missingCycle ? ExecutionMode.BLOCKED : cycleExpected ? ExecutionMode.ORDERED_CYCLE
                    : ECOPhaseScheduler.hasExecutionPhases(executionSchedule) ? ExecutionMode.PHASED_DAG : ExecutionMode.NATIVE;
                executionContract = missingCycle
                    ? new ECOExecutionContract(UUID.randomUUID(), signature, ExecutionMode.BLOCKED, null,
                        "CYCLE_METADATA_MISSING")
                    : ECOExecutionContract.nativeContract(UUID.randomUUID(), signature);
            }
        }
        cycleExpected = cycleExpected || ECOPhaseScheduler.requiresComponentScheduling(executionSchedule);
        boolean cycleWitnessMissing = cycleExpected && !ECOPhaseScheduler.requiresComponentScheduling(executionSchedule);
        permanentExecutionError = cycleWitnessMissing
            ? PermanentExecutionError.CYCLE_METADATA_MISSING
            : null;
        if (cycleWitnessMissing) {
            LOGGER.error(
                "[ECO-EXEC] solved cycle metadata is missing; job will remain fail-safe finalOutput={} "
                    + "submissionBound={} planningResultPresent={} schedulePresent={} phaseCount={}",
                plan.finalOutput(), submissionMetadata != null, planningResult != null, executionSchedule != null,
                executionSchedule == null ? 0 : executionSchedule.phases().size());
        }
        if (executionContract != null && executionContract.mode() == ExecutionMode.BLOCKED) {
            permanentExecutionError = executionContract.error() != null
                    && executionContract.error().contains("CYCLE")
                ? PermanentExecutionError.CYCLE_METADATA_MISSING
                : PermanentExecutionError.EXECUTION_PLAN_INVALID;
        }
        executionMode = executionContract == null
            ? cycleWitnessMissing ? ExecutionMode.BLOCKED
                : cycleExpected ? ExecutionMode.ORDERED_CYCLE
                : phased() ? ExecutionMode.PHASED_DAG : ExecutionMode.NATIVE
            : executionContract.mode();
        if (executionContract != null && executionContract.executionPlan() != null) {
            runtimeExecutionState = new RuntimeExecutionState(executionContract.executionPlan());
            executionSchedule = executionContract.executionPlan().schedule();
            syncRuntimeProjection();
        }
        this.link = link;
        this.playerId = playerId;
        this.suspended = false;
    }

    ExecutingCraftingJob(CompoundTag data, HolderLookup.Provider registries,
            CraftingDifferenceListener postCraftingDifference, ECOCraftingCPULogic logic) {
        this.link = new CraftingLink(data.getCompound(NBT_LINK), logic.cpu);
        this.finalOutput = GenericStack.readTag(registries, data.getCompound(NBT_FINAL_OUTPUT));
        this.remainingAmount = data.getLong(NBT_REMAINING_AMOUNT);
        this.waitingFor = new ListCraftingInventory(postCraftingDifference::onCraftingDifference);
        this.waitingFor.readFromNBT(data.getList(NBT_WAITING_FOR, Tag.TAG_COMPOUND), registries);
        this.timeTracker = new ElapsedTimeTracker(data.getCompound(NBT_TIME_TRACKER));
        this.bufferedFinalOutput = new ECOFinalOutputBuffer(Math.max(0L, data.getLong(NBT_BUFFERED_FINAL_OUTPUT)));
        if (data.contains(NBT_PLAYER_ID, Tag.TAG_INT)) {
            this.playerId = data.getInt(NBT_PLAYER_ID);
        } else {
            this.playerId = null;
        }

        Level level = logic.cpu.getLevel();
        boolean recoveryFailed = level == null;

        ListTag tasksTag = data.getList(NBT_TASKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < tasksTag.size(); ++i) {
            final CompoundTag item = tasksTag.getCompound(i);
            var pattern = AEItemKey.fromTag(registries, item);
            var details = level == null || pattern == null ? null : PatternDetailsHelper.decodePattern(pattern, level);
            if (details == null) {
                recoveryFailed = true;
                continue;
            }
            final TaskProgress tp = new TaskProgress();
            tp.value = item.getLong(NBT_CRAFTING_PROGRESS);
            this.tasks.put(details, tp);
        }

        this.suspended = data.getBoolean(NBT_SUSPENDED);
        this.cycleWitnessIndex = Math.max(0, data.getInt(NBT_CYCLE_WITNESS_INDEX));
        this.currentComponentIndex = Math.max(0, data.getInt(NBT_CURRENT_COMPONENT));
        this.executionStepIndex = Math.max(0, data.getInt(NBT_EXECUTION_STEP));
        boolean legacyOrderedCycle = data.getBoolean(NBT_REQUIRES_ORDERED_CYCLE);
        boolean cycleExpected = data.contains(NBT_CYCLE_EXPECTED, Tag.TAG_BYTE)
            ? data.getBoolean(NBT_CYCLE_EXPECTED)
            : legacyOrderedCycle;
        this.executionSchedule = level == null ? null : readExecutionSchedule(data, registries, level);
        int persistedContractVersion = data.contains(NBT_EXECUTION_CONTRACT_VERSION, Tag.TAG_INT)
            ? data.getInt(NBT_EXECUTION_CONTRACT_VERSION) : 0;
        ExecutionMode persistedMode = null;
        if (data.contains(NBT_EXECUTION_MODE, Tag.TAG_STRING)) {
            try { persistedMode = ExecutionMode.valueOf(data.getString(NBT_EXECUTION_MODE)); }
            catch (IllegalArgumentException ignored) { persistedMode = ExecutionMode.BLOCKED; }
        }
        if (persistedMode != null) this.executionMode = persistedMode;
        if (persistedContractVersion != 0 && persistedContractVersion != EXECUTION_CONTRACT_VERSION
                && persistedMode != null && persistedMode != ExecutionMode.NATIVE) {
            recoveryFailed = true;
        }
        boolean cycleWitnessMissing = cycleExpected && !ECOPhaseScheduler.requiresComponentScheduling(executionSchedule);
        this.permanentExecutionError = cycleWitnessMissing
            ? PermanentExecutionError.CYCLE_METADATA_MISSING
            : null;
        if (!recoveryFailed && level != null && persistedContractVersion == EXECUTION_CONTRACT_VERSION
                && data.contains(NBT_EXECUTION_PLAN, Tag.TAG_COMPOUND)
                && data.contains(NBT_PLAN_SIGNATURE_HASH, Tag.TAG_INT)) {
            try {
                RestoredExecution restored = readExecutionPlan(data.getCompound(NBT_EXECUTION_PLAN), registries, level,
                    finalOutput, data.getInt(NBT_PLAN_SIGNATURE_HASH));
                this.executionContract = new ECOExecutionContract(UUID.randomUUID(), restored.plan().signature(),
                    restored.plan().mode(), restored.plan(), null);
                this.executionSchedule = restored.plan().schedule();
                this.runtimeExecutionState = new RuntimeExecutionState(restored.plan());
                this.runtimeExecutionState.restore(restored.remaining(), restored.stepIndexes(),
                    restored.stepRemaining());
                this.executionMode = restored.plan().mode();
                this.permanentExecutionError = null;
                this.tasks.clear();
                for (var task : restored.plan().tasks()) {
                    this.tasks.computeIfAbsent(task.pattern(), ignored -> new TaskProgress()).value =
                        this.runtimeExecutionState.remaining(task.id());
                }
                syncRuntimeProjection();
            } catch (RuntimeException malformedPlan) {
                LOGGER.error("[ECO-RECOVERY] persisted execution plan failed validation", malformedPlan);
                recoveryFailed = true;
            }
        } else if (persistedMode == ExecutionMode.PHASED_DAG || persistedMode == ExecutionMode.ORDERED_CYCLE) {
            // Legacy schedule-only saves cannot prove compressed step counts or task ownership. Keep the job
            // visible and cancellable, but never resume it with guessed execution metadata.
            recoveryFailed = true;
        }
        if (recoveryFailed) {
            this.permanentExecutionError = PermanentExecutionError.RECOVERY_ERROR;
            this.executionMode = ExecutionMode.BLOCKED;
        }
        if (persistedMode == ExecutionMode.PHASED_DAG && (executionSchedule == null || executionSchedule.phases().isEmpty())) {
            this.permanentExecutionError = PermanentExecutionError.RECOVERY_ERROR;
            this.executionMode = ExecutionMode.BLOCKED;
        }
        IGrid grid = logic.cpu.getGrid();
        if (grid != null) {
            ((CraftingService) grid.getCraftingService()).addLink(link);
        }
    }

    CompoundTag writeToNBT(HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();

        CompoundTag linkData = new CompoundTag();
        link.writeToNBT(linkData);
        data.put(NBT_LINK, linkData);

        data.put(NBT_FINAL_OUTPUT, GenericStack.writeTag(registries, finalOutput));

        data.put(NBT_WAITING_FOR, waitingFor.writeToNBT(registries));
        data.put(NBT_TIME_TRACKER, timeTracker.writeToNBT());
        data.putLong(NBT_BUFFERED_FINAL_OUTPUT, bufferedFinalOutput.amount());

        final ListTag list = new ListTag();
        for (var e : this.tasks.entrySet()) {
            var item = e.getKey().getDefinition().toTag(registries);
            item.putLong(NBT_CRAFTING_PROGRESS, e.getValue().value);
            list.add(item);
        }
        data.put(NBT_TASKS, list);

        data.putLong(NBT_REMAINING_AMOUNT, remainingAmount);
        if (this.playerId != null) {
            data.putInt(NBT_PLAYER_ID, this.playerId);
        }

        data.putBoolean(NBT_SUSPENDED, suspended);
        data.putInt(NBT_CYCLE_WITNESS_INDEX, cycleWitnessIndex);
        data.putInt(NBT_CURRENT_COMPONENT, currentComponentIndex);
        data.putInt(NBT_EXECUTION_STEP, executionStepIndex);
        data.putBoolean(NBT_CYCLE_EXPECTED, orderedCycle());
        data.putBoolean(NBT_REQUIRES_COMPONENT_SCHEDULING, phased());
        // Retain the legacy safety bit so downgrading does not turn a known cycle into unordered execution.
        data.putBoolean(NBT_REQUIRES_ORDERED_CYCLE, orderedCycle());
        data.putString(NBT_EXECUTION_MODE, executionContract == null
            ? (permanentExecutionError != null ? ExecutionMode.BLOCKED.name()
                : phased() ? (orderedCycle() ? ExecutionMode.ORDERED_CYCLE.name() : ExecutionMode.PHASED_DAG.name())
                : ExecutionMode.NATIVE.name())
            : executionContract.mode().name());
        data.putInt(NBT_EXECUTION_CONTRACT_VERSION, EXECUTION_CONTRACT_VERSION);
        if (runtimeExecutionState != null) {
            data.putInt(NBT_PLAN_SIGNATURE_HASH, runtimeExecutionState.plan().signature().hashCode());
            data.put(NBT_EXECUTION_PLAN, writeExecutionPlan(registries));
        } else if (executionContract != null) {
            data.putInt(NBT_PLAN_SIGNATURE_HASH, executionContract.planSignature().hashCode());
        }
        ListTag scheduleTag = writeExecutionSchedule(registries);
        if (scheduleTag != null) {
            data.putInt(NBT_EXECUTION_SCHEDULE_VERSION, EXECUTION_SCHEDULE_VERSION);
            data.put(NBT_EXECUTION_SCHEDULE, scheduleTag);
        }
        return data;
    }

    private CompoundTag writeExecutionPlan(HolderLookup.Provider registries) {
        RuntimeExecutionState state = java.util.Objects.requireNonNull(runtimeExecutionState);
        ECOExecutionPlan plan = state.plan();
        CompoundTag root = new CompoundTag();
        root.putString(NBT_EXECUTION_MODE, plan.mode().name());
        root.put(NBT_SIGNATURE_USED, writeKeyAmounts(plan.signature().usedItems(), registries));
        root.put(NBT_SIGNATURE_EMITTED, writeKeyAmounts(plan.signature().emittedItems(), registries));
        root.put(NBT_SIGNATURE_MISSING, writeKeyAmounts(plan.signature().missingItems(), registries));

        ListTag taskTags = new ListTag();
        for (var task : plan.tasks()) {
            CompoundTag tag = task.pattern().getDefinition().toTag(registries);
            tag.putInt(NBT_TASK_ID, task.id());
            tag.putLong(NBT_TASK_TOTAL, task.totalCount());
            tag.putLong(NBT_TASK_REMAINING, state.remaining(task.id()));
            tag.putInt(NBT_TASK_PHASE, task.phaseIndex());
            tag.putString(NBT_TASK_KIND, task.kind().name());
            taskTags.add(tag);
        }
        root.put(NBT_PLAN_TASKS, taskTags);

        ListTag phaseTags = new ListTag();
        for (var phase : plan.phases()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt(NBT_COMPONENT_ID, phase.componentId());
            tag.putString(NBT_COMPONENT_TYPE, phase.type().name());
            tag.putIntArray(NBT_PHASE_TASK_IDS, phase.taskIds());
            tag.putIntArray(NBT_PHASE_DEPENDENCIES, phase.dependencies());
            tag.putInt(NBT_EXECUTION_STEP, state.stepIndex(phase.index()));
            tag.putLong(NBT_STEP_REMAINING, state.stepRemaining(phase.index()));
            ListTag steps = new ListTag();
            for (var step : phase.steps()) {
                CompoundTag stepTag = new CompoundTag();
                stepTag.putInt(NBT_TASK_ID, step.taskId());
                stepTag.putLong(NBT_STEP_COUNT, step.count());
                steps.add(stepTag);
            }
            tag.put(NBT_PHASE_STEPS, steps);
            phaseTags.add(tag);
        }
        root.put(NBT_PLAN_PHASES, phaseTags);
        return root;
    }

    private static RestoredExecution readExecutionPlan(CompoundTag root, HolderLookup.Provider registries,
            Level level, GenericStack finalOutput, int expectedSignatureHash) {
        if (finalOutput == null || finalOutput.what() == null
                || !root.contains(NBT_PLAN_TASKS, Tag.TAG_LIST)
                || !root.contains(NBT_PLAN_PHASES, Tag.TAG_LIST)
                || !root.contains(NBT_EXECUTION_MODE, Tag.TAG_STRING)
                || !root.contains(NBT_SIGNATURE_USED, Tag.TAG_LIST)
                || !root.contains(NBT_SIGNATURE_EMITTED, Tag.TAG_LIST)
                || !root.contains(NBT_SIGNATURE_MISSING, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Persisted execution plan is incomplete");
        }
        ExecutionMode mode = ExecutionMode.valueOf(root.getString(NBT_EXECUTION_MODE));
        ListTag taskTags = root.getList(NBT_PLAN_TASKS, Tag.TAG_COMPOUND);
        List<ECOExecutionPlan.TaskSpec> tasks = new ArrayList<>();
        long[] remaining = new long[taskTags.size()];
        Map<IPatternDetails, Long> totals = new java.util.LinkedHashMap<>();
        for (int i = 0; i < taskTags.size(); i++) {
            CompoundTag tag = taskTags.getCompound(i);
            if (!tag.contains(NBT_TASK_ID, Tag.TAG_INT) || !tag.contains(NBT_TASK_TOTAL, Tag.TAG_LONG)
                    || !tag.contains(NBT_TASK_REMAINING, Tag.TAG_LONG)
                    || !tag.contains(NBT_TASK_PHASE, Tag.TAG_INT)
                    || !tag.contains(NBT_TASK_KIND, Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Persisted task entry is incomplete");
            }
            int id = tag.getInt(NBT_TASK_ID);
            if (id != i) throw new IllegalArgumentException("Persisted task ids are not dense");
            AEItemKey definition = AEItemKey.fromTag(registries, tag);
            IPatternDetails pattern = definition == null ? null : PatternDetailsHelper.decodePattern(definition, level);
            if (pattern == null) throw new IllegalArgumentException("Cannot decode persisted execution task " + id);
            long total = tag.getLong(NBT_TASK_TOTAL);
            remaining[id] = tag.getLong(NBT_TASK_REMAINING);
            var identity = PlanIdentity.patternIdentityFor(pattern);
            if (identity == null) throw new IllegalArgumentException("Persisted task has no identity");
            var kind = ECOExecutionPlan.TaskKind.valueOf(tag.getString(NBT_TASK_KIND));
            tasks.add(new ECOExecutionPlan.TaskSpec(id, identity, pattern,
                ECOExecutionPlan.PatternRuntimeInfo.from(pattern), total, tag.getInt(NBT_TASK_PHASE), kind));
            totals.put(pattern, total);
        }

        ListTag phaseTags = root.getList(NBT_PLAN_PHASES, Tag.TAG_COMPOUND);
        List<ECOExecutionPlan.PhaseSpec> phases = new ArrayList<>();
        int[] stepIndexes = new int[phaseTags.size()];
        long[] stepRemaining = new long[phaseTags.size()];
        List<ECOExecutionSchedule.ComponentExecutionPhase> schedulePhases = new ArrayList<>();
        for (int i = 0; i < phaseTags.size(); i++) {
            CompoundTag tag = phaseTags.getCompound(i);
            if (!tag.contains(NBT_COMPONENT_ID, Tag.TAG_INT)
                    || !tag.contains(NBT_COMPONENT_TYPE, Tag.TAG_STRING)
                    || !tag.contains(NBT_PHASE_TASK_IDS, Tag.TAG_INT_ARRAY)
                    || !tag.contains(NBT_PHASE_DEPENDENCIES, Tag.TAG_INT_ARRAY)
                    || !tag.contains(NBT_EXECUTION_STEP, Tag.TAG_INT)
                    || !tag.contains(NBT_STEP_REMAINING, Tag.TAG_LONG)
                    || !tag.contains(NBT_PHASE_STEPS, Tag.TAG_LIST)) {
                throw new IllegalArgumentException("Persisted phase entry is incomplete");
            }
            var type = ECOExecutionSchedule.Type.valueOf(tag.getString(NBT_COMPONENT_TYPE));
            List<Integer> taskIds = java.util.Arrays.stream(tag.getIntArray(NBT_PHASE_TASK_IDS)).boxed().toList();
            List<Integer> dependencies = java.util.Arrays.stream(tag.getIntArray(NBT_PHASE_DEPENDENCIES)).boxed().toList();
            stepIndexes[i] = tag.getInt(NBT_EXECUTION_STEP);
            stepRemaining[i] = tag.getLong(NBT_STEP_REMAINING);
            List<ECOExecutionPlan.ExecutionStep> steps = new ArrayList<>();
            ListTag stepTags = tag.getList(NBT_PHASE_STEPS, Tag.TAG_COMPOUND);
            for (int stepIndex = 0; stepIndex < stepTags.size(); stepIndex++) {
                CompoundTag step = stepTags.getCompound(stepIndex);
                if (!step.contains(NBT_TASK_ID, Tag.TAG_INT) || !step.contains(NBT_STEP_COUNT, Tag.TAG_LONG)) {
                    throw new IllegalArgumentException("Persisted compressed step is incomplete");
                }
                steps.add(new ECOExecutionPlan.ExecutionStep(step.getInt(NBT_TASK_ID),
                    step.getLong(NBT_STEP_COUNT)));
            }
            int componentId = tag.getInt(NBT_COMPONENT_ID);
            phases.add(new ECOExecutionPlan.PhaseSpec(i, componentId, type, taskIds, steps, dependencies));
            LinkedHashSet<IPatternDetails> patterns = new LinkedHashSet<>();
            for (int taskId : taskIds) patterns.add(tasks.get(taskId).pattern());
            schedulePhases.add(new ECOExecutionSchedule.ComponentExecutionPhase(componentId, type, patterns, List.of()));
        }
        Map<PlanIdentity.PatternIdentity, Long> taskSignature = PlanIdentity.taskSignature(totals);
        if (taskSignature == null) throw new IllegalArgumentException("Cannot rebuild persisted task signature");
        var signature = new PlanIdentity.Signature(finalOutput.what(), finalOutput.amount(), taskSignature,
            readKeyAmounts(root.getList(NBT_SIGNATURE_USED, Tag.TAG_COMPOUND), registries),
            readKeyAmounts(root.getList(NBT_SIGNATURE_EMITTED, Tag.TAG_COMPOUND), registries),
            readKeyAmounts(root.getList(NBT_SIGNATURE_MISSING, Tag.TAG_COMPOUND), registries));
        if (signature.hashCode() != expectedSignatureHash) {
            throw new IllegalArgumentException("Persisted plan signature does not match its payload");
        }
        List<ECOExecutionSchedule.PhaseDependency> dependencies = new ArrayList<>();
        for (var phase : phases) for (int producer : phase.dependencies())
            dependencies.add(new ECOExecutionSchedule.PhaseDependency(producer, phase.index()));
        var plan = new ECOExecutionPlan(signature, mode, tasks, phases,
            new ECOExecutionSchedule(schedulePhases, dependencies));
        return new RestoredExecution(plan, remaining, stepIndexes, stepRemaining);
    }

    private static ListTag writeKeyAmounts(Map<AEKey, Long> values, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        values.forEach((key, amount) -> result.add(GenericStack.writeTag(registries, new GenericStack(key, amount))));
        return result;
    }

    private static Map<AEKey, Long> readKeyAmounts(ListTag values, HolderLookup.Provider registries) {
        Map<AEKey, Long> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < values.size(); i++) {
            GenericStack stack = GenericStack.readTag(registries, values.getCompound(i));
            if (stack == null || stack.what() == null || stack.amount() < 0) {
                throw new IllegalArgumentException("Invalid persisted signature stack");
            }
            result.merge(stack.what(), stack.amount(), Math::addExact);
        }
        return Map.copyOf(result);
    }

    private record RestoredExecution(ECOExecutionPlan plan, long[] remaining, int[] stepIndexes,
            long[] stepRemaining) { }

    private @Nullable ListTag writeExecutionSchedule(HolderLookup.Provider registries) {
        if (executionSchedule == null) return null;
        ListTag result = new ListTag();
        for (var phase : executionSchedule.phases()) {
            ListTag patterns = writePatternDefinitions(phase.patternSet(), registries);
            ListTag witness = writePatternDefinitions(phase.cycleWitness(), registries);
            if (patterns == null || witness == null) return null;
            CompoundTag phaseTag = new CompoundTag();
            phaseTag.putInt(NBT_COMPONENT_ID, phase.componentId());
            phaseTag.putString(NBT_COMPONENT_TYPE, phase.type().name());
            phaseTag.put(NBT_COMPONENT_PATTERNS, patterns);
            phaseTag.put(NBT_COMPONENT_WITNESS, witness);
            result.add(phaseTag);
        }
        return result;
    }

    private static @Nullable ListTag writePatternDefinitions(Collection<IPatternDetails> patterns,
            HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (IPatternDetails pattern : patterns) {
            if (pattern == null || pattern.getDefinition() == null) return null;
            result.add(pattern.getDefinition().toTag(registries));
        }
        return result;
    }

    private static @Nullable ECOExecutionSchedule readExecutionSchedule(CompoundTag data,
            HolderLookup.Provider registries, Level level) {
        int version = data.getInt(NBT_EXECUTION_SCHEDULE_VERSION);
        // Versions 1 and 2 stored the same definition-based phases. It is safe to migrate them in place;
        // malformed or unknown versions remain fail-closed.
        if ((version != 1 && version != 2 && version != EXECUTION_SCHEDULE_VERSION)
                || !data.contains(NBT_EXECUTION_SCHEDULE, Tag.TAG_LIST)) return null;
        ListTag scheduleTag = data.getList(NBT_EXECUTION_SCHEDULE, Tag.TAG_COMPOUND);
        List<ECOExecutionSchedule.ComponentExecutionPhase> phases = new ArrayList<>();
        try {
            for (int i = 0; i < scheduleTag.size(); i++) {
                CompoundTag phaseTag = scheduleTag.getCompound(i);
                if (!phaseTag.contains(NBT_COMPONENT_ID, Tag.TAG_INT)
                        || !phaseTag.contains(NBT_COMPONENT_TYPE, Tag.TAG_STRING)
                        || !phaseTag.contains(NBT_COMPONENT_PATTERNS, Tag.TAG_LIST)
                        || !phaseTag.contains(NBT_COMPONENT_WITNESS, Tag.TAG_LIST)) return null;
                ECOExecutionSchedule.Type type = ECOExecutionSchedule.Type.valueOf(
                    phaseTag.getString(NBT_COMPONENT_TYPE));
                LinkedHashSet<IPatternDetails> patterns = new LinkedHashSet<>(readPatternDefinitions(
                    phaseTag.getList(NBT_COMPONENT_PATTERNS, Tag.TAG_COMPOUND), registries, level));
                List<IPatternDetails> witness = readPatternDefinitions(
                    phaseTag.getList(NBT_COMPONENT_WITNESS, Tag.TAG_COMPOUND), registries, level);
                phases.add(new ECOExecutionSchedule.ComponentExecutionPhase(
                    phaseTag.getInt(NBT_COMPONENT_ID), type, patterns, witness));
            }
            return new ECOExecutionSchedule(phases);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static List<IPatternDetails> readPatternDefinitions(ListTag definitions,
            HolderLookup.Provider registries, Level level) {
        List<IPatternDetails> result = new ArrayList<>();
        for (int i = 0; i < definitions.size(); i++) {
            AEItemKey definition = AEItemKey.fromTag(registries, definitions.getCompound(i));
            IPatternDetails details = definition == null ? null : PatternDetailsHelper.decodePattern(definition, level);
            if (details == null) throw new IllegalArgumentException("Cannot decode persisted execution pattern");
            result.add(details);
        }
        return List.copyOf(result);
    }

    static class TaskProgress {
        long value = 0;
    }

    enum PermanentExecutionError {
        CYCLE_METADATA_MISSING,
        EXECUTION_PLAN_INVALID,
        RECOVERY_ERROR
    }
}
