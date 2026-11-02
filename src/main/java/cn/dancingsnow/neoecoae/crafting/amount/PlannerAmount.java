package cn.dancingsnow.neoecoae.crafting.amount;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Exact non-allocating-for-the-common-case arithmetic used by the planner.
 *
 * <p>Three tiers, always normalized to the smallest that fits: a signed {@code long}; a sign plus a {@link U126}
 * magnitude for anything below 2^126 in absolute value; and {@link BigInteger} only beyond that. The type is signed
 * because subtraction is useful while calculating deltas, although ordinary material quantities are non-negative.
 */
public final class PlannerAmount implements Comparable<PlannerAmount> {
    public static final PlannerAmount ZERO = new PlannerAmount(0L);
    public static final PlannerAmount ONE = new PlannerAmount(1L);

    private static final U126 LONG_MIN_MAGNITUDE = U126.of(Long.MAX_VALUE).add(1L);

    private final long fastValue;
    /** Middle tier: magnitude outside the long range, below 2^126. */
    private final U126 magnitude;
    private final boolean negative;
    private final BigInteger wideValue;

    private PlannerAmount(long value) {
        this.fastValue = value;
        this.magnitude = null;
        this.negative = false;
        this.wideValue = null;
    }

    private PlannerAmount(boolean negative, U126 magnitude) {
        this.fastValue = 0L;
        this.magnitude = magnitude;
        this.negative = negative;
        this.wideValue = null;
    }

    private PlannerAmount(BigInteger value) {
        this.fastValue = 0L;
        this.magnitude = null;
        this.negative = false;
        this.wideValue = value;
    }

    public static PlannerAmount of(long value) {
        if (value == 0L) return ZERO;
        if (value == 1L) return ONE;
        return new PlannerAmount(value);
    }

    public static PlannerAmount of(BigInteger value) {
        Objects.requireNonNull(value, "value");
        if (value.bitLength() < Long.SIZE) return of(value.longValue());
        U126 magnitude = U126.of(value.abs());
        return magnitude == null ? new PlannerAmount(value) : signed(value.signum() < 0, magnitude);
    }

    /** Normalizes a signed magnitude; {@code null} magnitude means the 126-bit tier overflowed. */
    private static PlannerAmount signed(boolean negative, U126 magnitude) {
        if (magnitude.fitsLong()) return of(negative ? -magnitude.longValue() : magnitude.longValue());
        if (negative && magnitude.equals(LONG_MIN_MAGNITUDE)) return of(Long.MIN_VALUE);
        return new PlannerAmount(negative, magnitude);
    }

    private boolean isWide() { return wideValue != null; }

    private boolean isNegative() { return magnitude != null ? negative : fastValue < 0L; }

    /** Magnitude of a non-wide value. */
    private U126 magnitude() {
        if (magnitude != null) return magnitude;
        if (fastValue == Long.MIN_VALUE) return LONG_MIN_MAGNITUDE;
        return U126.of(Math.abs(fastValue));
    }

    public PlannerAmount add(PlannerAmount other) {
        Objects.requireNonNull(other, "other");
        if (fitsLong() && other.fitsLong()) {
            long sum = fastValue + other.fastValue;
            if (((fastValue ^ sum) & (other.fastValue ^ sum)) >= 0L) return of(sum);
        }
        if (isWide() || other.isWide()) return of(toBigInteger().add(other.toBigInteger()));
        boolean leftNegative = isNegative(), rightNegative = other.isNegative();
        U126 left = magnitude(), right = other.magnitude();
        if (leftNegative == rightNegative) {
            U126 sum = left.add(right);
            return sum == null ? of(toBigInteger().add(other.toBigInteger())) : signed(leftNegative, sum);
        }
        return left.compareTo(right) >= 0
            ? signed(leftNegative, left.subtract(right))
            : signed(rightNegative, right.subtract(left));
    }

    public PlannerAmount add(long other) {
        return add(of(other));
    }

    public PlannerAmount subtract(PlannerAmount other) {
        Objects.requireNonNull(other, "other");
        if (fitsLong() && other.fitsLong()) {
            long difference = fastValue - other.fastValue;
            if (((fastValue ^ other.fastValue) & (fastValue ^ difference)) >= 0L) return of(difference);
        }
        return add(other.negate());
    }

    public PlannerAmount subtract(long other) {
        return subtract(of(other));
    }

    private PlannerAmount negate() {
        if (isWide()) return of(wideValue.negate());
        if (magnitude != null) return signed(!negative, magnitude);
        return fastValue == Long.MIN_VALUE ? signed(false, LONG_MIN_MAGNITUDE) : of(-fastValue);
    }

    public PlannerAmount multiply(PlannerAmount other) {
        Objects.requireNonNull(other, "other");
        if (fitsLong() && other.fitsLong()) {
            long high = Math.multiplyHigh(fastValue, other.fastValue);
            long low = fastValue * other.fastValue;
            if ((high == 0L && low >= 0L) || (high == -1L && low < 0L)) return of(low);
        }
        if (isZero() || other.isZero()) return ZERO;
        if (isWide() || other.isWide()) return of(toBigInteger().multiply(other.toBigInteger()));
        U126 product = magnitude().multiply(other.magnitude());
        return product == null ? of(toBigInteger().multiply(other.toBigInteger()))
            : signed(isNegative() != other.isNegative(), product);
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
        if (fitsLong() && divisor.fitsLong()) {
            long quotient = fastValue / divisor.fastValue;
            return of(fastValue % divisor.fastValue == 0L ? quotient : quotient + 1L);
        }
        if (!isWide() && divisor.fitsLong()) {
            U126 value = magnitude();
            U126 quotient = value.divide(divisor.fastValue);
            // quotient <= value / 2 when the divisor exceeds 1, so the increment cannot overflow.
            return signed(false, value.remainder(divisor.fastValue) == 0L ? quotient : quotient.add(1L));
        }
        BigInteger[] result = toBigInteger().divideAndRemainder(divisor.toBigInteger());
        return of(result[1].signum() == 0 ? result[0] : result[0].add(BigInteger.ONE));
    }

    public PlannerAmount divide(PlannerAmount divisor) {
        Objects.requireNonNull(divisor, "divisor");
        if (divisor.isZero()) throw new ArithmeticException("division by zero");
        if (fitsLong() && divisor.fitsLong()) {
            if (fastValue == Long.MIN_VALUE && divisor.fastValue == -1L) return signed(false, LONG_MIN_MAGNITUDE);
            return of(fastValue / divisor.fastValue);
        }
        if (!isWide() && divisor.fitsLong() && divisor.fastValue != Long.MIN_VALUE) {
            // Truncates toward zero, like long and BigInteger division.
            return signed(isNegative() != divisor.fastValue < 0L, magnitude().divide(Math.abs(divisor.fastValue)));
        }
        return of(toBigInteger().divide(divisor.toBigInteger()));
    }

    public PlannerAmount remainder(PlannerAmount divisor) {
        Objects.requireNonNull(divisor, "divisor");
        if (divisor.isZero()) throw new ArithmeticException("division by zero");
        if (fitsLong() && divisor.fitsLong()) {
            if (fastValue == Long.MIN_VALUE && divisor.fastValue == -1L) return ZERO;
            return of(fastValue % divisor.fastValue);
        }
        if (!isWide() && divisor.fitsLong() && divisor.fastValue != Long.MIN_VALUE) {
            // The remainder takes the dividend's sign, like long and BigInteger remainder.
            long remainder = magnitude().remainder(Math.abs(divisor.fastValue));
            return of(isNegative() ? -remainder : remainder);
        }
        return of(toBigInteger().remainder(divisor.toBigInteger()));
    }

    /** Exact planner storage estimate for a stack amount. */
    public static PlannerAmount stackBytes(PlannerAmount amount, long amountPerByte) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0 || amountPerByte <= 0) throw new IllegalArgumentException("stack bytes");
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
        if (isWide()) return wideValue.signum();
        if (magnitude != null) return negative ? -1 : 1;
        return Long.compare(fastValue, 0L);
    }

    public boolean isZero() {
        return signum() == 0;
    }

    public boolean fitsLong() {
        return magnitude == null && wideValue == null;
    }

    public long longValueExact() {
        if (fitsLong()) return fastValue;
        throw new ArithmeticException("BigInteger out of long range");
    }

    public BigInteger toBigInteger() {
        if (isWide()) return wideValue;
        if (magnitude != null) return negative ? magnitude.toBigInteger().negate() : magnitude.toBigInteger();
        return BigInteger.valueOf(fastValue);
    }

    @Override
    public int compareTo(PlannerAmount other) {
        Objects.requireNonNull(other, "other");
        if (fitsLong() && other.fitsLong()) return Long.compare(fastValue, other.fastValue);
        if (isWide() || other.isWide()) return toBigInteger().compareTo(other.toBigInteger());
        int sign = Integer.compare(signum(), other.signum());
        if (sign != 0) return sign;
        int byMagnitude = magnitude().compareTo(other.magnitude());
        return isNegative() ? -byMagnitude : byMagnitude;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlannerAmount amount && compareTo(amount) == 0;
    }

    @Override
    public int hashCode() {
        return toBigInteger().hashCode();
    }

    @Override
    public String toString() {
        return fitsLong() ? Long.toString(fastValue) : toBigInteger().toString();
    }
}
