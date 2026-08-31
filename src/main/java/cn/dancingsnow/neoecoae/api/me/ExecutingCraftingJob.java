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
import java.util.ArrayList;

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
    private static final String NBT_REQUIRES_ORDERED_CYCLE = "requiresOrderedCycleExecution";
    private static final String NBT_CURRENT_COMPONENT = "currentComponentIndex";
    private static final String NBT_EXECUTION_SCHEDULE = "executionSchedule";
    private static final String NBT_EXECUTION_SCHEDULE_VERSION = "executionScheduleVersion";
    private static final int EXECUTION_SCHEDULE_VERSION = 1;
    private static final String NBT_COMPONENT_ID = "componentId";
    private static final String NBT_COMPONENT_TYPE = "type";
    private static final String NBT_COMPONENT_PATTERNS = "patterns";
    private static final String NBT_COMPONENT_WITNESS = "cycleWitness";

    final CraftingLink link;
    final ListCraftingInventory waitingFor;
    final Map<IPatternDetails, TaskProgress> tasks = new HashMap<>();
    final List<IPatternDetails> cycleWitness = new ArrayList<>();
    int cycleWitnessIndex;
    int currentComponentIndex;
    boolean requiresOrderedCycleExecution;
    boolean cycleWitnessMissing;
    boolean cycleMetadataErrorLogged;
    ECOExecutionSchedule executionSchedule;
    final ElapsedTimeTracker timeTracker;
    final ECOFinalOutputBuffer bufferedFinalOutput;
    GenericStack finalOutput;
    long remainingAmount;
    @Nullable
    Integer playerId;
    boolean suspended;

    @Nullable ECOExecutionSchedule.ComponentExecutionPhase activePhase() {
        if (!requiresOrderedCycleExecution || executionSchedule == null
                || currentComponentIndex >= executionSchedule.phases().size()) return null;
        return executionSchedule.phases().get(currentComponentIndex);
    }

    boolean phaseComplete(ECOExecutionSchedule.ComponentExecutionPhase phase) {
        return ECOPhaseScheduler.isComplete(phase, cycleWitnessIndex,
            this::remainingTasksFor,
            key -> waitingFor.extract(key, Long.MAX_VALUE, Actionable.SIMULATE) > 0);
    }

    long remainingTasksFor(IPatternDetails pattern) {
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
        while (activePhase() != null && phaseComplete(activePhase())) {
            currentComponentIndex++;
            cycleWitnessIndex = 0;
        }
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
        boolean recoveredPlanningResult = false;
        ECOPlanningResultRegistry.RecoveredSchedule recoveredSchedule = null;
        if (planningResult == null || planningResult.executionSchedule().phases().isEmpty()) {
            recoveredSchedule = ECOPlanningResultRegistry.recoverSchedule(plan);
            recoveredPlanningResult = recoveredSchedule != null && !recoveredSchedule.schedule().phases().isEmpty();
        }
        if (planningResult != null) {
            ECOPlanningResult r = planningResult;
            cycleWitness.addAll(r.cycleWitness());
            executionSchedule = r.executionSchedule();
        }
        if (recoveredPlanningResult) {
            executionSchedule = recoveredSchedule.schedule();
            cycleWitness.clear();
            executionSchedule.phases().stream()
                .filter(phase -> phase.type() == ECOExecutionSchedule.Type.CYCLE)
                .forEach(phase -> cycleWitness.addAll(phase.cycleWitness()));
        }
        if (recoveredPlanningResult) {
            LOGGER.info(
                "[ECO-EXEC] recovered execution schedule finalOutput={} phases={} matchMode={}",
                plan.finalOutput(), executionSchedule.phases().size(), recoveredSchedule.matchMode());
        } else if (executionSchedule == null || executionSchedule.phases().isEmpty()) {
            LOGGER.warn(
                "[ECO-EXEC] no registered cycle schedule matched submitted plan finalOutput={} "
                    + "patternKinds={} emittedKinds={} registeredCycleSchedules={} mismatch={}",
                plan.finalOutput(), plan.patternTimes().size(), plan.emittedItems().size(),
                ECOPlanningResultRegistry.registeredScheduleCount(),
                ECOPlanningResultRegistry.mismatchDiagnostic(plan));
        }
        requiresOrderedCycleExecution = ECOPhaseScheduler.requiresComponentScheduling(executionSchedule);
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

        // Without a level the patterns cannot be decoded, which would silently restore a job with an empty
        // task list. Fail instead, so the caller keeps the persisted data and retries on the next reform.
        Level level = logic.cpu.getLevel();
        if (level == null) {
            throw new IllegalStateException("Cannot restore an ECO crafting job without a level");
        }

        ListTag tasksTag = data.getList(NBT_TASKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < tasksTag.size(); ++i) {
            final CompoundTag item = tasksTag.getCompound(i);
            var pattern = AEItemKey.fromTag(registries, item);
            var details = PatternDetailsHelper.decodePattern(pattern, level);
            if (details != null) {
                final TaskProgress tp = new TaskProgress();
                tp.value = item.getLong(NBT_CRAFTING_PROGRESS);
                this.tasks.put(details, tp);
            }
        }

        this.suspended = data.getBoolean(NBT_SUSPENDED);
        this.cycleWitnessIndex = Math.max(0, data.getInt(NBT_CYCLE_WITNESS_INDEX));
        this.currentComponentIndex = Math.max(0, data.getInt(NBT_CURRENT_COMPONENT));
        this.requiresOrderedCycleExecution = data.getBoolean(NBT_REQUIRES_ORDERED_CYCLE);
        this.executionSchedule = readExecutionSchedule(data, registries, level);
        if (executionSchedule != null) {
            executionSchedule.phases().stream()
                .filter(phase -> phase.type() == ECOExecutionSchedule.Type.CYCLE)
                .forEach(phase -> cycleWitness.addAll(phase.cycleWitness()));
        }
        this.cycleWitnessMissing = requiresOrderedCycleExecution && executionSchedule == null;
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
        data.putBoolean(NBT_REQUIRES_ORDERED_CYCLE, requiresOrderedCycleExecution);
        ListTag scheduleTag = writeExecutionSchedule(registries);
        if (scheduleTag != null) {
            data.putInt(NBT_EXECUTION_SCHEDULE_VERSION, EXECUTION_SCHEDULE_VERSION);
            data.put(NBT_EXECUTION_SCHEDULE, scheduleTag);
        }
        return data;
    }

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
        if (data.getInt(NBT_EXECUTION_SCHEDULE_VERSION) != EXECUTION_SCHEDULE_VERSION
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
}
