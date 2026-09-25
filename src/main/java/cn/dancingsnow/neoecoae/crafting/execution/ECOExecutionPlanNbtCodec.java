package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlannedInputAllocation;

/** NBT codec for the validated planner-to-CPU execution contract. */
public final class ECOExecutionPlanNbtCodec {
    private ECOExecutionPlanNbtCodec() {}

    public static CompoundTag encode(ECOExecutionPlan plan, HolderLookup.Provider registries) {
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

            ListTag allocations = new ListTag();
            for (var allocation : task.inputAllocations()) {
                CompoundTag allocationTag = new CompoundTag();
                allocationTag.putInt("slot", allocation.slot());
                ListTag runs = new ListTag();
                for (var run : allocation.runs()) {
                    CompoundTag runTag = GenericStack.writeTag(registries,
                        new GenericStack(run.key(), run.amount()));
                    runTag.putLong("crafts", run.crafts());
                    runs.add(runTag);
                }
                allocationTag.put("runs", runs);
                allocations.add(allocationTag);
            }
            taskTag.put("inputAllocations", allocations);
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
                if (step.repetitions() > 1L) {
                    stepTag.putInt("repeatWidth", step.repeatWidth());
                    stepTag.putLong("repetitions", step.repetitions());
                }
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

    public static ECOExecutionPlan decode(CompoundTag data, HolderLookup.Provider registries,
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

            List<PlannedInputAllocation> allocations = new ArrayList<>();
            ListTag allocationTags = taskTag.getList("inputAllocations", Tag.TAG_COMPOUND);
            for (int allocationIndex = 0; allocationIndex < allocationTags.size(); allocationIndex++) {
                CompoundTag allocationTag = allocationTags.getCompound(allocationIndex);
                List<PlannedInputAllocation.Run> runs = new ArrayList<>();
                ListTag runTags = allocationTag.getList("runs", Tag.TAG_COMPOUND);
                for (int runIndex = 0; runIndex < runTags.size(); runIndex++) {
                    CompoundTag runTag = runTags.getCompound(runIndex);
                    GenericStack stack = GenericStack.readTag(registries, runTag);
                    long crafts = runTag.getLong("crafts");
                    if (stack == null) throw new IllegalArgumentException("Invalid persisted input allocation");
                    runs.add(new PlannedInputAllocation.Run(stack.what(), stack.amount(), crafts));
                }
                allocations.add(new PlannedInputAllocation(allocationTag.getInt("slot"), runs));
            }
            tasks.add(new ECOExecutionPlan.TaskSpec(id, identity, pattern,
                ECOExecutionPlan.PatternRuntimeInfo.from(pattern), taskTag.getLong("total"),
                taskTag.getInt("phase"), ECOExecutionPlan.TaskKind.valueOf(taskTag.getString("kind")), allocations));
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
                steps.add(new ECOExecutionPlan.ExecutionStep(step.getInt("task"), step.getLong("count"),
                    step.contains("repeatWidth") ? step.getInt("repeatWidth") : 1,
                    step.contains("repetitions") ? step.getLong("repetitions") : 1L));
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
                if (stack == null || stack.amount() <= 0L) {
                    throw new IllegalArgumentException("Invalid persisted seed");
                }
                seed.put(stack.what(), stack.amount());
            }

            phases.add(new ECOExecutionPlan.PhaseSpec(index, phaseTag.getInt("component"), type,
                taskIds, steps, phaseDependencies, dynamic, seed));

            LinkedHashSet<IPatternDetails> patternSet = new LinkedHashSet<>();
            for (int taskId : taskIds) {
                IPatternDetails pattern = patternsById.get(taskId);
                if (pattern == null) throw new IllegalArgumentException("Persisted phase references an unknown task");
                patternSet.add(pattern);
            }
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
}
