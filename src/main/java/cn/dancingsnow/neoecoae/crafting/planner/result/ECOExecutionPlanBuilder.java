package cn.dancingsnow.neoecoae.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.RepeatLayout;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.ExecutionProvenance;
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
        Map<IPatternDetails, PlannerAmount> exact = new LinkedHashMap<>();
        patternTimes.forEach((pattern, count) -> exact.put(pattern, PlannerAmount.of(count)));
        Map<appeng.api.stacks.AEKey, PlannerAmount> used = new LinkedHashMap<>();
        signature.usedItems().forEach((key, count) -> used.put(key, PlannerAmount.of(count)));
        return buildExact(signature, mode, components, executionOrder, exact, used, provenance);
    }

    public static ECOExecutionPlan buildExact(PlanIdentity.Signature signature, ExecutionMode mode,
            List<ComponentPlanningResult> components, List<Integer> executionOrder,
            Map<IPatternDetails, PlannerAmount> patternTimes, Map<appeng.api.stacks.AEKey, PlannerAmount> used,
            ExecutionProvenance provenance) {
        validateCycleDispositions(used, components);
        // The schedule uses only positive task membership; all quantity accounting below stays exact.
        Map<IPatternDetails, Long> membership = new LinkedHashMap<>();
        patternTimes.forEach((pattern, count) -> { if (count.signum() > 0) membership.put(pattern, 1L); });
        ECOExecutionSchedule schedule = ECOExecutionSchedule.from(components, executionOrder, membership, provenance);
        Map<Integer, ComponentPlanningResult> componentById = new HashMap<>();
        components.forEach(component -> componentById.put(component.componentId(), component));

        Map<PlanIdentity.PatternIdentity, PlannedTask> selectedByIdentity = new LinkedHashMap<>();
        for (var entry : patternTimes.entrySet()) {
            if (entry.getValue() == null || entry.getValue().signum() <= 0) continue;
            var identity = requireIdentity(entry.getKey());
            PlannedTask existing = selectedByIdentity.get(identity);
            if (existing == null) selectedByIdentity.put(identity,
                new PlannedTask(identity, entry.getKey(), entry.getValue()));
            else existing.add(entry.getValue());
        }
        List<PlannedTask> selected = new ArrayList<>(selectedByIdentity.values());
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
                ECOExecutionPlan.PatternRuntimeInfo.from(task.pattern()), task.count(), phaseIndex, kind,
                defaultAllocations(task.pattern(), task.count())));
        }

        List<ECOExecutionPlan.PhaseSpec> phases = new ArrayList<>();
        for (int phaseIndex = 0; phaseIndex < schedule.phases().size(); phaseIndex++) {
            int currentPhaseIndex = phaseIndex;
            var schedulePhase = schedule.phases().get(phaseIndex);
            List<Integer> taskIds = schedulePhase.patternSet().stream()
                .map(ECOExecutionPlanBuilder::requireIdentity).map(taskIdByIdentity::get)
                .sorted().toList();
            List<ECOExecutionPlan.ExecutionStep> steps = new ArrayList<>();
            Map<Integer, PlannerAmount> dynamicFirings = new LinkedHashMap<>();
            ComponentPlanningResult component = componentById.get(schedulePhase.componentId());
            if (schedulePhase.type() == ECOExecutionSchedule.Type.CYCLE && component != null
                    && component.cycleResult() != null) {
                for (PatternRun run : component.cycleResult().executionPlan()) {
                    if (run.plannerCount().signum() <= 0) continue;
                    Integer taskId = taskIdByIdentity.get(requireIdentity(run.details()));
                    if (taskId == null || !taskIds.contains(taskId)) {
                        throw new IllegalStateException("Compact cycle trace references a task outside its phase");
                    }
                    steps.add(new ECOExecutionPlan.ExecutionStep(taskId, run.plannerCount(), run.repeatWidth(), run.plannerRepetitions()));
                }
                validateCycleCounts(component, steps, tasks, taskIdByIdentity);
            }
            if (schedulePhase.type() == ECOExecutionSchedule.Type.DYNAMIC_CYCLE && component != null
                    && component.cycleResult() != null) {
                for (var firing : component.cycleResult().exactPatternTimes().entrySet()) {
                    if (firing.getValue() == null || firing.getValue().signum() <= 0) continue;
                    Integer taskId = taskIdByIdentity.get(requireIdentity(firing.getKey()));
                    if (taskId == null || !taskIds.contains(taskId)) {
                        throw new IllegalStateException("Dynamic firing vector references a task outside its phase");
                    }
                    dynamicFirings.merge(taskId, firing.getValue(), PlannerAmount::add);
                }
                validateDynamicCycleCounts(component, dynamicFirings, tasks);
            }
            phases.add(new ECOExecutionPlan.PhaseSpec(dynamicFirings,
                schedulePhase.type() != ECOExecutionSchedule.Type.DAG && component != null && component.cycleResult() != null
                    ? component.cycleResult().exactRequiredSeed() : Map.of(), phaseIndex, schedulePhase.componentId(),
                schedulePhase.type(), taskIds, steps, schedule.dependencies().stream()
                    .filter(edge -> edge.consumerPhase() == currentPhaseIndex)
                    .map(ECOExecutionSchedule.PhaseDependency::producerPhase).sorted().toList()));
        }
        return new ECOExecutionPlan(signature, mode, tasks, phases, schedule);
    }

    private static List<PlannedInputAllocation> defaultAllocations(IPatternDetails pattern, PlannerAmount crafts) {
        List<PlannedInputAllocation> result = new ArrayList<>();
        var inputs = pattern.getInputs();
        if (inputs == null) return result;
        for (int slot = 0; slot < inputs.length; slot++) {
            var possible = inputs[slot].getPossibleInputs();
            if (possible == null || possible.length == 0 || possible[0] == null || possible[0].what() == null) continue;
            result.add(new PlannedInputAllocation(slot, List.of(new PlannedInputAllocation.Run(
                possible[0].what(), PlannerAmount.of(possible[0].amount())
                    .multiply(Math.max(1L, inputs[slot].getMultiplier())), crafts))));
        }
        return result;
    }

    private static void validateCycleCounts(ComponentPlanningResult component,
            List<ECOExecutionPlan.ExecutionStep> steps, List<ECOExecutionPlan.TaskSpec> tasks,
            Map<PlanIdentity.PatternIdentity, Integer> taskIdByIdentity) {
        Map<PlanIdentity.PatternIdentity, PlannerAmount> signature = PlanIdentity.exactTaskSignature(
            component.cycleResult().exactPatternTimes());
        if (signature == null) throw new IllegalStateException("Cycle firing vector has no stable identity");
        Map<PlanIdentity.PatternIdentity, PlannerAmount> expected = new HashMap<>();
        signature.forEach((identity, count) -> {
            if (count != null && count.signum() > 0) expected.put(identity, count);
        });
        Map<PlanIdentity.PatternIdentity, PlannerAmount> actual = new HashMap<>();
        PlannerAmount[] repeats = RepeatLayout.plannerMultipliers(steps);
        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            actual.merge(tasks.get(step.taskId()).identity(),
                step.plannerCount().multiply(repeats[i]), PlannerAmount::add);
        }
        if (!actual.equals(expected)) {
            throw new IllegalStateException("Compact cycle trace does not equal the solved firing vector");
        }
        for (var entry : actual.entrySet()) {
            var task = java.util.Optional.ofNullable(taskIdByIdentity.get(entry.getKey()))
                .map(tasks::get).orElseThrow();
            if (entry.getValue().compareTo(task.plannerTotalCount()) > 0) {
                throw new IllegalStateException("Cycle trace consumes more than the aggregate AE2 task count");
            }
        }
    }

    private static void validateCycleDispositions(Map<appeng.api.stacks.AEKey, PlannerAmount> used,
            List<ComponentPlanningResult> components) {
        Map<appeng.api.stacks.AEKey, PlannerAmount> reservations = new HashMap<>();
        for (ComponentPlanningResult component : components) {
            if (component.type() != ComponentPlanningResult.Type.CYCLIC) continue;
            switch (component.cycleDisposition()) {
                case NOT_REQUIRED -> {
                    if (hasPositiveFirings(component) || !component.stockReservations().isEmpty()) {
                        throw componentFailure(component,
                            "NOT_REQUIRED component has positive firings or stock reservations");
                    }
                }
                case STOCK_SATISFIED -> validateStockSatisfied(used, component);
                case ORDERED_EXECUTION -> validateOrdered(component);
                case DYNAMIC_EXECUTION -> validateDynamic(component);
                case BLOCKED -> throw componentFailure(component,
                    "Planner marked cyclic component BLOCKED: " + component.diagnostic());
            }
            for (var reservation : component.plannerStockReservations().entrySet()) {
                PlannerAmount planned = reservation.getValue();
                PlannerAmount finalUsed = used.getOrDefault(reservation.getKey(), PlannerAmount.ZERO);
                if (planned.signum() <= 0 || finalUsed.compareTo(planned) < 0) {
                    throw componentFailure(component, "Stock reservation is absent from final usedItems: key="
                        + reservation.getKey() + " stockReserved=" + planned + " finalUsedItems=" + finalUsed);
                }
                reservations.merge(reservation.getKey(), planned, PlannerAmount::add);
            }
        }
        reservations.forEach((key, projected) -> {
            PlannerAmount finalUsed = used.getOrDefault(key, PlannerAmount.ZERO);
            if (projected.compareTo(finalUsed) > 0) {
                throw new IllegalStateException("Cycle stock reservation projections exceed final usedItems: key="
                    + key + " projected=" + projected + " finalUsedItems=" + finalUsed);
            }
        });
    }

    private static void validateStockSatisfied(Map<appeng.api.stacks.AEKey, PlannerAmount> used,
            ComponentPlanningResult component) {
        if (component.requiredOutputs().isEmpty()) {
            throw componentFailure(component, "STOCK_SATISFIED component has no positive demand");
        }
        if (component.cycleResult() == null || !component.cycleResult().status().solved()
                || hasPositiveFirings(component)) {
            throw componentFailure(component, "STOCK_SATISFIED requires a solved zero-firing cycle result");
        }
        for (var requirement : component.plannerRequiredOutputs().entrySet()) {
            PlannerAmount required = requirement.getValue();
            PlannerAmount stockReserved = component.plannerStockReservations().getOrDefault(requirement.getKey(), PlannerAmount.ZERO);
            PlannerAmount finalUsed = used.getOrDefault(requirement.getKey(), PlannerAmount.ZERO);
            if (required.signum() <= 0 || stockReserved.compareTo(required) < 0 || finalUsed.compareTo(stockReserved) < 0) {
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
            Map<Integer, PlannerAmount> firings, List<ECOExecutionPlan.TaskSpec> tasks) {
        Map<PlanIdentity.PatternIdentity, PlannerAmount> signature = PlanIdentity.exactTaskSignature(
            component.cycleResult().exactPatternTimes());
        if (signature == null) throw new IllegalStateException("Dynamic cycle firing vector has no stable identity");
        Map<PlanIdentity.PatternIdentity, PlannerAmount> actual = new HashMap<>();
        firings.forEach((taskId, count) -> actual.merge(tasks.get(taskId).identity(), count, PlannerAmount::add));
        Map<PlanIdentity.PatternIdentity, PlannerAmount> expected = new HashMap<>();
        signature.forEach((identity, count) -> {
            if (count.signum() > 0) expected.put(identity, count);
        });
        if (!actual.equals(expected)) {
            throw new IllegalStateException("Dynamic cycle firing vector changed at the execution boundary");
        }
        firings.forEach((taskId, count) -> {
            if (count.compareTo(tasks.get(taskId).plannerTotalCount()) > 0) {
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
        private PlannerAmount count;
        private PlannedTask(PlanIdentity.PatternIdentity identity, IPatternDetails pattern, PlannerAmount count) {
            this.identity = identity; this.pattern = pattern; this.count = count;
        }
        PlanIdentity.PatternIdentity identity() { return identity; }
        IPatternDetails pattern() { return pattern; }
        PlannerAmount count() { return count; }
        void add(PlannerAmount amount) { count = count.add(amount); }
    }
}
