package cn.dancingsnow.neoecoae.crafting.planner.trace;

public record PlannerDiagnostic(Code code, String message) {
    public enum Code {
        FAST_DAG,
        JOINT_ROUTE_OPTIMIZED,
        ROUTE_OPTIMIZATION_BUDGET,
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
        /** A local impossibility proof; other routes or startup suppliers may still succeed. */
        CYCLE_PROVEN_INFEASIBLE_AT_CURRENT_STOCK,
        /** The accepted firing vector leaves the cycle's reusable seed in stock after delivery. */
        CYCLE_SEED_RETAINED,
        /** The reusable seed is consumed because no verified plan could keep it; the order still succeeds or fails on its own merits. */
        CYCLE_SEED_NOT_RETAINED,
        /** The first firing vector's boundary supply failed and a verified alternative vector was accepted. */
        CYCLE_SUPPLY_ALTERNATIVE,
        /** The boundary supply failed and every capped alternative firing vector was closed by the cycle solver. */
        CYCLE_SUPPLY_SEARCH_EXHAUSTED,
        /** The boundary supply failed and the alternative firing vector search stopped early; this is not a proof. */
        CYCLE_SUPPLY_SEARCH_INCOMPLETE,
        /** Even optimistic availability across every producer cannot reach the requested key. */
        ROUTE_PROVEN_UNREACHABLE,
        /** The bounded cycle search ran out of budget; explicitly not a missing-items verdict. */
        CYCLE_BUDGET_EXHAUSTED,
        /** Unvisited producer combinations remain; failed attempts do not prove global material shortage. */
        ROUTE_SEARCH_BUDGET_EXHAUSTED,
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
        INTERNAL_ERROR,
        /** Even an optimistic quantity bound over all producers cannot cover the requested order. */
        ROUTE_QUANTITY_BOUND,
        /** A quantity proof excluded an entire producer-choice subtree before numeric planning. */
        ROUTE_BRANCH_PRUNED
    }
}
