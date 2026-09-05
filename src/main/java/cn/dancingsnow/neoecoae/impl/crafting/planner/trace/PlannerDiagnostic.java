package cn.dancingsnow.neoecoae.impl.crafting.planner.trace;

public record PlannerDiagnostic(Code code, String message) {
    public enum Code {
        FAST_DAG,
        NATIVE_FALLBACK,
        CANDIDATE_REJECTED,
        CANDIDATE_DEFERRED_CYCLE,
        MISSING,
        UNSUPPORTED_INPUT,
        CYCLE_UNSUPPORTED,
        CYCLE_DISABLED,
        CYCLE_NOT_IMPLEMENTED,
        /** The cycle solver returned a verified firing order; stage one reports it without emitting it. */
        CYCLE_SOLVED,
        /** The cycle needs start-up or boundary material it does not have, proven at the current stock. */
        CYCLE_SEED_REQUIRED,
        /** The bounded cycle search ran out of budget; explicitly not a missing-items verdict. */
        CYCLE_BUDGET_EXHAUSTED,
        /** The cycle is beyond the stage-one structural limits. */
        CYCLE_TOO_COMPLEX,
        CYCLE_EXTERNAL_DEMAND_SOLVED,
        CYCLE_EXTERNAL_DEMAND_MISSING,
        CYCLE_EXTERNAL_ROUTE_FORBIDDEN,
        CYCLE_EXTERNAL_DEMAND_UNSUPPORTED,
        CYCLE_EXTERNAL_DEMAND_OVERFLOW,
        CYCLE_EXTERNAL_DEMAND_UNREPRESENTABLE,
        AMOUNT_OVERFLOW,
        /** Planning completed; the resulting AE2 execution field needs more than a signed long. */
        EXECUTION_AMOUNT_UNREPRESENTABLE,
        /** The raw AE2 task vector consumes more material than the plan can physically supply. */
        PLAN_MATERIAL_CLOSURE_INVALID,
        /** A consumed input had no numeric material attribution and will use conservative schedule fallback. */
        PROVENANCE_UNATTRIBUTED,
        CANCELLED,
        INTERNAL_ERROR
    }
}
