package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

public enum PlanningStatus {
    SUCCESS,
    MISSING_ITEMS,
    PARTIAL_UNSUPPORTED,
    CYCLE_UNSUPPORTED,
    CANCELLED,
    AMOUNT_OVERFLOW,
    INTERNAL_ERROR
}
