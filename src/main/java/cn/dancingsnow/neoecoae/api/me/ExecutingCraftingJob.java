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

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOCompiledFastPathPattern;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathPatternMetadata;
import cn.dancingsnow.neoecoae.api.me.fastpath.ECOFastPathStacks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutingCraftingJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutingCraftingJob.class);

    private static final String NBT_LINK = "link";
    private static final String NBT_PLAYER_ID = "playerId";
    private static final String NBT_FINAL_OUTPUT = "finalOutput";
    private static final String NBT_WAITING_FOR = "waitingFor";
    private static final String NBT_IN_FLIGHT_OUTPUTS = "inFlightOutputs";
    private static final String NBT_TIME_TRACKER = "timeTracker";
    private static final String NBT_REMAINING_AMOUNT = "remainingAmount";
    private static final String NBT_TASKS = "tasks";
    private static final String NBT_CRAFTING_PROGRESS = "#craftingProgress";
    private static final String NBT_SUSPENDED = "suspended";
    private static final String NBT_USER_PAUSED = "userPaused";

    final CraftingLink link;
    final ListCraftingInventory waitingFor;
    final KeyCounter inFlightOutputs = new KeyCounter();
    final Map<IPatternDetails, TaskProgress> tasks = new LinkedHashMap<>();
    final Map<IPatternDetails, List<IPatternDetails>> dependencies = new IdentityHashMap<>();
    final Map<IPatternDetails, List<AEKey>> inputKeys = new IdentityHashMap<>();
    final Map<IPatternDetails, List<AEKey>> dependencyOutputKeys = new IdentityHashMap<>();
    final ElapsedTimeTracker timeTracker;
    GenericStack finalOutput;
    long remainingAmount;

    @Nullable Integer playerId;

    boolean suspended;
    boolean userPaused;

    @FunctionalInterface
    interface CraftingDifferenceListener {
        void onCraftingDifference(AEKey what);
    }

    ExecutingCraftingJob(
            ICraftingPlan plan,
            CraftingDifferenceListener postCraftingDifference,
            CraftingLink link,
            Level level,
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
        rebuildTaskOrderAndDependencies(level);
        this.link = link;
        this.playerId = playerId;
        this.suspended = false;
        this.userPaused = false;
    }

    ExecutingCraftingJob(
            CompoundTag data,
            HolderLookup.Provider registries,
            CraftingDifferenceListener postCraftingDifference,
            ECOCraftingCPULogic logic) {
        this.link = new CraftingLink(data.getCompound(NBT_LINK), logic.cpu);
        IGrid grid = logic.cpu.getGrid();
        if (grid != null) {
            ((CraftingService) grid.getCraftingService()).addLink(link);
        }

        this.finalOutput = GenericStack.readTag(data.getCompound(NBT_FINAL_OUTPUT));
        this.remainingAmount = data.getLong(NBT_REMAINING_AMOUNT);
        this.waitingFor = new ListCraftingInventory(postCraftingDifference::onCraftingDifference);
        this.waitingFor.readFromNBT(data.getList(NBT_WAITING_FOR, Tag.TAG_COMPOUND));
        readCounter(inFlightOutputs, data.getList(NBT_IN_FLIGHT_OUTPUTS, Tag.TAG_COMPOUND));
        this.timeTracker = new ElapsedTimeTracker(data.getCompound(NBT_TIME_TRACKER));
        if (data.contains(NBT_PLAYER_ID, Tag.TAG_INT)) {
            this.playerId = data.getInt(NBT_PLAYER_ID);
        } else {
            this.playerId = null;
        }

        ListTag tasksTag = data.getList(NBT_TASKS, Tag.TAG_COMPOUND);
        boolean missingTaskPattern = false;
        for (int i = 0; i < tasksTag.size(); ++i) {
            final CompoundTag item = tasksTag.getCompound(i);
            var pattern = AEItemKey.fromTag(item);
            var details = PatternDetailsHelper.decodePattern(pattern, logic.cpu.getLevel());
            if (details != null) {
                final TaskProgress tp = new TaskProgress();
                tp.value = item.getLong(NBT_CRAFTING_PROGRESS);
                this.tasks.put(details, tp);
            } else {
                missingTaskPattern = true;
                LOGGER.warn(
                        "Unable to decode saved ECO crafting task pattern; restoring job as suspended. jobId={}",
                        this.link.getCraftingID());
            }
        }
        rebuildTaskOrderAndDependencies(logic.cpu.getLevel());

        this.suspended = data.getBoolean(NBT_SUSPENDED) || missingTaskPattern;
        this.userPaused = data.getBoolean(NBT_USER_PAUSED);
    }

    CompoundTag writeToNBT(HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();

        CompoundTag linkData = new CompoundTag();
        link.writeToNBT(linkData);
        data.put(NBT_LINK, linkData);

        data.put(NBT_FINAL_OUTPUT, GenericStack.writeTag(finalOutput));

        data.put(NBT_WAITING_FOR, waitingFor.writeToNBT());
        data.put(NBT_IN_FLIGHT_OUTPUTS, writeCounter(inFlightOutputs));
        data.put(NBT_TIME_TRACKER, timeTracker.writeToNBT());

        final ListTag list = new ListTag();
        for (var e : this.tasks.entrySet()) {
            var item = e.getKey().getDefinition().toTag();
            item.putLong(NBT_CRAFTING_PROGRESS, e.getValue().value);
            list.add(item);
        }
        data.put(NBT_TASKS, list);

        data.putLong(NBT_REMAINING_AMOUNT, remainingAmount);
        if (this.playerId != null) {
            data.putInt(NBT_PLAYER_ID, this.playerId);
        }

        data.putBoolean(NBT_SUSPENDED, suspended);
        data.putBoolean(NBT_USER_PAUSED, userPaused);
        return data;
    }

    DispatchBlock getDispatchBlock(IPatternDetails details) {
        for (AEKey inputKey : inputKeys.getOrDefault(details, List.of())) {
            if (inFlightOutputs.get(inputKey) > 0) {
                return DispatchBlock.IN_FLIGHT_OUTPUT;
            }
        }
        for (AEKey outputKey : dependencyOutputKeys.getOrDefault(details, List.of())) {
            if (inFlightOutputs.get(outputKey) > 0) {
                return DispatchBlock.IN_FLIGHT_OUTPUT;
            }
        }

        for (IPatternDetails dependency : dependencies.getOrDefault(details, List.of())) {
            TaskProgress progress = tasks.get(dependency);
            if (progress != null && progress.value > 0) {
                return DispatchBlock.UNFINISHED_DEPENDENCY;
            }
        }

        return DispatchBlock.NONE;
    }

    void addInFlightOutputs(List<GenericStack> stacks, int multiplier) {
        int count = Math.max(1, multiplier);
        for (GenericStack stack : stacks) {
            inFlightOutputs.add(stack.what(), stack.amount() * count);
        }
    }

    void removeInFlightOutput(AEKey what, long amount) {
        long tracked = inFlightOutputs.get(what);
        if (tracked <= 0) {
            return;
        }
        inFlightOutputs.remove(what, Math.min(tracked, amount));
        inFlightOutputs.removeZeros();
    }

    private void rebuildTaskOrderAndDependencies(Level level) {
        dependencies.clear();
        inputKeys.clear();
        dependencyOutputKeys.clear();

        List<IPatternDetails> taskDetails = new ArrayList<>(tasks.keySet());
        taskDetails.sort((left, right) -> patternSortId(left).compareTo(patternSortId(right)));

        for (IPatternDetails task : taskDetails) {
            Set<AEKey> inputs = new LinkedHashSet<>();
            for (IPatternDetails.IInput input : task.getInputs()) {
                for (GenericStack possibleInput : input.getPossibleInputs()) {
                    if (possibleInput != null && possibleInput.amount() > 0) {
                        inputs.add(possibleInput.what());
                    }
                }
            }
            inputKeys.put(task, List.copyOf(inputs));

            Set<IPatternDetails> upstream = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<AEKey> upstreamOutputs = new LinkedHashSet<>();
            for (IPatternDetails producer : taskDetails) {
                if (producer == task) {
                    continue;
                }
                for (GenericStack output : producer.getOutputs()) {
                    if (canUseOutputAsInput(task, output.what(), level)) {
                        upstream.add(producer);
                        upstreamOutputs.add(output.what());
                        break;
                    }
                }
            }
            dependencyOutputKeys.put(task, List.copyOf(upstreamOutputs));

            List<IPatternDetails> orderedUpstream = new ArrayList<>(upstream);
            orderedUpstream.sort((left, right) -> patternSortId(left).compareTo(patternSortId(right)));
            dependencies.put(task, List.copyOf(orderedUpstream));
        }

        Map<IPatternDetails, Integer> depths = new IdentityHashMap<>();
        List<Map.Entry<IPatternDetails, TaskProgress>> orderedTasks = new ArrayList<>(tasks.entrySet());
        orderedTasks.sort((left, right) -> {
            int depthCompare = Integer.compare(
                    dependencyDepth(left.getKey(), depths, Collections.newSetFromMap(new IdentityHashMap<>())),
                    dependencyDepth(right.getKey(), depths, Collections.newSetFromMap(new IdentityHashMap<>())));
            if (depthCompare != 0) {
                return depthCompare;
            }
            return patternSortId(left.getKey()).compareTo(patternSortId(right.getKey()));
        });

        tasks.clear();
        for (var entry : orderedTasks) {
            tasks.put(entry.getKey(), entry.getValue());
        }
    }

    private static boolean canUseOutputAsInput(IPatternDetails consumer, AEKey outputKey, Level level) {
        for (IPatternDetails.IInput input : consumer.getInputs()) {
            try {
                if (input.isValid(outputKey, level)) {
                    return true;
                }
            } catch (RuntimeException e) {
                for (GenericStack possibleInput : input.getPossibleInputs()) {
                    if (possibleInput != null && outputKey.equals(possibleInput.what())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int dependencyDepth(
            IPatternDetails details, Map<IPatternDetails, Integer> depths, Set<IPatternDetails> visiting) {
        Integer cached = depths.get(details);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(details)) {
            return 0;
        }

        int depth = 0;
        for (IPatternDetails dependency : dependencies.getOrDefault(details, List.of())) {
            depth = Math.max(depth, 1 + dependencyDepth(dependency, depths, visiting));
        }
        visiting.remove(details);
        depths.put(details, depth);
        return depth;
    }

    private static String patternSortId(IPatternDetails details) {
        try {
            return ECOFastPathStacks.keySortId(details.getDefinition());
        } catch (RuntimeException e) {
            return details.getClass().getName() + ":" + System.identityHashCode(details);
        }
    }

    private static ListTag writeCounter(KeyCounter counter) {
        return ECOFastPathStacks.writeGenericStacks(ECOFastPathStacks.copyCounterUnsorted(counter));
    }

    private static void readCounter(KeyCounter counter, ListTag list) {
        ECOFastPathStacks.readGenericStacksInto(counter, list);
    }

    enum DispatchBlock {
        NONE,
        UNFINISHED_DEPENDENCY,
        IN_FLIGHT_OUTPUT
    }

    static class TaskProgress {
        long value = 0;

        @Nullable private ECOCompiledFastPathPattern compiledFastPathPattern;

        @Nullable private ECOFastPathPatternMetadata fastPathMetadata;

        ECOCompiledFastPathPattern getCompiledFastPathPattern(IPatternDetails details) {
            if (compiledFastPathPattern == null || !compiledFastPathPattern.isCurrent()) {
                compiledFastPathPattern = ECOCompiledFastPathPattern.compile(details);
                fastPathMetadata = null;
            }
            return compiledFastPathPattern;
        }

        ECOExtractedPatternExecution createPatternExecution(
                IPatternDetails details,
                KeyCounter[] craftingContainer,
                KeyCounter expectedContainerItems,
                Level level) {
            ECOCompiledFastPathPattern compiledPattern = getCompiledFastPathPattern(details);
            List<GenericStack> containers = ECOFastPathStacks.copyCounter(expectedContainerItems);
            boolean canBuildFastPath =
                    ECOFastPathEligibility.isGloballyEnabled() && compiledPattern.canBuildFastPath(containers);
            ECOFastPathPatternMetadata metadata =
                    canBuildFastPath ? getFastPathMetadata(compiledPattern, craftingContainer, level) : null;
            return ECOExtractedPatternExecution.create(
                    details, compiledPattern, metadata, craftingContainer, containers, canBuildFastPath, level);
        }

        @Nullable private ECOFastPathPatternMetadata getFastPathMetadata(
                ECOCompiledFastPathPattern compiledPattern, KeyCounter[] craftingContainer, Level level) {
            if (!compiledPattern.canCacheFastPathInputs()) {
                fastPathMetadata = null;
                return null;
            }
            if (fastPathMetadata == null || !fastPathMetadata.isCurrent(compiledPattern, level)) {
                fastPathMetadata = ECOFastPathPatternMetadata.create(compiledPattern, craftingContainer, level);
            }
            return fastPathMetadata;
        }
    }
}
