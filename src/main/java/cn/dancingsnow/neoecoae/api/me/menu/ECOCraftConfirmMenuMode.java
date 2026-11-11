package cn.dancingsnow.neoecoae.api.me.menu;

import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.List;
import java.math.BigInteger;
import org.jetbrains.annotations.Nullable;

/** Client-synchronized ECO state for the AE2 crafting confirmation menu. */
public interface ECOCraftConfirmMenuMode {
    /** Whether the currently selected CPU belongs to ECO, independent of planner and tier. */
    default boolean neoecoae$isSelectedCpuEco() { return false; }

    /** CPU tier glyph for the ECO crafting report header: 4, 6, 9, or 0 when unavailable. */
    default int neoecoae$getSelectedCpuTier() { return 0; }

    default boolean neoecoae$bigOrderCpuAvailable() { return false; }
    default void neoecoae$startBigOrder(boolean forced) {}
    default int neoecoae$getOptimizationState() { return 0; }
    default boolean neoecoae$exactPlanReady() { return false; }
    default boolean neoecoae$provenInfeasible() { return false; }
    default long neoecoae$getDisplayedPlanRevision() { return -1L; }
    default boolean neoecoae$lockDisplayedPlan(long revision) { return true; }
    /** Whether this menu's normal AE2 planning request was eligible for ECO. */
    boolean neoecoae$isEcoPlannerAvailable();

    /** Whether the current result carries ECO planning diagnostics. */
    boolean neoecoae$isEcoReportReady();

    boolean neoecoae$shouldShowFastPlannerReport();

    boolean neoecoae$isCyclePlanningEnabled();

    /** Mode captured by the calculation displayed by this menu. */
    default boolean neoecoae$isLiteCraftingMode() { return false; }

    long neoecoae$getCalculationNanos();

    BigInteger neoecoae$getTheoreticalBytes();

    /** Byte estimate metadata can arrive before the material rows of the same final plan. */
    default boolean neoecoae$hasByteEstimate() {
        return neoecoae$getCraftingGraphSnapshot().hasMaterialDetails();
    }

    default boolean neoecoae$hasCompleteByteEstimate() {
        return neoecoae$getCraftingGraphSnapshot().hasCompleteByteEstimate();
    }

    @Nullable PlanningStatus neoecoae$getPlanningStatus();

    default String neoecoae$getPlanningDiagnostic() { return ""; }

    List<ECOCycleItemList.Entry> neoecoae$getCycleItems();

    CraftingGraphSnapshot neoecoae$getCraftingGraphSnapshot();
    default void neoecoae$setDiagnostics(long version, CraftingGraphSnapshot graph, ECOCycleItemList cycles) {}
    default void neoecoae$setMaterialReport(long version, CraftingGraphSnapshot report, ECOCycleItemList cycles) {
        neoecoae$setDiagnostics(version, report, cycles);
    }
    default boolean neoecoae$diagnosticsReady() { return true; }
    default boolean neoecoae$graphReady() { return neoecoae$diagnosticsReady(); }
    default int neoecoae$getReportSyncFailure() { return 0; }
}
