package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import org.jetbrains.annotations.Nullable;

/** Duck interface mixed into AE2 CraftingPlan so structured planner results survive the Future boundary. */
public interface ECOCraftingPlanDiagnostics {
    @Nullable ECOPlanningResult neoecoae$getPlanningResult();
    void neoecoae$setPlanningResult(@Nullable ECOPlanningResult result);
}
