package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import java.util.Objects;

public record ECOHyperflowResult<R>(
    Status status,
    ECOPlanCandidate<R> candidate,
    long expandedStates
) {
    public ECOHyperflowResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(candidate, "candidate");
        if (expandedStates < 0) {
            throw new IllegalArgumentException("expandedStates cannot be negative");
        }
    }

    public enum Status {
        COMPLETE,
        MISSING_SOURCES,
        NO_ROUTE,
        BUDGET_EXHAUSTED
    }
}
