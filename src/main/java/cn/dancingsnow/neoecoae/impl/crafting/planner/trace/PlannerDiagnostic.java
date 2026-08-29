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
        AMOUNT_OVERFLOW,
        CANCELLED,
        INTERNAL_ERROR
    }
}
