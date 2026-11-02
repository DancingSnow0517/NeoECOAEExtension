package cn.dancingsnow.neoecoae.crafting.amount;

import java.math.BigInteger;

public record ExactAmount(BigInteger value, boolean infinite) {
    public ExactAmount {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Amount must not be negative");
    }
    public static ExactAmount finite(BigInteger value) { return new ExactAmount(value, false); }
    public static ExactAmount unbounded() { return new ExactAmount(BigInteger.ZERO, true); }

    public ExactAmount add(long amount) {
        return infinite ? this : finite(value.add(BigInteger.valueOf(amount)));
    }

    public ExactAmount add(ExactAmount other) {
        return infinite || other.infinite ? unbounded() : finite(value.add(other.value));
    }
}
