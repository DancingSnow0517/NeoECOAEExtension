package cn.dancingsnow.neoecoae.impl.crafting.planner.schedule;

import java.util.Objects;

public record ECOScheduledStep<R>(R operation, long batches) {
    public ECOScheduledStep {
        Objects.requireNonNull(operation, "operation");
        if (batches <= 0) {
            throw new IllegalArgumentException("Scheduled batches must be positive");
        }
    }
}
