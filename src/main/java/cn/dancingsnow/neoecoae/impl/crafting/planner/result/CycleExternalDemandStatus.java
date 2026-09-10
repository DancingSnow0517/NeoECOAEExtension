package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

public enum CycleExternalDemandStatus {
    SOLVED,
    MISSING,
    FORBIDDEN_ROUTE,
    UNSUPPORTED,
    OVERFLOW,
    /** The external DAG was solved exactly, but its AE2 execution fields need more than a long. */
    UNREPRESENTABLE
}
