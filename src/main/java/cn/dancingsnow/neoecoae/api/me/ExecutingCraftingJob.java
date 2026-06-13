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
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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

    final CraftingLink link;
    final ListCraftingInventory waitingFor;
    final Map<IPatternDetails, TaskProgress> tasks = new HashMap<>();
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
                var amount = output.amount() * entry.getValue() * output.what().getAmountPerUnit();
                timeTracker.addMaxItems(amount, output.what().getType());
            }
        }
        this.link = link;
        this.playerId = playerId;
        this.suspended = false;
    }

    ExecutingCraftingJob(ValueInput data,
            CraftingDifferenceListener postCraftingDifference, ECOCraftingCPULogic logic) {
        this.link = new CraftingLink(data.childOrEmpty(NBT_LINK), logic.cpu);
        IGrid grid = logic.cpu.getGrid();
        if (grid != null) {
            ((CraftingService) grid.getCraftingService()).addLink(link);
        }

        this.finalOutput = GenericStack.readTag(data.childOrEmpty(NBT_FINAL_OUTPUT));
        this.remainingAmount = data.getLongOr(NBT_REMAINING_AMOUNT, 0);
        this.waitingFor = new ListCraftingInventory(postCraftingDifference::onCraftingDifference);
        this.waitingFor.deserialize(data.childrenListOrEmpty(NBT_WAITING_FOR));
        this.timeTracker = new ElapsedTimeTracker(data.childOrEmpty(NBT_TIME_TRACKER));
        this.playerId = data.getInt(NBT_PLAYER_ID).orElse(null);

        for (var item : data.childrenListOrEmpty(NBT_TASKS)) {
            var pattern = AEItemKey.fromTag(item);
            if (pattern == null) {
                continue;
            }
            var details = PatternDetailsHelper.decodePattern(pattern, logic.cpu.getLevel());
            if (details != null) {
                final TaskProgress tp = new TaskProgress();
                tp.value = item.getLongOr(NBT_CRAFTING_PROGRESS, 0);
                this.tasks.put(details, tp);
            }
        }

        this.suspended = data.getBooleanOr(NBT_SUSPENDED, false);
    }

    CompoundTag writeToNBT(HolderLookup.Provider registries) {
        return NbtValueIO.write(registries, this::writeToNBT);
    }

    void writeToNBT(ValueOutput data) {
        link.writeToNBT(data.child(NBT_LINK));
        GenericStack.writeTag(data.child(NBT_FINAL_OUTPUT), finalOutput);
        waitingFor.serialize(data.childrenList(NBT_WAITING_FOR));
        timeTracker.writeToNBT(data.child(NBT_TIME_TRACKER));
        var tasks = data.childrenList(NBT_TASKS);
        for (var e : this.tasks.entrySet()) {
            var item = tasks.addChild();
            e.getKey().getDefinition().toTag(item);
            item.putLong(NBT_CRAFTING_PROGRESS, e.getValue().value);
        }

        data.putLong(NBT_REMAINING_AMOUNT, remainingAmount);
        if (this.playerId != null) {
            data.putInt(NBT_PLAYER_ID, this.playerId);
        }

        data.putBoolean(NBT_SUSPENDED, suspended);
    }

    static class TaskProgress {
        long value = 0;
    }
}
