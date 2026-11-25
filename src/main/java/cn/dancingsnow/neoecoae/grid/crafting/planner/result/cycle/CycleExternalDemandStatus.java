package cn.dancingsnow.neoecoae.grid.crafting.planner.result.cycle;

public enum CycleExternalDemandStatus {
    SOLVED,
    MISSING,
    FORBIDDEN_ROUTE,
    UNSUPPORTED,
    OVERFLOW,
    /** The external DAG was solved exactly, but its AE2 execution fields need more than a long. */
    UNREPRESENTABLE
}
