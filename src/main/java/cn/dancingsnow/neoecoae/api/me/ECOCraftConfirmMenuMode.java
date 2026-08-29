package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Client-synchronized ECO state for the AE2 crafting confirmation menu. */
public interface ECOCraftConfirmMenuMode {
    boolean neoecoae$shouldShowFastPlannerReport();

    long neoecoae$getCalculationNanos();

    @Nullable PlanningStatus neoecoae$getPlanningStatus();

    List<ECOCycleItemList.Entry> neoecoae$getCycleItems();
}
