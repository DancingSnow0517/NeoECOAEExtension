package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import java.util.List;
import java.util.UUID;
import appeng.api.crafting.IPatternDetails;
import org.jetbrains.annotations.Nullable;

public record ECOPlanningResult(
    PlanningStatus status,
    @Nullable CraftingPlan plan,
    ECOPlanTrace trace,
    List<CycleDiagnostic> cycles,
    List<ComponentPlanningResult> components,
    List<Integer> executionComponentOrder,
    long calculationNanos,
    UUID planningId
) {
    public ECOPlanningResult {
        cycles = List.copyOf(cycles);
        components = List.copyOf(components);
        executionComponentOrder = List.copyOf(executionComponentOrder);
        calculationNanos = Math.max(0L, calculationNanos);
        planningId = planningId == null ? UUID.randomUUID() : planningId;
        if (status == PlanningStatus.SUCCESS && plan == null) {
            throw new IllegalArgumentException("A successful planning result requires a plan");
        }
    }

    public boolean shouldUseNativeFallback() {
        return status == PlanningStatus.PARTIAL_UNSUPPORTED || status == PlanningStatus.UNSUPPORTED
            || status == PlanningStatus.INTERNAL_ERROR;
    }

    /** Expanded ordered cycle witness consumed by the ECO CPU; large plans may retain only a compact execution plan. */
    public List<IPatternDetails> cycleWitness() {
        return executionSchedule().phases().stream().filter(p -> p.type() == ECOExecutionSchedule.Type.CYCLE)
            .flatMap(p -> p.cycleWitness().stream())
            .toList();
    }

    public ECOExecutionSchedule executionSchedule() {
        return status == PlanningStatus.SUCCESS && plan != null
            ? ECOExecutionSchedule.from(components, executionComponentOrder, plan.patternTimes())
            : ECOExecutionSchedule.from(components, executionComponentOrder);
    }

    /** Creates the immutable runtime hand-off once the physical plan has been selected. */
    public ECOExecutionContract executionContract() {
        if (plan == null) {
            throw new IllegalStateException("Cannot create an execution contract without a plan");
        }
        var signature = cn.dancingsnow.neoecoae.impl.crafting.planner.identity.PlanIdentity.of(plan);
        if (signature == null) throw new IllegalStateException("Plan identity unavailable");
        ECOExecutionSchedule schedule;
        try {
            schedule = executionSchedule();
        } catch (RuntimeException ex) {
            return new ECOExecutionContract(planningId, signature, ExecutionMode.BLOCKED, null,
                "SCHEDULE_BUILD_FAILED:" + ex.getClass().getSimpleName());
        }
        boolean cycle = components.stream().anyMatch(c -> c.type() == ComponentPlanningResult.Type.CYCLIC
            && c.cycleStatus() == CyclePlanningStatus.SOLVED);
        if (cycle && (schedule == null || schedule.phases().stream().noneMatch(p -> p.type() == ECOExecutionSchedule.Type.CYCLE))) {
            return new ECOExecutionContract(planningId, signature, ExecutionMode.BLOCKED, schedule,
                "CYCLE_METADATA_MISSING");
        }
        ExecutionMode mode = cycle ? ExecutionMode.ORDERED_CYCLE
            : schedule != null && !schedule.phases().isEmpty() ? ExecutionMode.PHASED_DAG : ExecutionMode.NATIVE;
        return new ECOExecutionContract(planningId, signature, mode, schedule, null);
    }

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, long calculationNanos) {
        this(status, plan, trace, cycles, List.of(), List.of(), calculationNanos);
    }

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, List<ComponentPlanningResult> components,
            List<Integer> executionComponentOrder, long calculationNanos) {
        this(status, plan, trace, cycles, components, executionComponentOrder, calculationNanos, UUID.randomUUID());
    }
}
