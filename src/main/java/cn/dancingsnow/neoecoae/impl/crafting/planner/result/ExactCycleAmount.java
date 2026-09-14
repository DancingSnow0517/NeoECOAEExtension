package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.PlannerAmount;
import java.math.BigInteger;
import java.util.Objects;

/** Arbitrary-precision diagnostic amount. It is never an AE2/runtime quantity. */
public record ExactCycleAmount(BigInteger value) {
    public static final ExactCycleAmount ZERO = new ExactCycleAmount(BigInteger.ZERO);

    public ExactCycleAmount {
        Objects.requireNonNull(value, "value");
    }

    public static ExactCycleAmount of(PlannerAmount value) {
        return value.isZero() ? ZERO : new ExactCycleAmount(value.toBigInteger());
    }

    public static ExactCycleAmount of(long value) {
        return value == 0L ? ZERO : new ExactCycleAmount(BigInteger.valueOf(value));
    }

    public PlannerAmount plannerAmount() { return PlannerAmount.of(value); }
}
