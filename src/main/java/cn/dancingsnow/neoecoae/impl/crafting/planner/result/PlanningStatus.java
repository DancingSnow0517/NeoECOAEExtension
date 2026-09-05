package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

public enum PlanningStatus {
    SUCCESS,
    MISSING_ITEMS,
    PARTIAL,
    CYCLE_UNRESOLVED,
    UNSUPPORTED,
    PARTIAL_UNSUPPORTED,
    CYCLE_UNSUPPORTED,
    CANCELLED,
    AMOUNT_OVERFLOW,
    /** The exact theoretical plan exists, but an AE2 long-valued execution field cannot carry it. */
    PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
    INTERNAL_ERROR
}
