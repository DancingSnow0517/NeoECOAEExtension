package cn.dancingsnow.neoecoae.api.me.menu;

import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.List;
import java.math.BigInteger;
import org.jetbrains.annotations.Nullable;

/** Client-synchronized ECO state for the AE2 crafting confirmation menu. */
public interface ECOCraftConfirmMenuMode {
    default boolean neoecoae$bigOrderCpuAvailable() { return false; }
    default void neoecoae$startBigOrder(boolean forced) {}
    /** Whether this menu's normal AE2 planning request was eligible for ECO. */
    boolean neoecoae$isEcoPlannerAvailable();

    /** Whether the current result carries ECO planning diagnostics. */
    boolean neoecoae$isEcoReportReady();

    boolean neoecoae$shouldShowFastPlannerReport();

    boolean neoecoae$isCyclePlanningEnabled();

    long neoecoae$getCalculationNanos();

    BigInteger neoecoae$getTheoreticalBytes();

    @Nullable PlanningStatus neoecoae$getPlanningStatus();

    default String neoecoae$getPlanningDiagnostic() { return ""; }

    List<ECOCycleItemList.Entry> neoecoae$getCycleItems();

    CraftingGraphSnapshot neoecoae$getCraftingGraphSnapshot();
}
