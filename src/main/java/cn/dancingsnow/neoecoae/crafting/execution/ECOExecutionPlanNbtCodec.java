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
        data.putLong("plannedOutputAmount", plan.signature().finalAmount());

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
        ExecutionMode mode = ExecutionMode.valueOf(data.getStringOr("mode", ""));
        ListTag taskTags = data.getListOrEmpty("tasks");
        List<ECOExecutionPlan.TaskSpec> tasks = new ArrayList<>(taskTags.size());
        Map<Integer, IPatternDetails> patternsById = new HashMap<>();
        for (int i = 0; i < taskTags.size(); i++) {
            CompoundTag taskTag = taskTags.getCompoundOrEmpty(i);
            int id = taskTag.getIntOr("id", 0);
            if (id != i) throw new IllegalArgumentException("Persisted execution task ids are not dense");
            AEItemKey definition = AEItemKey.fromTag(registries, taskTag.getCompoundOrEmpty("pattern"));
            IPatternDetails pattern = PatternDetailsHelper.decodePattern(definition, cpu.cpu.getLevel());
            if (pattern == null) throw new IllegalArgumentException("Persisted execution pattern cannot be decoded");
            PlanIdentity.PatternIdentity identity = PlanIdentity.patternIdentityFor(pattern);
            if (identity == null) throw new IllegalArgumentException("Persisted execution pattern has no identity");
            patternsById.put(id, pattern);

            List<PlannedInputAllocation> allocations = new ArrayList<>();
            ListTag allocationTags = taskTag.getListOrEmpty("inputAllocations");
            for (int allocationIndex = 0; allocationIndex < allocationTags.size(); allocationIndex++) {
                CompoundTag allocationTag = allocationTags.getCompoundOrEmpty(allocationIndex);
                List<PlannedInputAllocation.Run> runs = new ArrayList<>();
                ListTag runTags = allocationTag.getListOrEmpty("runs");
                for (int runIndex = 0; runIndex < runTags.size(); runIndex++) {
                    CompoundTag runTag = runTags.getCompoundOrEmpty(runIndex);
                    GenericStack stack = GenericStack.readTag(registries, runTag);
                    long crafts = runTag.getLongOr("crafts", 0L);
                    if (stack == null) throw new IllegalArgumentException("Invalid persisted input allocation");
                    runs.add(new PlannedInputAllocation.Run(stack.what(), stack.amount(), crafts));
                }
                allocations.add(new PlannedInputAllocation(allocationTag.getIntOr("slot", 0), runs));
            }
            tasks.add(new ECOExecutionPlan.TaskSpec(id, identity, pattern,
                ECOExecutionPlan.PatternRuntimeInfo.from(pattern), taskTag.getLong("total"),
                taskTag.getIntOr("phase", 0), ECOExecutionPlan.TaskKind.valueOf(taskTag.getStringOr("kind", "")), allocations));
        }

        ListTag phaseTags = data.getListOrEmpty("phases");
        List<ECOExecutionPlan.PhaseSpec> phases = new ArrayList<>(phaseTags.size());
        List<ECOExecutionSchedule.ComponentExecutionPhase> schedulePhases = new ArrayList<>(phaseTags.size());
        List<ECOExecutionSchedule.PhaseDependency> dependencies = new ArrayList<>();
        for (int i = 0; i < phaseTags.size(); i++) {
            CompoundTag phaseTag = phaseTags.getCompoundOrEmpty(i);
            int index = phaseTag.getIntOr("index", 0);
            if (index != i) throw new IllegalArgumentException("Persisted execution phase ids are not dense");
            ECOExecutionSchedule.Type type = ECOExecutionSchedule.Type.valueOf(phaseTag.getStringOr("type", ""));
            List<Integer> taskIds = Arrays.stream(phaseTag.getIntArray("tasks").orElseGet(() -> new int[0])).boxed().toList();
            List<Integer> phaseDependencies = Arrays.stream(phaseTag.getIntArray("dependencies").orElseGet(() -> new int[0])).boxed().toList();

            List<ECOExecutionPlan.ExecutionStep> steps = new ArrayList<>();
            ListTag stepTags = phaseTag.getListOrEmpty("steps");
            for (int stepIndex = 0; stepIndex < stepTags.size(); stepIndex++) {
                CompoundTag step = stepTags.getCompoundOrEmpty(stepIndex);
                steps.add(new ECOExecutionPlan.ExecutionStep(step.getIntOr("task", 0), step.getLongOr("count", 0L),
                    step.contains("repeatWidth") ? step.getInt("repeatWidth") : 1,
                    step.contains("repetitions") ? step.getLong("repetitions") : 1L));
            }

            Map<Integer, Long> dynamic = new LinkedHashMap<>();
            ListTag dynamicTags = phaseTag.getListOrEmpty("dynamic");
            for (int dynamicIndex = 0; dynamicIndex < dynamicTags.size(); dynamicIndex++) {
                CompoundTag firing = dynamicTags.getCompoundOrEmpty(dynamicIndex);
                dynamic.put(firing.getIntOr("task", 0), firing.getLongOr("count", 0L));
            }

            Map<AEKey, Long> seed = new LinkedHashMap<>();
            ListTag seedTags = phaseTag.getListOrEmpty("initialSeed");
            for (int seedIndex = 0; seedIndex < seedTags.size(); seedIndex++) {
                GenericStack stack = GenericStack.readTag(registries, seedTags.getCompoundOrEmpty(seedIndex));
                if (stack == null || stack.amount() <= 0L) {
                    throw new IllegalArgumentException("Invalid persisted seed");
                }
                seed.put(stack.what(), stack.amount());
            }

            phases.add(new ECOExecutionPlan.PhaseSpec(index, phaseTag.getIntOr("component", 0), type,
                taskIds, steps, phaseDependencies, dynamic, seed));

            LinkedHashSet<IPatternDetails> patternSet = new LinkedHashSet<>();
            for (int taskId : taskIds) {
                IPatternDetails pattern = patternsById.get(taskId);
                if (pattern == null) throw new IllegalArgumentException("Persisted phase references an unknown task");
                patternSet.add(pattern);
            }
            schedulePhases.add(new ECOExecutionSchedule.ComponentExecutionPhase(
                phaseTag.getIntOr("component", 0), type, patternSet, List.of()));
            for (int dependency : phaseDependencies) {
                dependencies.add(new ECOExecutionSchedule.PhaseDependency(dependency, index));
            }
        }

        Map<PlanIdentity.PatternIdentity, Long> signatureTasks = new LinkedHashMap<>();
        for (var task : tasks) signatureTasks.put(task.identity(), task.totalCount());
        if (finalOutput == null) throw new IllegalArgumentException("Persisted execution job has no final output");
        long plannedAmount = data.contains("plannedOutputAmount") ? data.getLongOr("plannedOutputAmount", 0L) : finalOutput.amount();
        if (plannedAmount <= 0 || plannedAmount > finalOutput.amount())
            throw new IllegalArgumentException("Invalid persisted remaining-plan output amount");
        PlanIdentity.Signature signature = new PlanIdentity.Signature(
            finalOutput.what(), plannedAmount, signatureTasks, Map.of(), Map.of(), Map.of());
        return new ECOExecutionPlan(signature, mode, tasks, phases,
            new ECOExecutionSchedule(schedulePhases, dependencies));
    }
}
