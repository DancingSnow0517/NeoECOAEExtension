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
    long calculationNanos
) {
    public ECOPlanningResult {
        cycles = List.copyOf(cycles);
        components = List.copyOf(components);
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
        return components.stream().filter(c -> c.cycleResult() != null)
            .flatMap(c -> c.cycleResult().executionWitness().stream().map(w -> w.pattern().details()))
            .toList();
    }

    public ECOPlanningResult(PlanningStatus status, @Nullable CraftingPlan plan, ECOPlanTrace trace,
            List<CycleDiagnostic> cycles, long calculationNanos) {
        this(status, plan, trace, cycles, List.of(), calculationNanos);
    }
}
