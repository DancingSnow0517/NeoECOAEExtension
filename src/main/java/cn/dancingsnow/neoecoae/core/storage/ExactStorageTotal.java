package cn.dancingsnow.neoecoae.core.storage;

import java.math.BigInteger;
import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;

/** Server-thread statistic; large totals accumulate small transfers without allocating on every operation. */
public final class ExactStorageTotal {
    private long value;
    private PlannerAmount base;

    public void add(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative storage quantity");
        if (base == null) {
            if (amount <= Long.MAX_VALUE - value) value += amount;
            else {
                base = PlannerAmount.of(value).add(amount);
                value = 0;
            }
        } else accumulate(amount);
    }

    public void subtract(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative storage quantity");
        if (base == null) {
            if (amount > value) throw new IllegalArgumentException("Negative storage total");
            value -= amount;
        } else {
            if (value < amount) {
                // amount - value is at most 2^64 - 1. Compare unsigned so this check
                // does not allocate an exact sum on every debit of a large total.
                BigInteger wideBase = base.toBigInteger();
                if (wideBase.bitLength() <= 64
                        && Long.compareUnsigned(wideBase.longValue(), amount - value) < 0) {
                    throw new IllegalArgumentException("Negative storage total");
                }
            }
            accumulate(-amount);
        }
    }

    public void add(BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative storage quantity");
        set(exact().add(PlannerAmount.of(amount)));
    }
    public void subtract(BigInteger amount) {
        if (amount.signum() < 0) throw new IllegalArgumentException("Negative storage quantity");
        set(exact().subtract(PlannerAmount.of(amount)));
    }

    private void accumulate(long delta) {
        if (delta > 0 && value > Long.MAX_VALUE - delta
                || delta < 0 && value < Long.MIN_VALUE - delta) {
            base = base.add(value);
            value = delta;
        } else value += delta;
    }

    private PlannerAmount exact() {
        return base == null ? PlannerAmount.of(value) : base.add(value);
    }

    private void set(PlannerAmount total) {
        if (total.signum() < 0) throw new IllegalArgumentException("Negative storage total");
        if (total.fitsLong()) {
            value = total.longValueExact();
            base = null;
        } else {
            base = total;
            value = 0;
        }
    }

    public BigInteger snapshot() {
        if (base != null) set(exact());
        return base == null ? BigInteger.valueOf(value) : base.toBigInteger();
    }
}
