package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import org.jetbrains.annotations.Nullable;

/**
 * ECO settings and diagnostics associated with one AE2 crafting calculation.
 */
public interface ECOCraftingCalculationSettings {
    boolean neoecoae$isIgnoringPatternSubstitutions();

    /** Latest structured ECO result, retained for diagnostics and the future ECO Crafting Plan UI. */
    @Nullable
    ECOPlanningResult neoecoae$getLastPlanningResult();
}
