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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
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
import cn.dancingsnow.neoecoae.impl.crafting.planner.ae2.ECOPlannedInputs;

public class ExecutingCraftingJob {
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
    private static final String NBT_PLANNED_INPUTS = "plannedInputs";
    private static final String NBT_PLANNED_INPUT_COUNT = "count";
    private static final String NBT_PLANNED_INPUT_STACKS = "stacks";

    final CraftingLink link;
    final ListCraftingInventory waitingFor;
    final Map<IPatternDetails, TaskProgress> tasks = new HashMap<>();
    final Map<IPatternDetails, ArrayDeque<ECOPlannedInputs.PlannedInputBatch>> plannedInputs = new HashMap<>();
    final ElapsedTimeTracker timeTracker;
    final ECOFinalOutputBuffer bufferedFinalOutput;
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
        this.plannedInputs.putAll(ECOPlannedInputs.take(plan));
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

        boolean invalidPlannedInputs = false;
        ListTag tasksTag = data.getList(NBT_TASKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < tasksTag.size(); ++i) {
            final CompoundTag item = tasksTag.getCompound(i);
            var pattern = AEItemKey.fromTag(registries, item);
            var details = PatternDetailsHelper.decodePattern(pattern, logic.cpu.getLevel());
            if (details != null) {
                final TaskProgress tp = new TaskProgress();
                tp.value = item.getLong(NBT_CRAFTING_PROGRESS);
                this.tasks.put(details, tp);
                ArrayDeque<ECOPlannedInputs.PlannedInputBatch> selections = readPlannedInputs(
                    item, registries, details.getInputs().length);
                if (selections == null) {
                    invalidPlannedInputs = true;
                } else if (!selections.isEmpty()) {
                    this.plannedInputs.put(details, selections);
                }
            }
        }

        this.suspended = data.getBoolean(NBT_SUSPENDED) || invalidPlannedInputs;
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
            writePlannedInputs(item, registries, plannedInputs.get(e.getKey()));
            list.add(item);
        }
        data.put(NBT_TASKS, list);

        data.putLong(NBT_REMAINING_AMOUNT, remainingAmount);
        if (this.playerId != null) {
            data.putInt(NBT_PLAYER_ID, this.playerId);
        }

        data.putBoolean(NBT_SUSPENDED, suspended);
        return data;
    }

    @Nullable
    List<GenericStack> peekPlannedInputs(IPatternDetails details) {
        ArrayDeque<ECOPlannedInputs.PlannedInputBatch> batches = plannedInputs.get(details);
        ECOPlannedInputs.PlannedInputBatch batch = batches == null ? null : batches.peekFirst();
        return batch == null ? null : batch.selectedInputs();
    }

    void consumePlannedInputs(IPatternDetails details) {
        ArrayDeque<ECOPlannedInputs.PlannedInputBatch> batches = plannedInputs.get(details);
        if (batches == null || batches.isEmpty()) {
            return;
        }
        ECOPlannedInputs.PlannedInputBatch batch = batches.getFirst();
        batch.consumeOne();
        if (batch.remaining() == 0L) {
            batches.removeFirst();
        }
        if (batches.isEmpty()) {
            plannedInputs.remove(details);
        }
    }

    private static void writePlannedInputs(
        CompoundTag taskTag,
        HolderLookup.Provider registries,
        @Nullable ArrayDeque<ECOPlannedInputs.PlannedInputBatch> batches
    ) {
        if (batches == null || batches.isEmpty()) {
            return;
        }
        ListTag serializedBatches = new ListTag();
        for (ECOPlannedInputs.PlannedInputBatch batch : batches) {
            CompoundTag serializedBatch = new CompoundTag();
            serializedBatch.putLong(NBT_PLANNED_INPUT_COUNT, batch.remaining());
            ListTag stacks = new ListTag();
            for (GenericStack stack : batch.selectedInputs()) {
                stacks.add(GenericStack.writeTag(registries, stack));
            }
            serializedBatch.put(NBT_PLANNED_INPUT_STACKS, stacks);
            serializedBatches.add(serializedBatch);
        }
        taskTag.put(NBT_PLANNED_INPUTS, serializedBatches);
    }

    private static @Nullable ArrayDeque<ECOPlannedInputs.PlannedInputBatch> readPlannedInputs(
        CompoundTag taskTag,
        HolderLookup.Provider registries,
        int expectedInputs
    ) {
        ArrayDeque<ECOPlannedInputs.PlannedInputBatch> result = new ArrayDeque<>();
        if (!taskTag.contains(NBT_PLANNED_INPUTS, Tag.TAG_LIST)) {
            return result;
        }
        ListTag serializedBatches = taskTag.getList(NBT_PLANNED_INPUTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < serializedBatches.size(); i++) {
            CompoundTag serializedBatch = serializedBatches.getCompound(i);
            long count = serializedBatch.getLong(NBT_PLANNED_INPUT_COUNT);
            ListTag serializedStacks = serializedBatch.getList(NBT_PLANNED_INPUT_STACKS, Tag.TAG_COMPOUND);
            if (count <= 0L || serializedStacks.size() != expectedInputs) {
                return null;
            }
            List<GenericStack> stacks = new ArrayList<>(serializedStacks.size());
            for (int j = 0; j < serializedStacks.size(); j++) {
                GenericStack stack = GenericStack.readTag(registries, serializedStacks.getCompound(j));
                if (stack == null || stack.amount() <= 0L) {
                    return null;
                }
                stacks.add(stack);
            }
            result.addLast(new ECOPlannedInputs.PlannedInputBatch(stacks, count));
        }
        return result;
    }

    static class TaskProgress {
        long value = 0;
    }
}
