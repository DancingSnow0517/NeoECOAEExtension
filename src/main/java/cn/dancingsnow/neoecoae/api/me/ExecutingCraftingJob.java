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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

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
import cn.dancingsnow.neoecoae.util.NEMath;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOGrowthDispatchBarrier;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;

public class ExecutingCraftingJob {
    private static final String NBT_LINK = "link";
    private static final String NBT_PLAYER_ID = "playerId";
    private static final String NBT_FINAL_OUTPUT = "finalOutput";
    private static final String NBT_WAITING_FOR = "waitingFor";
    private static final String NBT_TIME_TRACKER = "timeTracker";
    private static final String NBT_REMAINING_AMOUNT = "remainingAmount";
    private static final String NBT_TASKS = "tasks";
    private static final String NBT_SUSPENDED = "suspended";
    private static final String NBT_CRAFTING_PROGRESS = "#craftingProgress";
    private static final String NBT_EXECUTION_PLAN = "executionPlan";
    private static final String NBT_EXECUTION_RUNTIME = "executionRuntime";
    private static final String NBT_EXECUTION_PERSISTENCE_FAILED = "executionPersistenceFailed";

    final CraftingLink link;
    final ListCraftingInventory waitingFor;
    final Map<IPatternDetails, TaskProgress> tasks = new HashMap<>();
    @Nullable
    final ECOExecutionPlan executionPlan;
    @Nullable
    final ECOExecutionRuntime executionRuntime;
    private ECOGrowthDispatchBarrier growthBarrier;
    final ElapsedTimeTracker timeTracker;
    GenericStack finalOutput;
    long remainingAmount;
    @Nullable
    Integer playerId;
    boolean suspended;

    @FunctionalInterface
    interface CraftingDifferenceListener {
        void onCraftingDifference(AEKey what);
    }

    ExecutingCraftingJob(ICraftingPlan plan, CraftingDifferenceListener postCraftingDifference, CraftingLink link,
            @Nullable Integer playerId) {
        this(plan, null, postCraftingDifference, link, playerId);
    }

    ExecutingCraftingJob(ICraftingPlan plan, @Nullable ECOExecutionPlan executionPlan,
            CraftingDifferenceListener postCraftingDifference, CraftingLink link, @Nullable Integer playerId) {
        this.finalOutput = plan.finalOutput();
        this.remainingAmount = this.finalOutput.amount();
        this.waitingFor = new ListCraftingInventory(postCraftingDifference::onCraftingDifference);

        // Fill waiting for and tasks
        this.timeTracker = new ElapsedTimeTracker();
        for (var entry : plan.emittedItems()) {
            waitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            timeTracker.addMaxItems(entry.getLongValue(), entry.getKey().getType());
        }
        for (var entry : plan.patternTimes().entrySet()) {
            tasks.computeIfAbsent(entry.getKey(), p -> new TaskProgress()).value += entry.getValue();
            for (var output : entry.getKey().getOutputs()) {
                var amount = NEMath.saturatingMultiply(output.amount(), entry.getValue());
                amount = NEMath.saturatingMultiply(amount, output.what().getAmountPerUnit());
                timeTracker.addMaxItems(amount, output.what().getType());
            }
        }
        this.executionPlan = executionPlan;
        this.executionRuntime = executionPlan == null ? null : new ECOExecutionRuntime(executionPlan,
            bindExecutionPatterns(executionPlan));
        this.link = link;
        this.playerId = playerId;
        this.suspended = false;
    }

    ExecutingCraftingJob(CompoundTag data, HolderLookup.Provider registries,
            CraftingDifferenceListener postCraftingDifference, ECOCraftingCPULogic cpu) {
        this.link = new CraftingLink(data.getCompound(NBT_LINK), cpu.cpu);
        IGrid grid = cpu.cpu.getGrid();
        if (grid != null) {
            ((CraftingService) grid.getCraftingService()).addLink(link);
        }

        this.finalOutput = GenericStack.readTag(registries, data.getCompound(NBT_FINAL_OUTPUT));
        this.remainingAmount = data.getLong(NBT_REMAINING_AMOUNT);
        this.waitingFor = new ListCraftingInventory(postCraftingDifference::onCraftingDifference);
        this.waitingFor.readFromNBT(data.getList(NBT_WAITING_FOR, Tag.TAG_COMPOUND), registries);
        this.timeTracker = new ElapsedTimeTracker(data.getCompound(NBT_TIME_TRACKER));
        if (data.contains(NBT_PLAYER_ID, Tag.TAG_INT)) {
            this.playerId = data.getInt(NBT_PLAYER_ID);
        } else {
            this.playerId = null;
        }

        ListTag tasksTag = data.getList(NBT_TASKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < tasksTag.size(); ++i) {
            final CompoundTag item = tasksTag.getCompound(i);
            var pattern = AEItemKey.fromTag(registries, item);
            var details = PatternDetailsHelper.decodePattern(pattern, cpu.cpu.getLevel());
            if (details != null) {
                final TaskProgress tp = new TaskProgress();
                tp.value = item.getLong(NBT_CRAFTING_PROGRESS);
                this.tasks.put(details, tp);
            }
        }

        ECOExecutionPlan restoredPlan = null;
        ECOExecutionRuntime restoredRuntime = null;
        boolean executionMetadataLost = data.getBoolean(NBT_EXECUTION_PERSISTENCE_FAILED);
        if (data.contains(NBT_EXECUTION_PLAN)) {
            try {
                restoredPlan = readExecutionPlan(data.getCompound(NBT_EXECUTION_PLAN), registries, cpu, this.finalOutput);
                Map<Integer, IPatternDetails> boundPatterns = bindExecutionPatterns(restoredPlan, false);
                if (data.contains(NBT_EXECUTION_RUNTIME)) {
                    restoredRuntime = ECOExecutionRuntime.fromNBT(restoredPlan, boundPatterns,
                        data.getCompound(NBT_EXECUTION_RUNTIME), registries);
                } else {
                    restoredRuntime = new ECOExecutionRuntime(restoredPlan, boundPatterns);
                }
            } catch (RuntimeException failure) {
                executionMetadataLost = true;
            }
        }
        this.executionPlan = restoredPlan;
        this.executionRuntime = restoredRuntime;
        this.suspended = data.getBoolean(NBT_SUSPENDED) || executionMetadataLost;
    }

    private Map<Integer, IPatternDetails> bindExecutionPatterns(ECOExecutionPlan plan) {
        return bindExecutionPatterns(plan, true);
    }

    private Map<Integer, IPatternDetails> bindExecutionPatterns(ECOExecutionPlan plan, boolean validateTotals) {
        Map<Integer, IPatternDetails> result = new HashMap<>();
        for (var task : plan.tasks()) {
            IPatternDetails match = tasks.keySet().stream()
                .filter(candidate -> ECOPhaseScheduler.samePattern(candidate, task.pattern()))
                .findFirst().orElse(null);
            if (match == null) {
                throw new IllegalArgumentException("Execution plan task is absent from submitted plan: " + task.id());
            }
            TaskProgress progress = tasks.get(match);
            if (progress == null || (validateTotals && progress.value != task.totalCount())) {
                throw new IllegalArgumentException("Execution plan count does not match submitted task: " + task.id());
            }
            result.put(task.id(), match);
        }
        return result;
    }

    private static CompoundTag writeExecutionPlan(ECOExecutionPlan plan, HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        data.putString("mode", plan.mode().name());

        ListTag taskTags = new ListTag();
        for (var task : plan.tasks()) {
            if (task.pattern().getDefinition() == null) {
                throw new IllegalStateException("Cannot persist an execution task without a pattern definition");
            }
            CompoundTag taskTag = new CompoundTag();
            taskTag.putInt("id", task.id());
            taskTag.putLong("total", task.totalCount());
            taskTag.putInt("phase", task.phaseIndex());
            taskTag.putString("kind", task.kind().name());
            taskTag.put("pattern", task.pattern().getDefinition().toTag(registries));
            taskTags.add(taskTag);
        }
        data.put("tasks", taskTags);

        ListTag phaseTags = new ListTag();
        for (var phase : plan.phases()) {
            CompoundTag phaseTag = new CompoundTag();
            phaseTag.putInt("index", phase.index());
            phaseTag.putInt("component", phase.componentId());
            phaseTag.putString("type", phase.type().name());
            phaseTag.putIntArray("tasks", phase.taskIds().stream().mapToInt(Integer::intValue).toArray());
            phaseTag.putIntArray("dependencies", phase.dependencies().stream().mapToInt(Integer::intValue).toArray());

            ListTag steps = new ListTag();
            for (var step : phase.steps()) {
                CompoundTag stepTag = new CompoundTag();
                stepTag.putInt("task", step.taskId());
                stepTag.putLong("count", step.count());
                steps.add(stepTag);
            }
            phaseTag.put("steps", steps);

            ListTag dynamic = new ListTag();
            for (var entry : phase.dynamicFirings().entrySet()) {
                CompoundTag firing = new CompoundTag();
                firing.putInt("task", entry.getKey());
                firing.putLong("count", entry.getValue());
                dynamic.add(firing);
            }
            phaseTag.put("dynamic", dynamic);

            ListTag seeds = new ListTag();
            for (var entry : phase.initialSeed().entrySet()) {
                seeds.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getValue())));
            }
            phaseTag.put("initialSeed", seeds);
            phaseTags.add(phaseTag);
        }
        data.put("phases", phaseTags);
        return data;
    }

    private static ECOExecutionPlan readExecutionPlan(CompoundTag data, HolderLookup.Provider registries,
            ECOCraftingCPULogic cpu, GenericStack finalOutput) {
        ExecutionMode mode = ExecutionMode.valueOf(data.getString("mode"));
        ListTag taskTags = data.getList("tasks", Tag.TAG_COMPOUND);
        List<ECOExecutionPlan.TaskSpec> tasks = new ArrayList<>(taskTags.size());
        Map<Integer, IPatternDetails> patternsById = new HashMap<>();
        for (int i = 0; i < taskTags.size(); i++) {
            CompoundTag taskTag = taskTags.getCompound(i);
            int id = taskTag.getInt("id");
            if (id != i) throw new IllegalArgumentException("Persisted execution task ids are not dense");
            AEItemKey definition = AEItemKey.fromTag(registries, taskTag.getCompound("pattern"));
            IPatternDetails pattern = PatternDetailsHelper.decodePattern(definition, cpu.cpu.getLevel());
            if (pattern == null) throw new IllegalArgumentException("Persisted execution pattern cannot be decoded");
            PlanIdentity.PatternIdentity identity = PlanIdentity.patternIdentityFor(pattern);
            if (identity == null) throw new IllegalArgumentException("Persisted execution pattern has no identity");
            patternsById.put(id, pattern);
            tasks.add(new ECOExecutionPlan.TaskSpec(id, identity, pattern,
                ECOExecutionPlan.PatternRuntimeInfo.from(pattern), taskTag.getLong("total"),
                taskTag.getInt("phase"), ECOExecutionPlan.TaskKind.valueOf(taskTag.getString("kind"))));
        }

        ListTag phaseTags = data.getList("phases", Tag.TAG_COMPOUND);
        List<ECOExecutionPlan.PhaseSpec> phases = new ArrayList<>(phaseTags.size());
        List<ECOExecutionSchedule.ComponentExecutionPhase> schedulePhases = new ArrayList<>(phaseTags.size());
        List<ECOExecutionSchedule.PhaseDependency> dependencies = new ArrayList<>();
        for (int i = 0; i < phaseTags.size(); i++) {
            CompoundTag phaseTag = phaseTags.getCompound(i);
            int index = phaseTag.getInt("index");
            if (index != i) throw new IllegalArgumentException("Persisted execution phase ids are not dense");
            ECOExecutionSchedule.Type type = ECOExecutionSchedule.Type.valueOf(phaseTag.getString("type"));
            List<Integer> taskIds = Arrays.stream(phaseTag.getIntArray("tasks")).boxed().toList();
            List<Integer> phaseDependencies = Arrays.stream(phaseTag.getIntArray("dependencies")).boxed().toList();
            List<ECOExecutionPlan.ExecutionStep> steps = new ArrayList<>();
            ListTag stepTags = phaseTag.getList("steps", Tag.TAG_COMPOUND);
            for (int stepIndex = 0; stepIndex < stepTags.size(); stepIndex++) {
                CompoundTag step = stepTags.getCompound(stepIndex);
                steps.add(new ECOExecutionPlan.ExecutionStep(step.getInt("task"), step.getLong("count")));
            }
            Map<Integer, Long> dynamic = new LinkedHashMap<>();
            ListTag dynamicTags = phaseTag.getList("dynamic", Tag.TAG_COMPOUND);
            for (int dynamicIndex = 0; dynamicIndex < dynamicTags.size(); dynamicIndex++) {
                CompoundTag firing = dynamicTags.getCompound(dynamicIndex);
                dynamic.put(firing.getInt("task"), firing.getLong("count"));
            }
            Map<AEKey, Long> seed = new LinkedHashMap<>();
            ListTag seedTags = phaseTag.getList("initialSeed", Tag.TAG_COMPOUND);
            for (int seedIndex = 0; seedIndex < seedTags.size(); seedIndex++) {
                GenericStack stack = GenericStack.readTag(registries, seedTags.getCompound(seedIndex));
                if (stack == null || stack.amount() <= 0L) throw new IllegalArgumentException("Invalid persisted seed");
                seed.put(stack.what(), stack.amount());
            }
            phases.add(new ECOExecutionPlan.PhaseSpec(index, phaseTag.getInt("component"), type,
                taskIds, steps, phaseDependencies, dynamic, seed));

            LinkedHashSet<IPatternDetails> patternSet = new LinkedHashSet<>();
            for (int taskId : taskIds) patternSet.add(patternsById.get(taskId));
            schedulePhases.add(new ECOExecutionSchedule.ComponentExecutionPhase(
                phaseTag.getInt("component"), type, patternSet, List.of()));
            for (int dependency : phaseDependencies) {
                dependencies.add(new ECOExecutionSchedule.PhaseDependency(dependency, index));
            }
        }

        Map<PlanIdentity.PatternIdentity, Long> signatureTasks = new LinkedHashMap<>();
        for (var task : tasks) signatureTasks.put(task.identity(), task.totalCount());
        if (finalOutput == null) throw new IllegalArgumentException("Persisted execution job has no final output");
        PlanIdentity.Signature signature = new PlanIdentity.Signature(
            finalOutput.what(), finalOutput.amount(), signatureTasks, Map.of(), Map.of(), Map.of());
        return new ECOExecutionPlan(signature, mode, tasks, phases,
            new ECOExecutionSchedule(schedulePhases, dependencies));
    }

    CompoundTag writeToNBT(HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();

        CompoundTag linkData = new CompoundTag();
        link.writeToNBT(linkData);
        data.put(NBT_LINK, linkData);

        data.put(NBT_FINAL_OUTPUT, GenericStack.writeTag(registries, finalOutput));

        data.put(NBT_WAITING_FOR, waitingFor.writeToNBT(registries));
        data.put(NBT_TIME_TRACKER, timeTracker.writeToNBT());

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
        if (executionPlan != null && executionRuntime != null) {
            try {
                data.put(NBT_EXECUTION_PLAN, writeExecutionPlan(executionPlan, registries));
                CompoundTag runtime = new CompoundTag();
                executionRuntime.writeToNBT(runtime, registries);
                data.put(NBT_EXECUTION_RUNTIME, runtime);
            } catch (RuntimeException failure) {
                // A phased job must never be restored as a native unordered job when a pattern definition cannot
                // be encoded. The read path turns this marker into a suspended job instead.
                data.putBoolean(NBT_EXECUTION_PERSISTENCE_FAILED, true);
            }
        }
        return data;
    }

    boolean canDispatchAfterGrowth(IPatternDetails pattern) {
        // Derived from task patterns, including after NBT restore; no independent persisted cursor is needed.
        if (growthBarrier == null) {
            growthBarrier = new ECOGrowthDispatchBarrier(tasks.keySet());
        }
        return growthBarrier.canDispatch(pattern, member -> {
            var task = tasks.get(member);
            return task == null ? 0L : task.value;
        });
    }

    Map<IPatternDetails, Long> remainingTaskCounts() {
        Map<IPatternDetails, Long> result = new HashMap<>();
        tasks.forEach((pattern, progress) -> result.put(pattern, Math.max(0L, progress.value)));
        return result;
    }

    static class TaskProgress {
        long value = 0;
    }
}
