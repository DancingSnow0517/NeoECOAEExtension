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
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.util.NEMath;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOGrowthDispatchBarrier;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlannedInputAllocation;
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
    private static final String NBT_PERMANENT_EXECUTION_ERROR = "permanentExecutionError";
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
    @Nullable
    String permanentExecutionError;

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
        if (executionPlan == null) {
            this.executionRuntime = null;
        } else {
            Map<Integer, IPatternDetails> boundPatterns = bindExecutionPatterns(executionPlan);
            this.executionRuntime = new ECOExecutionRuntime(executionPlan, boundPatterns,
                bindExecutionProgress(executionPlan, boundPatterns));
        }
        this.link = link;
        this.playerId = playerId;
        this.suspended = false;
        this.permanentExecutionError = null;
    }

    ExecutingCraftingJob(CompoundTag data, HolderLookup.Provider registries,
            CraftingDifferenceListener postCraftingDifference, ECOCraftingCPULogic cpu) {
        this.link = new CraftingLink(data.getCompound(NBT_LINK), cpu.cpu);

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
                restoredPlan = ECOExecutionPlanNbtCodec.decode(
                    data.getCompound(NBT_EXECUTION_PLAN), registries, cpu, this.finalOutput);
                Map<Integer, IPatternDetails> boundPatterns = bindExecutionPatterns(restoredPlan, false);
                TaskProgress[] boundProgress = bindExecutionProgress(restoredPlan, boundPatterns);
                if (data.contains(NBT_EXECUTION_RUNTIME)) {
                    restoredRuntime = ECOExecutionRuntime.fromNBT(restoredPlan, boundPatterns,
                        boundProgress, data.getCompound(NBT_EXECUTION_RUNTIME), registries);
                } else {
                    restoredRuntime = new ECOExecutionRuntime(restoredPlan, boundPatterns, boundProgress);
                }
            } catch (RuntimeException failure) {
                executionMetadataLost = true;
            }
        }
        this.executionPlan = restoredPlan;
        this.executionRuntime = restoredRuntime;
        this.suspended = data.getBoolean(NBT_SUSPENDED) || executionMetadataLost;
        this.permanentExecutionError = data.contains(NBT_PERMANENT_EXECUTION_ERROR, Tag.TAG_STRING)
            ? data.getString(NBT_PERMANENT_EXECUTION_ERROR) : null;
        if (executionMetadataLost && this.permanentExecutionError == null) {
            this.permanentExecutionError = "EXECUTION_METADATA_LOST";
        }
    }

    private Map<Integer, IPatternDetails> bindExecutionPatterns(ECOExecutionPlan plan) {
        return bindExecutionPatterns(plan, true);
    }

    private Map<Integer, IPatternDetails> bindExecutionPatterns(ECOExecutionPlan plan, boolean validateTotals) {
        Map<Integer, IPatternDetails> result = new HashMap<>();
        java.util.Set<IPatternDetails> usedMatches = java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<>());
        for (var task : plan.tasks()) {
            IPatternDetails match = tasks.keySet().stream()
                .filter(candidate -> !usedMatches.contains(candidate))
                .filter(candidate -> ECOPhaseScheduler.samePattern(candidate, task.pattern()))
                .findFirst().orElse(null);
            if (match == null) {
                throw new IllegalArgumentException("Execution plan task is absent from submitted plan: " + task.id());
            }
            TaskProgress progress = tasks.get(match);
            if (progress == null || progress.value < 0L || progress.value > task.totalCount()
                    || (validateTotals && progress.value != task.totalCount())) {
                throw new IllegalArgumentException("Execution plan count does not match submitted task: " + task.id());
            }
            result.put(task.id(), match);
            usedMatches.add(match);
        }
        return result;
    }

    private TaskProgress[] bindExecutionProgress(ECOExecutionPlan plan,
            Map<Integer, IPatternDetails> boundPatterns) {
        TaskProgress[] result = new TaskProgress[plan.tasks().size()];
        for (var task : plan.tasks()) {
            IPatternDetails pattern = boundPatterns.get(task.id());
            TaskProgress progress = tasks.get(pattern);
            if (progress == null) {
                throw new IllegalArgumentException("Execution plan task has no live progress: " + task.id());
            }
            result[task.id()] = progress;
        }
        return result;
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
        if (permanentExecutionError == null) {
            data.remove(NBT_PERMANENT_EXECUTION_ERROR);
        } else {
            data.putString(NBT_PERMANENT_EXECUTION_ERROR, permanentExecutionError);
        }
        if (executionPlan != null && executionRuntime != null) {
            try {
                data.put(NBT_EXECUTION_PLAN, ECOExecutionPlanNbtCodec.encode(executionPlan, registries));
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

    void failPermanently(String reason) {
        permanentExecutionError = reason;
        suspended = true;
    }

    static class TaskProgress {
        long value = 0;
    }
}
