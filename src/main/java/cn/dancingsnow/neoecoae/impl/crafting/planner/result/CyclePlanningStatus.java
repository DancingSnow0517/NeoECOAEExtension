package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus;

/** UI-facing status of one cyclic component inside a planning attempt. */
public enum CyclePlanningStatus {
    /** The component exists structurally but the plan does not need any output from it. */
    NOT_REQUIRED,
    /** Cycle planning is switched off for this network. */
    DISABLED,
    /** The configured solver is the inert stage-zero stub. */
    NOT_IMPLEMENTED,
    /** A pattern inside the component is not batch-safe. */
    UNSUPPORTED,
    /** The solver found a concrete, non-negative firing order for the current stock snapshot. */
    SOLVED,
    /** Proven at the current stock snapshot: the component needs seed or boundary material it does not have. */
    INSUFFICIENT_EXTERNAL_INPUT,
    /** The bounded search ran out of budget. Nothing is proven, and this is never a missing-items verdict. */
    UNKNOWN_BUDGET,
    /** The component is beyond the stage-one structural limits. */
    TOO_COMPLEX,
    /** Solving was cancelled. */
    CANCELLED;

    public static CyclePlanningStatus of(CycleSolveStatus status) {
        return switch (status) {
            case SUCCESS -> SOLVED;
            case INSUFFICIENT_EXTERNAL_INPUT -> INSUFFICIENT_EXTERNAL_INPUT;
            case UNKNOWN_BUDGET -> UNKNOWN_BUDGET;
            case TOO_COMPLEX -> TOO_COMPLEX;
            case UNSUPPORTED_PATTERN -> UNSUPPORTED;
            case CANCELLED -> CANCELLED;
            case NOT_IMPLEMENTED -> NOT_IMPLEMENTED;
        };
    }
}
