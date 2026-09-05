package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

/** Planner-owned decision about whether a cyclic component contributes runtime work. */
public enum CycleExecutionDisposition {
    /** The component has no positive demand in this plan. */
    NOT_REQUIRED,
    /** Positive demand is covered by explicitly reserved inventory and requires no cycle firing. */
    STOCK_SATISFIED,
    /** The solved component has positive firings and must execute with ordered cycle metadata. */
    ORDERED_EXECUTION,
    /** The solved component has exact firings, but its patterns are selected from live inventory at runtime. */
    DYNAMIC_EXECUTION,
    /** Positive demand has neither complete stock coverage nor a valid executable cycle solve. */
    BLOCKED
}
