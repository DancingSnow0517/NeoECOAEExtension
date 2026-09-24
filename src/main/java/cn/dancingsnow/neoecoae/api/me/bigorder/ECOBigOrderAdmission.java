package cn.dancingsnow.neoecoae.api.me.bigorder;

import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import java.math.BigInteger;
import java.util.function.LongUnaryOperator;

/** Reject diagnostic planner shells before they can acquire CPU ownership. */
public final class ECOBigOrderAdmission {
    private ECOBigOrderAdmission() {}

    public static boolean hasStoredAmount(BigInteger required, boolean unbounded, LongUnaryOperator simulate) {
        if (required.signum() <= 0) return true;
        BigInteger limit = BigInteger.valueOf(Long.MAX_VALUE);
        if (!unbounded && required.compareTo(limit) > 0) return false;
        long probe = required.min(limit).longValueExact();
        return simulate.applyAsLong(probe) >= probe;
    }

    public static boolean allows(ECOPlanningResult result, boolean forced) {
        if (result == null || result.plan() == null) return false;
        if (result.status() != PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
                && !(forced && result.status() == PlanningStatus.MISSING_ITEMS)) return false;
        if (result.components().stream().anyMatch(component ->
                component.status() == ComponentPlanningResult.Status.UNRESOLVED
                        || component.status() == ComponentPlanningResult.Status.UNSUPPORTED
                        || component.status() == ComponentPlanningResult.Status.UNREPRESENTABLE
                        || component.status() == ComponentPlanningResult.Status.SOLVED_NOT_EMITTED)) return false;
        return forced || result.trace().nodes().stream().noneMatch(node -> node.exactMissing().signum() > 0);
    }
}
