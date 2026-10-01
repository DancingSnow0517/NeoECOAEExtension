package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.math.BigInteger;

/** Server-thread statistic; large totals accumulate small transfers without allocating on every operation. */
final class MutableInfiniteStorageTotal {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private long value;
    private BigInteger base;

    void add(long amount) {
        if (base == null) {
            if (amount <= Long.MAX_VALUE - value) value += amount;
            else {
                base = BigInteger.valueOf(value).add(BigInteger.valueOf(amount));
                value = 0;
            }
        } else accumulate(amount);
    }

    void subtract(long amount) {
        if (base == null) {
            if (amount > value) throw new IllegalArgumentException("Negative storage total");
            value -= amount;
        } else accumulate(-amount);
    }

    void add(BigInteger amount) { set(exact().add(amount)); }
    void subtract(BigInteger amount) { set(exact().subtract(amount)); }

    private void accumulate(long delta) {
        if (delta > 0 && value > Long.MAX_VALUE - delta
                || delta < 0 && value < Long.MIN_VALUE - delta) {
            base = base.add(BigInteger.valueOf(value));
            value = delta;
        } else value += delta;
    }

    private BigInteger exact() {
        return base == null ? BigInteger.valueOf(value) : base.add(BigInteger.valueOf(value));
    }

    private void set(BigInteger total) {
        if (total.signum() < 0) throw new IllegalArgumentException("Negative storage total");
        if (total.compareTo(MAX_LONG) <= 0) {
            value = total.longValueExact();
            base = null;
        } else {
            base = total;
            value = 0;
        }
    }

    HugeAmount snapshot() {
        if (base != null) set(exact());
        return base == null ? HugeAmount.of(value) : HugeAmount.of(base);
    }
}
