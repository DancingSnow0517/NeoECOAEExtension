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

package cn.dancingsnow.neoecoae.crafting.execution;

import java.util.HashMap;
import java.util.ArrayDeque;
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
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOGrowthDispatchBarrier;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPhaseScheduler;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlannedInputAllocation;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;

public class ExecutingCraftingJob extends cn.dancingsnow.neoecoae.api.me.ExecutingCraftingJob {
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
    final Map<IPatternDetails, TaskProgress> tasks = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>();
    {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Map<IPatternDetails, Object> legacyTasks = (Map) tasks;
        super.tasks = legacyTasks;
    }
    @Nullable
    final ECOExecutionPlan executionPlan;
    @Nullable
    final ECOExecutionRuntime executionRuntime;
    private ECOGrowthDispatchBarrier growthBarrier;
    final ElapsedTimeTracker timeTracker;
    GenericStack finalOutput;
    long remainingAmount;
    boolean exactOrder;
    final Map<AEKey, java.math.BigInteger> deferredStock = new LinkedHashMap<>();
    final Map<AEKey, java.math.BigInteger> deferredEmitted = new LinkedHashMap<>();
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
        this.exactOrder = plan instanceof cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan;
        this.waitingFor = createWaitingInventory(exactOrder, postCraftingDifference);

        // Fill waiting for and tasks
        this.timeTracker = new ElapsedTimeTracker();
        for (var entry : plan.emittedItems()) {
            waitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            timeTracker.addMaxItems(entry.getLongValue(), entry.getKey().getType());
        }
        // EAEP's vanilla/quantum mixins do not run on ECO's independent executor.
        // Use the normal persisted ledger so missing inputs survive reload and follow normal insertion/cancellation.
        for (var entry : cn.dancingsnow.neoecoae.compat.extendedaeplus.EAEPForcedCrafting.manualMissing(plan)) {
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
        if (plan instanceof cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan exact) {
            exactOrder = true;
            exact.exactTasks().forEach((pattern, count) -> tasks.get(pattern).setExact(count, count));
            deferredStock.putAll(exact.deferredStock());
            deferredEmitted.putAll(exact.deferredEmitted());
            timeTracker.startExactWork();
            exact.exactTasks().forEach((pattern, count) -> pattern.getOutputs().forEach(output ->
                timeTracker.addMaxItems(count.multiply(java.math.BigInteger.valueOf(output.amount()))
                    .multiply(java.math.BigInteger.valueOf(output.what().getAmountPerUnit())), output.what().getType())));
            for (var entry : plan.emittedItems()) timeTracker.addMaxItems(
                java.math.BigInteger.valueOf(entry.getLongValue()), entry.getKey().getType());
            deferredEmitted.forEach((key, amount) -> timeTracker.addMaxItems(amount, key.getType()));
        }
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
        this.exactOrder = data.getBoolean("exactOrder");
        this.waitingFor = createWaitingInventory(exactOrder, postCraftingDifference);
        this.waitingFor.readFromNBT(data.getList(NBT_WAITING_FOR, Tag.TAG_COMPOUND), registries);
        this.timeTracker = new ElapsedTimeTracker(data.getCompound(NBT_TIME_TRACKER));
        if (data.contains(NBT_PLAYER_ID, Tag.TAG_INT)) {
            this.playerId = data.getInt(NBT_PLAYER_ID);
        } else {
            this.playerId = null;
        }

        ListTag tasksTag = data.getList(NBT_TASKS, Tag.TAG_COMPOUND);
        boolean taskDefinitionLost = false;
        for (int i = 0; i < tasksTag.size(); ++i) {
            final CompoundTag item = tasksTag.getCompound(i);
            var pattern = AEItemKey.fromTag(registries, item);
            var details = PatternDetailsHelper.decodePattern(pattern, cpu.cpu.getLevel());
            if (details != null) {
                final TaskProgress tp = new TaskProgress();
                tp.value = item.getLong(NBT_CRAFTING_PROGRESS);
                if (item.contains("exactRemaining")) tp.setExact(
                    new java.math.BigInteger(item.getString("exactTotal")),
                    new java.math.BigInteger(item.getString("exactRemaining")));
                this.tasks.put(details, tp);
            } else taskDefinitionLost = true;
        }

        ECOExecutionPlan restoredPlan = null;
        exactOrder = data.getBoolean("exactOrder");
        readDeferred(data, "deferredStock", deferredStock, registries);
        readDeferred(data, "deferredEmitted", deferredEmitted, registries);
        ECOExecutionRuntime restoredRuntime = null;
        boolean executionMetadataLost = data.getBoolean(NBT_EXECUTION_PERSISTENCE_FAILED)
            || exactOrder && taskDefinitionLost;
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
        // AE2 may reconstruct equivalent pattern-detail wrappers while building the execution
        // plan. The old implementation searched every live task for every execution task and
        // recomputed the structural identity on each comparison (O(n^2)). Large cyclic plans
        // made this synchronous submission step visible to the server tick. Build the identity
        // index once and consume one live pattern per execution task instead.
        Map<PlanIdentity.PatternIdentity, ArrayDeque<IPatternDetails>> candidates = new HashMap<>();
        Map<IPatternDetails, ArrayDeque<IPatternDetails>> objectCandidates = new java.util.IdentityHashMap<>();
        for (IPatternDetails candidate : tasks.keySet()) {
            var identity = PlanIdentity.patternIdentityFor(candidate);
            if (identity != null) {
                candidates.computeIfAbsent(identity, ignored -> new ArrayDeque<>()).addLast(candidate);
            } else {
                objectCandidates.computeIfAbsent(candidate, ignored -> new ArrayDeque<>()).addLast(candidate);
            }
        }
        for (var task : plan.tasks()) {
            IPatternDetails match = null;
            var identity = PlanIdentity.patternIdentityFor(task.pattern());
            if (identity != null) {
                var matches = candidates.get(identity);
                if (matches != null) match = matches.pollFirst();
            } else {
                var matches = objectCandidates.get(task.pattern());
                if (matches != null) match = matches.pollFirst();
            }
            if (match == null) {
                throw new IllegalArgumentException("Execution plan task is absent from submitted plan: " + task.id());
            }
            TaskProgress progress = tasks.get(match);
            if (progress == null || progress.value < 0L || progress.value > task.totalCount()
                    || (validateTotals && progress.value != task.totalCount())) {
                throw new IllegalArgumentException("Execution plan count does not match submitted task: " + task.id());
            }
            result.put(task.id(), match);
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
            if (e.getValue().exactTotal != null) {
                item.putString("exactTotal", e.getValue().exactTotal.toString());
                item.putString("exactRemaining", e.getValue().exactRemaining.toString());
            }
            list.add(item);
        }
        data.put(NBT_TASKS, list);

        data.putLong(NBT_REMAINING_AMOUNT, remainingAmount);
        data.putBoolean("exactOrder", exactOrder);
        writeDeferred(data, "deferredStock", deferredStock, registries);
        writeDeferred(data, "deferredEmitted", deferredEmitted, registries);
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

    static class TaskProgress extends cn.dancingsnow.neoecoae.api.me.ExecutingCraftingJob.TaskProgress {
        private java.math.BigInteger exactTotal;
        private java.math.BigInteger exactRemaining;

        void setExact(java.math.BigInteger total, java.math.BigInteger remaining) {
            if (remaining.signum() < 0 || total.compareTo(remaining) < 0)
                throw new IllegalArgumentException("Invalid exact task progress");
            exactTotal = total;
            exactRemaining = remaining;
            value = cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(remaining);
        }
        java.math.BigInteger remainingExact() {
            return exactRemaining == null ? java.math.BigInteger.valueOf(value) : exactRemaining;
        }
        boolean isExact() { return exactTotal != null; }
        long completedBounded(long total) {
            return exactTotal == null ? Math.max(0L, total - value)
                : cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan.bounded(exactTotal.subtract(exactRemaining));
        }
        void accept(long count) {
            if (count < 0 || count > value) throw new IllegalArgumentException("Dispatch exceeds remaining task");
            if (exactRemaining == null) value -= count;
            else setExact(exactTotal, exactRemaining.subtract(java.math.BigInteger.valueOf(count)));
        }

        void accept(java.math.BigInteger count) {
            if (count.signum() < 0 || count.compareTo(remainingExact()) > 0)
                throw new IllegalArgumentException("Dispatch exceeds remaining task");
            if (exactRemaining == null) accept(count.longValueExact());
            else setExact(exactTotal, exactRemaining.subtract(count));
        }
    }

    private static ListCraftingInventory createWaitingInventory(boolean exact, CraftingDifferenceListener listener) {
        if (!exact) return new ListCraftingInventory(listener::onCraftingDifference);
        var inventory = new cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory(listener::onCraftingDifference);
        inventory.setEnabled(true);
        return inventory;
    }

    private static void writeDeferred(CompoundTag data, String name, Map<AEKey, java.math.BigInteger> amounts,
            HolderLookup.Provider registries) {
        var list = new ListTag();
        amounts.forEach((key, amount) -> {
            var entry = new CompoundTag();
            entry.put("key", GenericStack.writeTag(registries, new GenericStack(key, 1)));
            entry.putString("amount", amount.toString());
            list.add(entry);
        });
        data.put(name, list);
    }

    private static void readDeferred(CompoundTag data, String name, Map<AEKey, java.math.BigInteger> amounts,
            HolderLookup.Provider registries) {
        var list = data.getList(name, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            var key = GenericStack.readTag(registries, entry.getCompound("key"));
            var amount = new java.math.BigInteger(entry.getString("amount"));
            if (key == null || amount.signum() <= 0) throw new IllegalArgumentException("Invalid deferred material");
            amounts.put(key.what(), amount);
        }
    }
}
