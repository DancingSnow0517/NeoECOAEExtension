package cn.dancingsnow.neoecoae.impl.crafting.planner.solver;

import cn.dancingsnow.neoecoae.impl.crafting.planner.model.ECOPlanCandidate;
import java.util.Objects;
import java.util.Optional;

public record ECOHyperflowResult<R>(
    Status status,
    ECOPlanCandidate<R> candidate,
    long expandedStates,
    Optional<ECOCycleTrace<R>> cycleTrace
) {
    public ECOHyperflowResult(Status status, ECOPlanCandidate<R> candidate, long expandedStates) {
        this(status, candidate, expandedStates, Optional.empty());
    }

    public ECOHyperflowResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(candidate, "candidate");
        if (expandedStates < 0) {
            throw new IllegalArgumentException("expandedStates cannot be negative");
        }
        cycleTrace = Objects.requireNonNull(cycleTrace, "cycleTrace");
    }

    public enum Status {
        COMPLETE,
        MISSING_SOURCES,
        NO_ROUTE,
        BUDGET_EXHAUSTED
    }
}
