package cn.dancingsnow.neoecoae.api.me.bigorder;

import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import java.math.BigInteger;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import java.util.function.LongUnaryOperator;

/** Submission allow-list: diagnostic shells never become execution plans. */
public final class ECOBigOrderAdmission {
    private ECOBigOrderAdmission() {}

    /** Check current access without narrowing a parent's exact reservation to long. */
    public static boolean hasStoredAmount(BigInteger required, boolean unbounded, LongUnaryOperator simulate) {
        if (required.signum() <= 0) return true;
        PlannerAmount quantity = PlannerAmount.of(required);
        return hasStoredAmount(quantity, PlannerAmount.of(Long.MAX_VALUE), unbounded, simulate);
    }

    public static boolean hasStoredAmount(PlannerAmount required, PlannerAmount stored, boolean unbounded,
            LongUnaryOperator simulate) {
        if (required.signum() <= 0) return true;
        if (!unbounded && stored.compareTo(required) < 0) return false;
        long probe = required.longValueSaturated();
        return simulate.applyAsLong(probe) >= probe;
    }
    public static boolean allows(PlanningStatus status, boolean forced) {
        return status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
                || forced && status == PlanningStatus.MISSING_ITEMS;
    }

    /** A forced shortage may wait for a fresh plan without executing the incomplete preview. */
    public static boolean waitsForMaterials(ECOPlanningResult result, boolean forced) {
        return forced && result != null && result.plan() != null
            && result.status() == PlanningStatus.MISSING_ITEMS;
    }

    public static boolean allows(ECOPlanningResult result, boolean forced) {
        if (result == null || result.plan() == null || !allows(result.status(), forced)) return false;
        if (result.components().stream().anyMatch(component ->
                component.status() != cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.PLANNED
                && component.status() != cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.NOT_REQUIRED))
            return false;
        // A wide AE2 projection says nothing about feasibility. Neither force-start nor a
        // completed firing vector can override an order-wide impossibility/closure proof.
        if (result.provenInfeasible()) return false;
        return forced || result.exactMissingItems().isEmpty()
            && result.trace().nodes().stream().noneMatch(node -> node.exactMissing().signum() > 0);
    }
}
