package cn.dancingsnow.neoecoae.api.me.lifecycle;

import java.util.Objects;

/** Immutable terminal result delivered to lifecycle listeners. */
public record ECOCraftingJobResult(
        Status status,
        long requestedAmount,
        long completedAmount,
        long remainingAmount) {

    public ECOCraftingJobResult {
        Objects.requireNonNull(status, "status");
        if (requestedAmount < 0L || completedAmount < 0L || remainingAmount < 0L) {
            throw new IllegalArgumentException("Job result amounts must not be negative");
        }
    }

    public boolean successful() {
        return status == Status.SUCCESS;
    }

    public enum Status {
        SUCCESS,
        FAILURE,
        CANCELLED
    }
}
