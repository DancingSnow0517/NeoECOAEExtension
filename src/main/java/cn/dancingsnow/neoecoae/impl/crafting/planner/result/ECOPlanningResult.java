package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public record ECOPlanningResult(
    PlanningStatus status,
    @Nullable CraftingPlan plan,
    ECOPlanTrace trace,
    List<CycleDiagnostic> cycles,
    long calculationNanos
) {
    public ECOPlanningResult {
        cycles = List.copyOf(cycles);
        calculationNanos = Math.max(0L, calculationNanos);
        if (status == PlanningStatus.SUCCESS && plan == null) {
            throw new IllegalArgumentException("A successful planning result requires a plan");
        }
    }

    public boolean shouldUseNativeFallback() {
        return status == PlanningStatus.PARTIAL_UNSUPPORTED || status == PlanningStatus.INTERNAL_ERROR;
    }
}
