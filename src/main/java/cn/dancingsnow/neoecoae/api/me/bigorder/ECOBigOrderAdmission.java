package cn.dancingsnow.neoecoae.api.me.bigorder;

import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;

/** Submission allow-list: diagnostic shells never become execution plans. */
public final class ECOBigOrderAdmission {
    private ECOBigOrderAdmission() {}
    public static boolean allows(PlanningStatus status, boolean forced) {
        return status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
                || forced && status == PlanningStatus.MISSING_ITEMS;
    }
    public static boolean allows(ECOPlanningResult result, boolean forced) {
        if (result == null || result.plan() == null || !allows(result.status(), forced)) return false;
        if (result.components().stream().anyMatch(component ->
                component.status() == cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Status.UNRESOLVED
                || component.status() == cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Status.UNSUPPORTED
                || component.status() == cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Status.SOLVED_NOT_EMITTED))
            return false;
        return forced || result.trace().nodes().stream().noneMatch(node -> node.exactMissing().signum() > 0);
    }
}
