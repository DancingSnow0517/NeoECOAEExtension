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
        AMOUNT_OVERFLOW,
        CANCELLED,
        INTERNAL_ERROR
    }
}
