package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The only boundary that interprets planner components as an executable contract. */
public final class ECOExecutionPlanBuilder {
    private ECOExecutionPlanBuilder() { }

    public static ECOExecutionPlan build(PlanIdentity.Signature signature, ExecutionMode mode,
            List<ComponentPlanningResult> components, List<Integer> executionOrder,
            Map<IPatternDetails, Long> patternTimes) {
        validateStockSatisfiedCycles(signature, components);
        ECOExecutionSchedule schedule = ECOExecutionSchedule.from(components, executionOrder, patternTimes);
        Map<Integer, ComponentPlanningResult> componentById = new HashMap<>();
        components.forEach(component -> componentById.put(component.componentId(), component));

        List<PlannedTask> selected = new ArrayList<>();
        for (var entry : patternTimes.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;
            var identity = requireIdentity(entry.getKey());
            PlannedTask existing = selected.stream().filter(task -> task.identity().equals(identity)).findFirst().orElse(null);
            if (existing == null) selected.add(new PlannedTask(identity, entry.getKey(), entry.getValue()));
            else existing.add(entry.getValue());
        }
        selected.sort(Comparator.comparing(task -> stableIdentity(task.identity())));

        Map<PlanIdentity.PatternIdentity, Integer> phaseByIdentity = new LinkedHashMap<>();
        for (int phaseIndex = 0; phaseIndex < schedule.phases().size(); phaseIndex++) {
            for (IPatternDetails pattern : schedule.phases().get(phaseIndex).patternSet()) {
                var identity = requireIdentity(pattern);
                if (phaseByIdentity.putIfAbsent(identity, phaseIndex) != null) {
                    throw new IllegalStateException("A physical pattern is owned by multiple phases: " + identity);
                }
            }
        }
        if (phaseByIdentity.size() != selected.size()) {
            throw new IllegalStateException("Execution phases do not cover the complete positive task vector");
        }

        List<ECOExecutionPlan.TaskSpec> tasks = new ArrayList<>();
        Map<PlanIdentity.PatternIdentity, Integer> taskIdByIdentity = new HashMap<>();
        for (PlannedTask task : selected) {
            Integer phaseIndex = phaseByIdentity.get(task.identity());
            if (phaseIndex == null) throw new IllegalStateException("A positive task has no phase owner");
            var phase = schedule.phases().get(phaseIndex);
            var component = componentById.get(phase.componentId());
            boolean ordered = phase.type() == ECOExecutionSchedule.Type.CYCLE
                && component != null && component.cycleResult() != null
                && !component.cycleResult().executionPlan().isEmpty();
            var kind = phase.type() == ECOExecutionSchedule.Type.DAG ? ECOExecutionPlan.TaskKind.DAG
                : ordered ? ECOExecutionPlan.TaskKind.CYCLE_ORDERED : ECOExecutionPlan.TaskKind.CYCLE_REMAINDER;
            int id = tasks.size();
            taskIdByIdentity.put(task.identity(), id);
            tasks.add(new ECOExecutionPlan.TaskSpec(id, task.identity(), task.pattern(),
                ECOExecutionPlan.PatternRuntimeInfo.from(task.pattern()), task.count(), phaseIndex, kind));
        }

        List<ECOExecutionPlan.PhaseSpec> phases = new ArrayList<>();
        for (int phaseIndex = 0; phaseIndex < schedule.phases().size(); phaseIndex++) {
            var schedulePhase = schedule.phases().get(phaseIndex);
            List<Integer> taskIds = schedulePhase.patternSet().stream()
                .map(ECOExecutionPlanBuilder::requireIdentity).map(taskIdByIdentity::get)
                .sorted().toList();
            List<ECOExecutionPlan.ExecutionStep> steps = new ArrayList<>();
            ComponentPlanningResult component = componentById.get(schedulePhase.componentId());
            if (schedulePhase.type() == ECOExecutionSchedule.Type.CYCLE && component != null
                    && component.cycleResult() != null) {
                for (PatternRun run : component.cycleResult().executionPlan()) {
                    if (run.count() <= 0) continue;
                    Integer taskId = taskIdByIdentity.get(requireIdentity(run.details()));
                    if (taskId == null || !taskIds.contains(taskId)) {
                        throw new IllegalStateException("Compact cycle trace references a task outside its phase");
                    }
                    steps.add(new ECOExecutionPlan.ExecutionStep(taskId, run.count()));
                }
                validateCycleCounts(component, steps, tasks);
            }
            phases.add(new ECOExecutionPlan.PhaseSpec(phaseIndex, schedulePhase.componentId(),
                schedulePhase.type(), taskIds, steps));
        }
        return new ECOExecutionPlan(signature, mode, tasks, phases, schedule);
    }

    private static void validateCycleCounts(ComponentPlanningResult component,
            List<ECOExecutionPlan.ExecutionStep> steps, List<ECOExecutionPlan.TaskSpec> tasks) {
        Map<PlanIdentity.PatternIdentity, Long> signature = PlanIdentity.taskSignature(
            component.cycleResult().patternTimes());
        if (signature == null) throw new IllegalStateException("Cycle firing vector has no stable identity");
        Map<PlanIdentity.PatternIdentity, Long> expected = new HashMap<>();
        signature.forEach((identity, count) -> { if (count != null && count > 0) expected.put(identity, count); });
        Map<PlanIdentity.PatternIdentity, Long> actual = new HashMap<>();
        for (var step : steps) actual.merge(tasks.get(step.taskId()).identity(), step.count(), Math::addExact);
        if (!actual.equals(expected)) {
            throw new IllegalStateException("Compact cycle trace does not equal the solved firing vector");
        }
        for (var entry : actual.entrySet()) {
            var task = tasks.stream().filter(candidate -> candidate.identity().equals(entry.getKey())).findFirst().orElseThrow();
            if (entry.getValue() > task.totalCount()) {
                throw new IllegalStateException("Cycle trace consumes more than the aggregate AE2 task count");
            }
        }
    }

    private static void validateStockSatisfiedCycles(PlanIdentity.Signature signature,
            List<ComponentPlanningResult> components) {
        for (ComponentPlanningResult component : components) {
            if (component.type() != ComponentPlanningResult.Type.CYCLIC || component.cycleResult() == null
                    || !component.cycleResult().status().solved()) continue;
            boolean hasFirings = component.cycleResult().patternTimes().values().stream()
                .anyMatch(count -> count != null && count > 0);
            if (hasFirings) continue;
            for (var requirement : component.requiredOutputs().entrySet()) {
                if (requirement.getValue() == null || requirement.getValue() <= 0) continue;
                long reserved = signature.usedItems().getOrDefault(requirement.getKey(), 0L);
                if (reserved < requirement.getValue()) {
                    throw new IllegalStateException("Stock-satisfied cycle consumption is absent from usedItems");
                }
            }
        }
    }

    private static PlanIdentity.PatternIdentity requireIdentity(IPatternDetails pattern) {
        var identity = PlanIdentity.patternIdentityFor(pattern);
        if (identity == null) throw new IllegalStateException("Execution pattern has no identity");
        return identity;
    }

    private static String stableIdentity(PlanIdentity.PatternIdentity identity) {
        return identity.kind().name() + ':' + identity.value();
    }

    private static final class PlannedTask {
        private final PlanIdentity.PatternIdentity identity;
        private final IPatternDetails pattern;
        private long count;
        private PlannedTask(PlanIdentity.PatternIdentity identity, IPatternDetails pattern, long count) {
            this.identity = identity; this.pattern = pattern; this.count = count;
        }
        PlanIdentity.PatternIdentity identity() { return identity; }
        IPatternDetails pattern() { return pattern; }
        long count() { return count; }
        void add(long amount) { count = Math.addExact(count, amount); }
    }
}
