package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.impl.crafting.planner.provenance.ExecutionProvenance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The only boundary that interprets planner components as an executable contract. */
public final class ECOExecutionPlanBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOExecutionPlanBuilder.class);
    private ECOExecutionPlanBuilder() { }

    public static ECOExecutionPlan build(PlanIdentity.Signature signature, ExecutionMode mode,
            List<ComponentPlanningResult> components, List<Integer> executionOrder,
            Map<IPatternDetails, Long> patternTimes) {
        return build(signature, mode, components, executionOrder, patternTimes, null);
    }

    public static ECOExecutionPlan build(PlanIdentity.Signature signature, ExecutionMode mode,
            List<ComponentPlanningResult> components, List<Integer> executionOrder,
            Map<IPatternDetails, Long> patternTimes, ExecutionProvenance provenance) {
        try {
            return buildValidated(signature, mode, components, executionOrder, patternTimes, provenance);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw failure;
        }
    }

    private static ECOExecutionPlan buildValidated(PlanIdentity.Signature signature, ExecutionMode mode,
            List<ComponentPlanningResult> components, List<Integer> executionOrder,
            Map<IPatternDetails, Long> patternTimes, ExecutionProvenance provenance) {
        validateCycleDispositions(signature, components);
        ECOExecutionSchedule schedule = ECOExecutionSchedule.from(components, executionOrder, patternTimes, provenance);
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
                : phase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE
                    ? ECOExecutionPlan.TaskKind.CYCLE_DYNAMIC
                    : ordered ? ECOExecutionPlan.TaskKind.CYCLE_ORDERED : ECOExecutionPlan.TaskKind.CYCLE_REMAINDER;
            int id = tasks.size();
            taskIdByIdentity.put(task.identity(), id);
            tasks.add(new ECOExecutionPlan.TaskSpec(id, task.identity(), task.pattern(),
                ECOExecutionPlan.PatternRuntimeInfo.from(task.pattern()), task.count(), phaseIndex, kind));
        }

        List<ECOExecutionPlan.PhaseSpec> phases = new ArrayList<>();
        for (int phaseIndex = 0; phaseIndex < schedule.phases().size(); phaseIndex++) {
            int currentPhaseIndex = phaseIndex;
            var schedulePhase = schedule.phases().get(phaseIndex);
            List<Integer> taskIds = schedulePhase.patternSet().stream()
                .map(ECOExecutionPlanBuilder::requireIdentity).map(taskIdByIdentity::get)
                .sorted().toList();
            List<ECOExecutionPlan.ExecutionStep> steps = new ArrayList<>();
            Map<Integer, Long> dynamicFirings = new LinkedHashMap<>();
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
            if (schedulePhase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE && component != null
                    && component.cycleResult() != null) {
                for (var firing : component.cycleResult().patternTimes().entrySet()) {
                    if (firing.getValue() == null || firing.getValue() <= 0L) continue;
                    Integer taskId = taskIdByIdentity.get(requireIdentity(firing.getKey()));
                    if (taskId == null || !taskIds.contains(taskId)) {
                        throw new IllegalStateException("Dynamic firing vector references a task outside its phase");
                    }
                    dynamicFirings.merge(taskId, firing.getValue(), Math::addExact);
                }
                validateDynamicCycleCounts(component, dynamicFirings, tasks);
            }
            phases.add(new ECOExecutionPlan.PhaseSpec(phaseIndex, schedulePhase.componentId(),
                schedulePhase.type(), taskIds, steps, schedule.dependencies().stream()
                    .filter(edge -> edge.consumerPhase() == currentPhaseIndex)
                    .map(ECOExecutionSchedule.PhaseDependency::producerPhase).sorted().toList(),
                dynamicFirings, schedulePhase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE
                    && component != null && component.cycleResult() != null
                        ? component.cycleResult().requiredSeed() : Map.of()));
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

    private static void validateCycleDispositions(PlanIdentity.Signature signature,
            List<ComponentPlanningResult> components) {
        Map<appeng.api.stacks.AEKey, Long> projectedReservations = new HashMap<>();
        for (ComponentPlanningResult component : components) {
            if (component.type() != ComponentPlanningResult.Type.CYCLIC) continue;
            switch (component.cycleDisposition()) {
                case NOT_REQUIRED -> {
                    if (hasPositiveFirings(component) || !component.stockReservations().isEmpty()) {
                        throw componentFailure(component,
                            "NOT_REQUIRED component has positive firings or stock reservations");
                    }
                }
                case STOCK_SATISFIED -> validateStockSatisfied(signature, component);
                case ORDERED_EXECUTION -> validateOrdered(component);
                case DYNAMIC_EXECUTION -> validateDynamic(component);
                case BLOCKED -> throw componentFailure(component,
                    "Planner marked cyclic component BLOCKED: " + component.diagnostic());
            }
            for (var reservation : component.stockReservations().entrySet()) {
                long planned = reservation.getValue() == null ? -1L : reservation.getValue();
                long finalUsed = signature.usedItems().getOrDefault(reservation.getKey(), 0L);
                if (planned <= 0L || finalUsed < planned) {
                    throw componentFailure(component, "Stock reservation is absent from final usedItems: key="
                        + reservation.getKey() + " stockReserved=" + planned + " finalUsedItems=" + finalUsed);
                }
                projectedReservations.merge(reservation.getKey(), planned, Math::addExact);
            }
        }
        projectedReservations.forEach((key, projected) -> {
            long finalUsed = signature.usedItems().getOrDefault(key, 0L);
            if (projected > finalUsed) {
                throw new IllegalStateException("Cycle stock reservation projections exceed final usedItems: key="
                    + key + " projected=" + projected + " finalUsedItems=" + finalUsed);
            }
        });
    }

    private static void validateStockSatisfied(PlanIdentity.Signature signature,
            ComponentPlanningResult component) {
        if (component.requiredOutputs().isEmpty()) {
            throw componentFailure(component, "STOCK_SATISFIED component has no positive demand");
        }
        if (component.cycleResult() == null || !component.cycleResult().status().solved()
                || hasPositiveFirings(component)) {
            throw componentFailure(component, "STOCK_SATISFIED requires a solved zero-firing cycle result");
        }
        for (var requirement : component.requiredOutputs().entrySet()) {
            long required = requirement.getValue() == null ? -1L : requirement.getValue();
            long stockReserved = component.stockReservations().getOrDefault(requirement.getKey(), 0L);
            long finalUsed = signature.usedItems().getOrDefault(requirement.getKey(), 0L);
            if (required <= 0L || stockReserved < required || finalUsed < stockReserved) {
                throw componentFailure(component, "STOCK_SATISFIED accounting mismatch: key="
                    + requirement.getKey() + " required=" + required + " stockReserved=" + stockReserved
                    + " finalUsedItems=" + finalUsed);
            }
        }
    }

    private static void validateOrdered(ComponentPlanningResult component) {
        if (component.cycleResult() == null || !component.cycleResult().status().solved()
                || !hasPositiveFirings(component) || component.cycleResult().executionPlan().isEmpty()) {
            throw componentFailure(component,
                "ORDERED_EXECUTION requires solved positive firings and compact execution metadata");
        }
    }

    private static void validateDynamic(ComponentPlanningResult component) {
        if (component.cycleResult() == null || !component.cycleResult().status().solved()
                || !component.cycleResult().hasExactExecutionCounts() || !hasPositiveFirings(component)) {
            throw componentFailure(component,
                "DYNAMIC_EXECUTION requires solved positive exact firing metadata");
        }
    }

    private static void validateDynamicCycleCounts(ComponentPlanningResult component,
            Map<Integer, Long> firings, List<ECOExecutionPlan.TaskSpec> tasks) {
        Map<PlanIdentity.PatternIdentity, Long> expected = PlanIdentity.taskSignature(
            component.cycleResult().patternTimes());
        if (expected == null) throw new IllegalStateException("Dynamic cycle firing vector has no stable identity");
        Map<PlanIdentity.PatternIdentity, Long> actual = new HashMap<>();
        firings.forEach((taskId, count) -> actual.merge(tasks.get(taskId).identity(), count, Math::addExact));
        expected = expected.entrySet().stream().filter(entry -> entry.getValue() > 0L).collect(
            java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!actual.equals(expected)) {
            throw new IllegalStateException("Dynamic cycle firing vector changed at the execution boundary");
        }
        firings.forEach((taskId, count) -> {
            if (count > tasks.get(taskId).totalCount()) {
                throw new IllegalStateException("Dynamic cycle firing count exceeds aggregate AE2 task count");
            }
        });
    }

    private static boolean hasPositiveFirings(ComponentPlanningResult component) {
        return component.cycleResult() != null && component.cycleResult().patternTimes().values().stream()
            .anyMatch(count -> count != null && count > 0L);
    }

    private static IllegalStateException componentFailure(ComponentPlanningResult component, String detail) {
        return new IllegalStateException("cycleComponent=" + component.componentId() + " disposition="
            + component.cycleDisposition() + " requiredOutputs=" + component.requiredOutputs().size()
            + " stockReservations=" + component.stockReservations().size() + " detail=" + detail);
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
