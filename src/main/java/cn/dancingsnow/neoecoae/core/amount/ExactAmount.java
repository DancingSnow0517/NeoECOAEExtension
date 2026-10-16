package cn.dancingsnow.neoecoae.core.amount;

import java.math.BigInteger;

public record ExactAmount(BigInteger value, boolean infinite) {
    public ExactAmount {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Amount must not be negative");
    }
    public static ExactAmount finite(BigInteger value) { return new ExactAmount(value, false); }
    public static ExactAmount unbounded() { return new ExactAmount(BigInteger.ZERO, true); }

    /** Compatibility DTO for persisted/network fields; arithmetic is owned by PlannerAmount. */
    public PlannerAmount plannerAmount() { return infinite ? PlannerAmount.unbounded() : PlannerAmount.of(value); }

    public static ExactAmount of(PlannerAmount amount) {
        return amount.infinite() ? unbounded() : finite(amount.toBigInteger());
    }

    public ExactAmount add(long amount) {
        // An unbounded snapshot ignores finite deltas, including negative adjustment deltas.
        return infinite ? this : of(plannerAmount().add(amount));
    }

    public ExactAmount add(ExactAmount other) {
        return of(plannerAmount().add(other.plannerAmount()));
    }
}
