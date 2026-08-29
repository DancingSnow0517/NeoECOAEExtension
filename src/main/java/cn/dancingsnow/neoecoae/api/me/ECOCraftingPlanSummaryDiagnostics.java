package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import org.jetbrains.annotations.Nullable;

/** Extra ECO data serialized after AE2's CraftingPlanSummary payload. */
public interface ECOCraftingPlanSummaryDiagnostics {
    long neoecoae$getCalculationNanos();
    void neoecoae$setCalculationNanos(long nanos);
    @Nullable PlanningStatus neoecoae$getPlanningStatus();
    void neoecoae$setPlanningStatus(@Nullable PlanningStatus status);
}
