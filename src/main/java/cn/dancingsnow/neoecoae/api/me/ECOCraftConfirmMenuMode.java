package cn.dancingsnow.neoecoae.api.me;

import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.List;
import java.math.BigInteger;
import org.jetbrains.annotations.Nullable;

/** Client-synchronized ECO state for the AE2 crafting confirmation menu. */
public interface ECOCraftConfirmMenuMode {
    boolean neoecoae$shouldShowFastPlannerReport();

    boolean neoecoae$isCyclePlanningEnabled();

    long neoecoae$getCalculationNanos();

    BigInteger neoecoae$getTheoreticalBytes();

    @Nullable PlanningStatus neoecoae$getPlanningStatus();

    List<ECOCycleItemList.Entry> neoecoae$getCycleItems();

    CraftingGraphSnapshot neoecoae$getCraftingGraphSnapshot();
}
