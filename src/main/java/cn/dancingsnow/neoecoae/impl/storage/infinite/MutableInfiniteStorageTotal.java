package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.math.BigInteger;

/**
 * Server-thread statistic; large totals accumulate small transfers without allocating on every operation.
 * Past Long.MAX_VALUE the exact base is a {@link HugeAmount}: U126 below 2^126, BigInteger only beyond.
 */
final class MutableInfiniteStorageTotal {
    /** The whole total while {@link #base} is null; otherwise a pending signed delta on top of it. */
    private long value;
    private HugeAmount base;

    void add(long amount) {
        if (base == null) {
            if (amount <= Long.MAX_VALUE - value) value += amount;
            else {
                base = HugeAmount.of(value).add(amount);
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

    void add(BigInteger amount) {
        if (amount.signum() < 0) subtract(HugeAmount.of(amount.negate()));
        else add(HugeAmount.of(amount));
    }

    void subtract(BigInteger amount) {
        if (amount.signum() < 0) add(HugeAmount.of(amount.negate()));
        else subtract(HugeAmount.of(amount));
    }

    void add(HugeAmount amount) { set(exact().add(amount)); }

    void subtract(HugeAmount amount) {
        HugeAmount total = exact();
        if (total.compareTo(amount) < 0) throw new IllegalArgumentException("Negative storage total");
        set(total.subtract(amount));
    }

    private void accumulate(long delta) {
        long next = value + delta;
        if (((value ^ next) & (delta ^ next)) >= 0L) {
            value = next;
            return;
        }
        set(exact());
        if (base != null) value = delta;
        else if (delta >= 0L) add(delta);
        else subtract(-delta);
    }

    private HugeAmount exact() {
        if (base == null) return HugeAmount.of(value);
        if (value >= 0L) return base.add(value);
        HugeAmount magnitude = value == Long.MIN_VALUE ? HugeAmount.of(Long.MAX_VALUE).add(1L) : HugeAmount.of(-value);
        if (base.compareTo(magnitude) < 0) throw new IllegalArgumentException("Negative storage total");
        return base.subtract(magnitude);
    }

    private void set(HugeAmount total) {
        if (total.isBig()) {
            base = total;
            value = 0L;
        } else {
            base = null;
            value = total.toLongSaturated();
        }
    }

    HugeAmount snapshot() {
        if (base != null) set(exact());
        return base == null ? HugeAmount.of(value) : base;
    }
}
