package cn.dancingsnow.neoecoae.api.me.menu;

import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.List;
import java.math.BigInteger;
import org.jetbrains.annotations.Nullable;

/** Client-synchronized ECO state for the AE2 crafting confirmation menu. */
public interface ECOCraftConfirmMenuMode {
    /** Whether the server allows an explicit ECO request for this menu. */
    boolean neoecoae$isEcoPlannerAvailable();

    /** Whether the current result was produced by the independent ECO planning request. */
    boolean neoecoae$isEcoReportReady();

    void neoecoae$startEcoPlanning();

    boolean neoecoae$shouldShowFastPlannerReport();

    boolean neoecoae$isCyclePlanningEnabled();

    long neoecoae$getCalculationNanos();

    BigInteger neoecoae$getTheoreticalBytes();

    @Nullable PlanningStatus neoecoae$getPlanningStatus();

    List<ECOCycleItemList.Entry> neoecoae$getCycleItems();

    CraftingGraphSnapshot neoecoae$getCraftingGraphSnapshot();
}
