package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import java.util.List;
import appeng.api.crafting.IPatternDetails;
import org.jetbrains.annotations.Nullable;

public record ECOPlanningResult(
    PlanningStatus status,
    @Nullable CraftingPlan plan,
    ECOPlanTrace trace,
    List<CycleDiagnostic> cycles,
    List<ComponentPlanningResult> components,
    List<Integer> executionComponentOrder,
    long calculationNanos
) {
    public ECOPlanningResult {
        cycles = List.copyOf(cycles);
        components = List.copyOf(components);
        executionComponentOrder = List.copyOf(executionComponentOrder);
        calculationNanos = Math.max(0L, calculationNanos);
        if (status == PlanningStatus.SUCCESS && plan == null) {
            throw new IllegalArgumentException("A successful planning result requires a plan");
        }
    }

    public boolean shouldUseNativeFallback() {
        return status == PlanningStatus.PARTIAL_UNSUPPORTED || status == PlanningStatus.UNSUPPORTED
            || status == PlanningStatus.INTERNAL_ERROR;
    }

    /** Ordered cycle witness consumed by the ECO CPU; empty means vanilla scheduling is preserved. */
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

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, long calculationNanos) {
        this(status, plan, trace, cycles, List.of(), List.of(), calculationNanos);
    }
}
