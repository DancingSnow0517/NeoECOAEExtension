package cn.dancingsnow.neoecoae.crafting.execution;

import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;

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
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

/** NBT codec for the validated planner-to-CPU execution contract. */
public final class ECOExecutionPlanNbtCodec {
    private ECOExecutionPlanNbtCodec() {}

    static void writeAmount(CompoundTag data, String key, PlannerAmount amount) {
        if (amount.fitsLong()) data.putLong(key, amount.longValueExact());
        else data.putString(key, amount.toString());
    }

    static PlannerAmount readAmount(CompoundTag data, String key) {
        return data.get(key) instanceof net.minecraft.nbt.StringTag ? PlannerAmount.parse(data.getStringOr(key, "0"))
            : PlannerAmount.of(data.getLongOr(key, 0L));
    }

    static void writeAmounts(CompoundTag data, String key, PlannerAmount[] amounts) {
        if (Arrays.stream(amounts).allMatch(PlannerAmount::fitsLong)) {
            data.putLongArray(key, Arrays.stream(amounts).mapToLong(PlannerAmount::longValueExact).toArray());
        } else {
            ListTag values = new ListTag();
            for (PlannerAmount amount : amounts) values.add(net.minecraft.nbt.StringTag.valueOf(amount.toString()));
            data.put(key, values);
        }
    }

    static PlannerAmount[] readAmounts(CompoundTag data, String key) {
        if (data.get(key) instanceof net.minecraft.nbt.LongArrayTag) return Arrays.stream(data.getLongArray(key).orElseThrow())
            .mapToObj(PlannerAmount::of).toArray(PlannerAmount[]::new);
        ListTag values = data.getListOrEmpty(key);
        PlannerAmount[] result = new PlannerAmount[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = PlannerAmount.parse(values.getStringOr(i, "0"));
        return result;
    }

    static CompoundTag writeStack(HolderLookup.Provider registries, AEKey key, PlannerAmount amount) {
        CompoundTag data = cn.dancingsnow.neoecoae.crafting.ECOLegacyAENbt.writeStack(registries, new GenericStack(key, amount.longValueSaturated()));
        if (!amount.fitsLong()) data.putString("ecoExactAmount", amount.toString());
        return data;
    }

    static PlannerAmount readStackAmount(CompoundTag data, GenericStack projection) {
        return data.contains("ecoExactAmount") ? PlannerAmount.parse(data.getStringOr("ecoExactAmount", ""))
            : PlannerAmount.of(projection.amount());
    }

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
            writeAmount(taskTag, "total", task.plannerTotalCount());
            taskTag.putInt("phase", task.phaseIndex());
            taskTag.putString("kind", task.kind().name());
            taskTag.put("pattern", cn.dancingsnow.neoecoae.crafting.ECOLegacyAENbt.writeItemKey(registries, task.pattern().getDefinition()));

            ListTag allocations = new ListTag();
            for (var allocation : task.inputAllocations()) {
                CompoundTag allocationTag = new CompoundTag();
                allocationTag.putInt("slot", allocation.slot());
                ListTag runs = new ListTag();
                for (var run : allocation.runs()) {
                    CompoundTag runTag = writeStack(registries, run.key(), run.plannerAmount());
                    writeAmount(runTag, "crafts", run.plannerCrafts());
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
                writeAmount(stepTag, "count", step.plannerCount());
                if (step.plannerRepetitions().compareTo(PlannerAmount.ONE) > 0) {
                    stepTag.putInt("repeatWidth", step.repeatWidth());
                    writeAmount(stepTag, "repetitions", step.plannerRepetitions());
                }
                steps.add(stepTag);
            }
            phaseTag.put("steps", steps);

            ListTag dynamic = new ListTag();
            for (var entry : phase.plannerDynamicFirings().entrySet()) {
                CompoundTag firing = new CompoundTag();
                firing.putInt("task", entry.getKey());
                writeAmount(firing, "count", entry.getValue());
                dynamic.add(firing);
            }
            phaseTag.put("dynamic", dynamic);

            ListTag seeds = new ListTag();
            for (var entry : phase.plannerInitialSeed().entrySet()) {
                seeds.add(writeStack(registries, entry.getKey(), entry.getValue()));
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
            AEItemKey definition = ECOKeyFactory.itemFromTag(registries, taskTag.getCompoundOrEmpty("pattern"));
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
                    GenericStack stack = ECOKeyFactory.readTag(registries, runTag);
                    PlannerAmount crafts = readAmount(runTag, "crafts");
                    if (stack == null) throw new IllegalArgumentException("Invalid persisted input allocation");
                    runs.add(new PlannedInputAllocation.Run(stack.what(), readStackAmount(runTag, stack), crafts));
                }
                allocations.add(new PlannedInputAllocation(allocationTag.getIntOr("slot", 0), runs));
            }
            tasks.add(new ECOExecutionPlan.TaskSpec(id, identity, pattern,
                ECOExecutionPlan.PatternRuntimeInfo.from(pattern), readAmount(taskTag, "total"),
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
                steps.add(new ECOExecutionPlan.ExecutionStep(step.getIntOr("task", 0), readAmount(step, "count"),
                    step.contains("repeatWidth") ? step.getIntOr("repeatWidth", 0) : 1,
                    step.contains("repetitions") ? readAmount(step, "repetitions") : PlannerAmount.ONE));
            }

            Map<Integer, PlannerAmount> dynamic = new LinkedHashMap<>();
            ListTag dynamicTags = phaseTag.getListOrEmpty("dynamic");
            for (int dynamicIndex = 0; dynamicIndex < dynamicTags.size(); dynamicIndex++) {
                CompoundTag firing = dynamicTags.getCompoundOrEmpty(dynamicIndex);
                dynamic.put(firing.getIntOr("task", 0), readAmount(firing, "count"));
            }

            Map<AEKey, PlannerAmount> seed = new LinkedHashMap<>();
            ListTag seedTags = phaseTag.getListOrEmpty("initialSeed");
            for (int seedIndex = 0; seedIndex < seedTags.size(); seedIndex++) {
                CompoundTag seedTag = seedTags.getCompoundOrEmpty(seedIndex);
                GenericStack stack = ECOKeyFactory.readTag(registries, seedTag);
                if (stack == null || stack.amount() <= 0L) {
                    throw new IllegalArgumentException("Invalid persisted seed");
                }
                seed.put(stack.what(), readStackAmount(seedTag, stack));
            }

            phases.add(new ECOExecutionPlan.PhaseSpec(dynamic, seed, index, phaseTag.getIntOr("component", 0), type,
                taskIds, steps, phaseDependencies));

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
