package cn.dancingsnow.neoecoae.crafting.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.core.io.DataReader;
import cn.dancingsnow.neoecoae.core.io.DataWriter;
import cn.dancingsnow.neoecoae.core.io.ExecutionPlanCodec;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlannedInputAllocation;
import cn.dancingsnow.neoecoae.platform.nbt.NbtData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** AE2 pattern/runtime binding for the version-independent execution-plan persistence structure. */
public final class ECOExecutionPlanNbtCodec {
    private ECOExecutionPlanNbtCodec() {}

    public static CompoundTag encode(ECOExecutionPlan plan, HolderLookup.Provider registries) {
        var tasks = plan.tasks().stream().map(task -> new ExecutionPlanCodec.Task<>(task.id(),
            new PatternBinding(task.pattern(), task.identity(), task.runtimeInfo()), task.totalCount(),
            task.phaseIndex(), task.kind().name(), task.inputAllocations().stream()
                .map(allocation -> new ExecutionPlanCodec.Allocation<>(allocation.slot(), allocation.runs().stream()
                    .map(run -> new ExecutionPlanCodec.Run<>(run.key(), run.amount(), run.crafts())).toList())).toList()))
            .toList();
        var phases = plan.phases().stream().map(phase -> new ExecutionPlanCodec.Phase<>(phase.index(),
            phase.componentId(), phase.type().name(), phase.taskIds(), phase.steps().stream()
                .map(step -> new ExecutionPlanCodec.Step(step.taskId(), step.count(), step.repeatWidth(), step.repetitions()))
                .toList(), phase.dependencies(), phase.dynamicFirings(), phase.initialSeed())).toList();
        CompoundTag data = new CompoundTag();
        ExecutionPlanCodec.write(new NbtData(data, registries),
            new ExecutionPlanCodec.Plan<>(plan.mode().name(), tasks, phases), new PatternPlatform(null));
        return data;
    }

    public static ECOExecutionPlan decode(CompoundTag data, HolderLookup.Provider registries,
            ECOCraftingCPULogic cpu, GenericStack finalOutput) {
        // Reject a missing/unknown mode before invoking the platform's pattern decoder, as in old saves.
        var reader = new NbtData(data, registries);
        ExecutionMode mode = ExecutionMode.valueOf(reader.readString("mode"));
        var saved = ExecutionPlanCodec.read(reader, new PatternPlatform(cpu));
        List<ECOExecutionPlan.TaskSpec> tasks = new ArrayList<>(saved.tasks().size());
        Map<Integer, IPatternDetails> patternsById = new HashMap<>();
        for (var task : saved.tasks()) {
            var pattern = task.pattern();
            patternsById.put(task.id(), pattern.pattern());
            var allocations = task.allocations().stream().map(allocation -> new PlannedInputAllocation(
                allocation.slot(), allocation.runs().stream()
                    .map(run -> new PlannedInputAllocation.Run(run.key(), run.amount(), run.crafts())).toList())).toList();
            tasks.add(new ECOExecutionPlan.TaskSpec(task.id(), pattern.identity(), pattern.pattern(), pattern.runtime(),
                task.total(), task.phase(), ECOExecutionPlan.TaskKind.valueOf(task.kind()), allocations));
        }
        List<ECOExecutionPlan.PhaseSpec> phases = new ArrayList<>(saved.phases().size());
        List<ECOExecutionSchedule.ComponentExecutionPhase> schedulePhases = new ArrayList<>(saved.phases().size());
        List<ECOExecutionSchedule.PhaseDependency> dependencies = new ArrayList<>();
        for (var phase : saved.phases()) {
            var type = ECOExecutionSchedule.Type.valueOf(phase.type());
            var steps = phase.steps().stream().map(step -> new ECOExecutionPlan.ExecutionStep(
                step.task(), step.count(), step.repeatWidth(), step.repetitions())).toList();
            phases.add(new ECOExecutionPlan.PhaseSpec(phase.index(), phase.component(), type,
                phase.tasks(), steps, phase.dependencies(), phase.dynamic(), phase.initialSeed()));
            LinkedHashSet<IPatternDetails> patternSet = new LinkedHashSet<>();
            for (int taskId : phase.tasks()) {
                IPatternDetails pattern = patternsById.get(taskId);
                if (pattern == null) throw new IllegalArgumentException("Persisted phase references an unknown task");
                patternSet.add(pattern);
            }
            schedulePhases.add(new ECOExecutionSchedule.ComponentExecutionPhase(
                phase.component(), type, patternSet, List.of()));
            for (int dependency : phase.dependencies()) {
                dependencies.add(new ECOExecutionSchedule.PhaseDependency(dependency, phase.index()));
            }
        }
        Map<PlanIdentity.PatternIdentity, Long> signatureTasks = new LinkedHashMap<>();
        for (var task : tasks) signatureTasks.put(task.identity(), task.totalCount());
        if (finalOutput == null) throw new IllegalArgumentException("Persisted execution job has no final output");
        PlanIdentity.Signature signature = new PlanIdentity.Signature(
            finalOutput.what(), finalOutput.amount(), signatureTasks, Map.of(), Map.of(), Map.of());
        return new ECOExecutionPlan(signature, mode, tasks, phases, new ECOExecutionSchedule(schedulePhases, dependencies));
    }

    private record PatternBinding(IPatternDetails pattern, PlanIdentity.PatternIdentity identity,
                                   ECOExecutionPlan.PatternRuntimeInfo runtime) {}

    private record PatternPlatform(ECOCraftingCPULogic cpu) implements ExecutionPlanCodec.Platform<PatternBinding, AEKey> {
        @Override public void writePattern(DataWriter<AEKey> task, PatternBinding binding) {
            var definition = binding.pattern().getDefinition();
            if (definition == null) {
                throw new IllegalStateException("Cannot persist an execution task without a pattern definition");
            }
            ((NbtData) task).writeItemKey("pattern", definition);
        }
        @Override public PatternBinding readPattern(DataReader<AEKey> task) {
            var definition = ((NbtData) task).readItemKey("pattern");
            IPatternDetails pattern = PatternDetailsHelper.decodePattern(definition, cpu.cpu.getLevel());
            if (pattern == null) throw new IllegalArgumentException("Persisted execution pattern cannot be decoded");
            var identity = PlanIdentity.patternIdentityFor(pattern);
            if (identity == null) throw new IllegalArgumentException("Persisted execution pattern has no identity");
            return new PatternBinding(pattern, identity, ECOExecutionPlan.PatternRuntimeInfo.from(pattern));
        }
        @Override public void writeStack(DataWriter<AEKey> entry, AEKey key, long amount) {
            ((NbtData) entry).writeStack(key, amount);
        }
        @Override public ExecutionPlanCodec.Stack<AEKey> readStack(DataReader<AEKey> entry) {
            var stack = ((NbtData) entry).readStack();
            return stack == null ? null : new ExecutionPlanCodec.Stack<>(stack.what(), stack.amount());
        }
    }
}
