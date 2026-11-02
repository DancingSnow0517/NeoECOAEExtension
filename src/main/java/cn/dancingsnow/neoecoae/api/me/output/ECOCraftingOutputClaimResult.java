package cn.dancingsnow.neoecoae.api.me.output;

import java.util.Objects;

/** Immutable result of {@link ECOCraftingOutputClaimSink#claimCraftingOutput} bookkeeping. */
public record ECOCraftingOutputClaimResult(
        Status status,
        long requestedAmount,
        long claimedAmount,
        long deliveredToRequester,
        long deliveredToNetwork,
        long storedInCpu,
        long remainingFinalOutput,
        boolean jobFinished) {

    public ECOCraftingOutputClaimResult {
        Objects.requireNonNull(status, "status");
        if (requestedAmount < 0L || claimedAmount < 0L || deliveredToRequester < 0L
                || deliveredToNetwork < 0L || storedInCpu < 0L || remainingFinalOutput < 0L) {
            throw new IllegalArgumentException("Claim amounts must not be negative");
        }
        if (claimedAmount > requestedAmount) {
            throw new IllegalArgumentException("Claimed amount exceeds requested amount");
        }
        long destinations;
        try {
            destinations = Math.addExact(
                    Math.addExact(deliveredToRequester, deliveredToNetwork), storedInCpu);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Claim destinations overflow", overflow);
        }
        if (destinations != claimedAmount) {
            throw new IllegalArgumentException("Claim destinations do not add up to the claimed amount");
        }
        if (jobFinished && status != Status.ACCEPTED) {
            throw new IllegalArgumentException("Only an accepted claim can finish a job");
        }
    }

    public boolean claimedAnything() {
        return claimedAmount > 0L;
    }

    public boolean accepted() {
        return status == Status.ACCEPTED;
    }

    public enum Status {
        ACCEPTED,
        NO_JOB,
        NO_MATCH,
        INVALID_REQUEST,
        TERMINAL
    }
}
