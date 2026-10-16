package cn.dancingsnow.neoecoae.core.amount;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Exact non-allocating-for-the-common-case arithmetic used by the planner.
 *
 * <p>Values that fit in a signed {@code long} stay on the fast path. The first
 * overflowing operation promotes the value to {@link BigInteger}; subsequent
 * operations on that value remain exact. The type is signed because subtraction
 * is useful while calculating deltas, although ordinary material quantities are
 * non-negative. Positive infinity is explicit and never converts to BigInteger or long;
 * undefined operations fail rather than silently manufacturing a finite quantity.
 */
public final class PlannerAmount implements Comparable<PlannerAmount> {
    public static final PlannerAmount ZERO = new PlannerAmount(0L);
    public static final PlannerAmount ONE = new PlannerAmount(1L);
    /** Explicit positive infinity. It can never be narrowed to a finite execution quantity. */
    public static final PlannerAmount INFINITE = new PlannerAmount(true);

    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final long fastValue;
    private final BigInteger wideValue;
    private final boolean infinite;

    private PlannerAmount(boolean infinite) {
        this.fastValue = 0L;
        this.wideValue = null;
        this.infinite = infinite;
    }

    public static PlannerAmount unbounded() { return INFINITE; }
    public boolean infinite() { return infinite; }

    private void requireFinite() {
        if (infinite) throw new ArithmeticException("Infinite amount has no finite value");
    }

    private static void requireNonNegative(PlannerAmount amount) {
        if (amount.signum() < 0) throw new ArithmeticException("Infinity arithmetic requires non-negative operands");
    }

    private PlannerAmount(long value) {
        this.fastValue = value;
        this.wideValue = null;
        this.infinite = false;
    }

    private PlannerAmount(BigInteger value) {
        Objects.requireNonNull(value, "value");
        this.infinite = false;
        if (fitsLong(value)) {
            this.fastValue = value.longValue();
            this.wideValue = null;
        } else {
            this.fastValue = 0L;
            this.wideValue = value;
        }
    }

    public static PlannerAmount of(long value) {
        if (value == 0L) return ZERO;
        if (value == 1L) return ONE;
        return new PlannerAmount(value);
    }

    public static PlannerAmount of(BigInteger value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() == 0) return ZERO;
        if (value.equals(BigInteger.ONE)) return ONE;
        return new PlannerAmount(value);
    }

    public PlannerAmount add(PlannerAmount other) {
        Objects.requireNonNull(other, "other");
        if (infinite || other.infinite) {
            requireNonNegative(this);
            requireNonNegative(other);
            return INFINITE;
        }
        if (wideValue == null && other.wideValue == null) {
            try {
                return of(Math.addExact(fastValue, other.fastValue));
            } catch (ArithmeticException ignored) {
                return of(BigInteger.valueOf(fastValue).add(BigInteger.valueOf(other.fastValue)));
            }
        }
        return of(toBigInteger().add(other.toBigInteger()));
    }

    public PlannerAmount add(long other) {
        return add(of(other));
    }

    public PlannerAmount subtract(PlannerAmount other) {
        Objects.requireNonNull(other, "other");
        if (other.infinite) throw new ArithmeticException("Cannot subtract infinite amount");
        if (infinite) return INFINITE;
        if (wideValue == null && other.wideValue == null) {
            try {
                return of(Math.subtractExact(fastValue, other.fastValue));
            } catch (ArithmeticException ignored) {
                return of(BigInteger.valueOf(fastValue).subtract(BigInteger.valueOf(other.fastValue)));
            }
        }
        return of(toBigInteger().subtract(other.toBigInteger()));
    }

    public PlannerAmount subtract(long other) {
        return subtract(of(other));
    }

    public PlannerAmount multiply(PlannerAmount other) {
        Objects.requireNonNull(other, "other");
        if (infinite || other.infinite) {
            requireNonNegative(this);
            requireNonNegative(other);
            return isZero() || other.isZero() ? ZERO : INFINITE;
        }
        if (wideValue == null && other.wideValue == null) {
            try {
                return of(Math.multiplyExact(fastValue, other.fastValue));
            } catch (ArithmeticException ignored) {
                return of(BigInteger.valueOf(fastValue).multiply(BigInteger.valueOf(other.fastValue)));
            }
        }
        return of(toBigInteger().multiply(other.toBigInteger()));
    }

    public PlannerAmount multiply(long other) {
        return multiply(of(other));
    }

    /** Exact ceiling division for non-negative values and a positive divisor. */
    public PlannerAmount ceilDiv(PlannerAmount divisor) {
        Objects.requireNonNull(divisor, "divisor");
        if (signum() < 0 || divisor.signum() <= 0) {
            throw new IllegalArgumentException("ceilDiv requires value >= 0 and divisor > 0");
        }
        if (divisor.infinite) {
            if (infinite) throw new ArithmeticException("Infinite divided by infinite");
            return isZero() ? ZERO : ONE;
        }
        if (infinite) return INFINITE;
        if (wideValue == null && divisor.wideValue == null) {
            long quotient = fastValue / divisor.fastValue;
            return of(fastValue % divisor.fastValue == 0L ? quotient : quotient + 1L);
        }
        BigInteger[] result = toBigInteger().divideAndRemainder(divisor.toBigInteger());
        return of(result[1].signum() == 0 ? result[0] : result[0].add(BigInteger.ONE));
    }

    public PlannerAmount divide(PlannerAmount divisor) {
        Objects.requireNonNull(divisor, "divisor");
        if (divisor.isZero()) throw new ArithmeticException("division by zero");
        if (infinite || divisor.infinite) {
            requireNonNegative(this);
            requireNonNegative(divisor);
            if (infinite && divisor.infinite) throw new ArithmeticException("Infinite divided by infinite");
            return infinite ? INFINITE : ZERO;
        }
        if (wideValue == null && divisor.wideValue == null) {
            if (fastValue == Long.MIN_VALUE && divisor.fastValue == -1L) {
                return of(BigInteger.valueOf(fastValue).divide(BigInteger.valueOf(divisor.fastValue)));
            }
            return of(fastValue / divisor.fastValue);
        }
        return of(toBigInteger().divide(divisor.toBigInteger()));
    }

    public PlannerAmount remainder(PlannerAmount divisor) {
        Objects.requireNonNull(divisor, "divisor");
        requireFinite();
        divisor.requireFinite();
        if (divisor.isZero()) throw new ArithmeticException("division by zero");
        if (wideValue == null && divisor.wideValue == null) {
            if (fastValue == Long.MIN_VALUE && divisor.fastValue == -1L) return ZERO;
            return of(fastValue % divisor.fastValue);
        }
        return of(toBigInteger().remainder(divisor.toBigInteger()));
    }

    /** Exact planner storage estimate for a stack amount. */
    public static PlannerAmount stackBytes(PlannerAmount amount, long amountPerByte) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0 || amountPerByte <= 0) throw new IllegalArgumentException("stack bytes");
        if (amount.infinite) return INFINITE;
        PlannerAmount divisor = of(amountPerByte);
        PlannerAmount whole = amount.divide(divisor).multiply(8L);
        PlannerAmount remainder = amount.remainder(divisor);
        PlannerAmount partial = remainder.multiply(8L).ceilDiv(divisor);
        return whole.add(partial);
    }

    public PlannerAmount min(PlannerAmount other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public PlannerAmount max(PlannerAmount other) {
        return compareTo(other) >= 0 ? this : other;
    }

    public int signum() {
        return infinite ? 1 : wideValue == null ? Long.compare(fastValue, 0L) : wideValue.signum();
    }

    public boolean isZero() {
        return signum() == 0;
    }

    public boolean fitsLong() {
        return !infinite && wideValue == null;
    }

    public long longValueExact() {
        requireFinite();
        return wideValue == null ? fastValue : wideValue.longValueExact();
    }

    public BigInteger toBigInteger() {
        requireFinite();
        return wideValue == null ? BigInteger.valueOf(fastValue) : wideValue;
    }

    @Override
    public int compareTo(PlannerAmount other) {
        Objects.requireNonNull(other, "other");
        if (infinite || other.infinite) return Boolean.compare(infinite, other.infinite);
        if (wideValue == null && other.wideValue == null) return Long.compare(fastValue, other.fastValue);
        return toBigInteger().compareTo(other.toBigInteger());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlannerAmount amount && compareTo(amount) == 0;
    }

    @Override
    public int hashCode() {
        return infinite ? 0x7ff00000 : toBigInteger().hashCode();
    }

    @Override
    public String toString() {
        return infinite ? "infinite" : wideValue == null ? Long.toString(fastValue) : wideValue.toString();
    }

    private static boolean fitsLong(BigInteger value) {
        return value.compareTo(LONG_MIN) >= 0 && value.compareTo(LONG_MAX) <= 0;
    }
}
