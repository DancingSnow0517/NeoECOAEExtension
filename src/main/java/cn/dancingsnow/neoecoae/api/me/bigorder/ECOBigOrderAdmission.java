package cn.dancingsnow.neoecoae.api.me.bigorder;

import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import java.math.BigInteger;
import java.util.function.LongUnaryOperator;

/** Submission allow-list: diagnostic shells never become execution plans. */
public final class ECOBigOrderAdmission {
    private ECOBigOrderAdmission() {}

    /** Check current access without narrowing a parent's exact reservation to long. */
    public static boolean hasStoredAmount(BigInteger required, boolean unbounded, LongUnaryOperator simulate) {
        if (required.signum() <= 0) return true;
        BigInteger limit = BigInteger.valueOf(Long.MAX_VALUE);
        if (!unbounded && required.compareTo(limit) > 0) return false;
        long probe = required.min(limit).longValueExact();
        return simulate.applyAsLong(probe) >= probe;
    }
    public static boolean allows(PlanningStatus status, boolean forced) {
        return status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
                || forced && status == PlanningStatus.MISSING_ITEMS;
    }
    public static boolean allows(ECOPlanningResult result, boolean forced) {
        if (result == null || result.plan() == null || !allows(result.status(), forced)) return false;
        if (result.components().stream().anyMatch(component ->
                component.status() == cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.UNRESOLVED
                || component.status() == cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.UNSUPPORTED
                || component.status() == cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult.Status.SOLVED_NOT_EMITTED))
            return false;
        return forced || result.trace().nodes().stream().noneMatch(node -> node.exactMissing().signum() > 0);
    }
}
