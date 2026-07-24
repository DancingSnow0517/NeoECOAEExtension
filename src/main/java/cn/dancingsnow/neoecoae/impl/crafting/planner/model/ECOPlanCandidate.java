package cn.dancingsnow.neoecoae.impl.crafting.planner.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ECOPlanCandidate<R>(
    Map<R, Long> executions,
    long requestedShortfall,
    long dependencyShortfall,
    long sourceShortfall,
    long surplus
) {
    public ECOPlanCandidate {
        Objects.requireNonNull(executions, "executions");
        Map<R, Long> copy = new LinkedHashMap<>();
        for (var entry : executions.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                copy.put(Objects.requireNonNull(entry.getKey(), "operation reference"), entry.getValue());
            }
        }
        executions = Map.copyOf(copy);
        if (requestedShortfall < 0 || dependencyShortfall < 0 || sourceShortfall < 0 || surplus < 0) {
            throw new IllegalArgumentException("Candidate scores cannot be negative");
        }
    }

    public long totalExecutions() {
        long total = 0;
        for (long count : executions.values()) {
            total = Math.addExact(total, count);
        }
        return total;
    }
}
