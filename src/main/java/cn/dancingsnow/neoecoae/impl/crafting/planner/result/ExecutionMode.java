package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

/** The single execution policy selected for a physical crafting plan. */
public enum ExecutionMode {
    NATIVE,
    PHASED_DAG,
    ORDERED_CYCLE,
    BLOCKED
}
