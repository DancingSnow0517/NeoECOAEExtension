package cn.dancingsnow.neoecoae.crafting.amount;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Exact integer arithmetic shared by planning and execution; native tiers avoid BigInteger allocation.
 *
 * <p>Three tiers, always normalized to the smallest that fits: a signed {@code long}; a sign plus a {@link U128}
 * magnitude for anything below 2^128 in absolute value; and {@link BigInteger} only beyond that. The type is signed
 * because subtraction is useful while calculating deltas, although ordinary material quantities are non-negative.
 */
public final class PlannerAmount implements Comparable<PlannerAmount> {
    public static final PlannerAmount ZERO = new PlannerAmount(0L);
    public static final PlannerAmount ONE = new PlannerAmount(1L);

    private static final U128 LONG_MIN_MAGNITUDE = U128.of(Long.MAX_VALUE).add(1L);

    private final long fastValue;
    /** Middle tier: magnitude outside the long range, below 2^128. */
    private final U128 magnitude;
    private final boolean negative;
    private final BigInteger wideValue;

    private PlannerAmount(long value) {
        this.fastValue = value;
        this.magnitude = null;
        this.negative = false;
        this.wideValue = null;
    }

    private PlannerAmount(boolean negative, U128 magnitude) {
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
        if (value.bitLength() > 128) return new PlannerAmount(value);
        U128 magnitude = U128.of(value.abs());
        return magnitude == null ? new PlannerAmount(value) : signed(value.signum() < 0, magnitude);
    }

    /** Shares an immutable native magnitude without a BigInteger round trip. */
    public static PlannerAmount of(U128 value) {
        return signed(false, Objects.requireNonNull(value, "value"));
    }

    /** Decimal parsing is an input boundary; subsequent arithmetic uses the normalized native tier. */
    public static PlannerAmount parse(String value) { return of(new BigInteger(value)); }

    /** Normalizes a signed magnitude; {@code null} magnitude means the 128-bit tier overflowed. */
    private static PlannerAmount signed(boolean negative, U128 magnitude) {
        if (magnitude.fitsLong()) return of(negative ? -magnitude.longValue() : magnitude.longValue());
        if (negative && magnitude.equals(LONG_MIN_MAGNITUDE)) return of(Long.MIN_VALUE);
        return new PlannerAmount(negative, magnitude);
    }

    private boolean isWide() { return wideValue != null; }

    private boolean isNegative() { return magnitude != null ? negative : fastValue < 0L; }

    /** Magnitude of a non-wide value. */
    private U128 magnitude() {
        if (magnitude != null) return magnitude;
        if (fastValue == Long.MIN_VALUE) return LONG_MIN_MAGNITUDE;
        return U128.of(Math.abs(fastValue));
    }

    public PlannerAmount add(PlannerAmount other) {
        Objects.requireNonNull(other, "other");
        if (other.isZero()) return this;
        if (isZero()) return other;
        if (fitsLong() && other.fitsLong()) {
            long sum = fastValue + other.fastValue;
            if (((fastValue ^ sum) & (other.fastValue ^ sum)) >= 0L) return of(sum);
        }
        if (isWide() || other.isWide()) return of(toBigInteger().add(other.toBigInteger()));
        boolean leftNegative = isNegative(), rightNegative = other.isNegative();
        U128 left = magnitude(), right = other.magnitude();
        if (leftNegative == rightNegative) {
            U128 sum = left.add(right);
            return sum == null ? of(toBigInteger().add(other.toBigInteger())) : signed(leftNegative, sum);
        }
        return left.compareTo(right) >= 0
            ? signed(leftNegative, left.subtract(right))
            : signed(rightNegative, right.subtract(left));
    }

    public PlannerAmount add(long other) {
        if (other == 0L) return this;
        if (fitsLong()) {
            long sum = fastValue + other;
            if (((fastValue ^ sum) & (other ^ sum)) >= 0L) return of(sum);
        } else if (magnitude != null) {
            U128 sum = negative ? magnitude.subtract(other) : magnitude.add(other);
            return sum == null ? of(toBigInteger().add(BigInteger.valueOf(other))) : signed(negative, sum);
        }
        if (isWide()) {
            BigInteger sum = wideValue.add(BigInteger.valueOf(other));
            return wideValue.signum() == Long.signum(other) ? new PlannerAmount(sum) : of(sum);
        }
        return add(of(other));
    }

    public PlannerAmount subtract(PlannerAmount other) {
        Objects.requireNonNull(other, "other");
        if (other.isZero()) return this;
        if (fitsLong() && other.fitsLong()) {
            long difference = fastValue - other.fastValue;
            if (((fastValue ^ other.fastValue) & (fastValue ^ difference)) >= 0L) return of(difference);
        }
        return add(other.negate());
    }

    public PlannerAmount subtract(long other) {
        if (other == 0L) return this;
        if (fitsLong()) {
            long difference = fastValue - other;
            if (((fastValue ^ other) & (fastValue ^ difference)) >= 0L) return of(difference);
        }
        if (isWide()) {
            BigInteger difference = wideValue.subtract(BigInteger.valueOf(other));
            return wideValue.signum() != Long.signum(other) ? new PlannerAmount(difference) : of(difference);
        }
        if (other != Long.MIN_VALUE) return add(-other);
        return subtract(of(other));
    }

    public PlannerAmount negate() {
        if (isWide()) return new PlannerAmount(wideValue.negate());
        if (magnitude != null) return signed(!negative, magnitude);
        return fastValue == Long.MIN_VALUE ? signed(false, LONG_MIN_MAGNITUDE) : of(-fastValue);
    }

    public PlannerAmount multiply(PlannerAmount other) {
        Objects.requireNonNull(other, "other");
        if (isZero() || other.isZero()) return ZERO;
        if (other.equals(ONE)) return this;
        if (equals(ONE)) return other;
        if (fitsLong() && other.fitsLong()) {
            long high = Math.multiplyHigh(fastValue, other.fastValue);
            long low = fastValue * other.fastValue;
            if ((high == 0L && low >= 0L) || (high == -1L && low < 0L)) return of(low);
        }
        if (isWide() || other.isWide()) return of(toBigInteger().multiply(other.toBigInteger()));
        U128 product = magnitude().multiply(other.magnitude());
        return product == null ? of(toBigInteger().multiply(other.toBigInteger()))
            : signed(isNegative() != other.isNegative(), product);
    }

    public PlannerAmount multiply(long other) {
        if (other == 0L || isZero()) return ZERO;
        if (other == 1L) return this;
        if (other == -1L) return negate();
        if (fitsLong()) {
            long high = Math.multiplyHigh(fastValue, other), low = fastValue * other;
            if ((high == 0L && low >= 0L) || (high == -1L && low < 0L)) return of(low);
        }
        if (isWide()) return new PlannerAmount(wideValue.multiply(BigInteger.valueOf(other)));
        U128 product = other == Long.MIN_VALUE ? magnitude().multiply(LONG_MIN_MAGNITUDE)
                : magnitude().multiply(Math.abs(other));
        return product == null ? of(toBigInteger().multiply(BigInteger.valueOf(other)))
                : signed(isNegative() != (other < 0L), product);
    }

    /** Exact ceiling division for non-negative values and a positive divisor. */
    public PlannerAmount ceilDiv(PlannerAmount divisor) {
        Objects.requireNonNull(divisor, "divisor");
        if (signum() < 0 || divisor.signum() <= 0) {
            throw new IllegalArgumentException("ceilDiv requires value >= 0 and divisor > 0");
        }
        if (divisor.equals(ONE)) return this;
        if (fitsLong() && divisor.fitsLong()) {
            long quotient = fastValue / divisor.fastValue;
            return of(fastValue % divisor.fastValue == 0L ? quotient : quotient + 1L);
        }
        if (!isWide() && !divisor.isWide()) {
            var division = magnitude().divideAndRemainder(divisor.magnitude());
            return signed(false, division.remainder().isZero()
                    ? division.quotient() : division.quotient().add(1L));
        }
        BigInteger[] result = toBigInteger().divideAndRemainder(divisor.toBigInteger());
        return of(result[1].signum() == 0 ? result[0] : result[0].add(BigInteger.ONE));
    }

    public PlannerAmount divide(PlannerAmount divisor) {
        Objects.requireNonNull(divisor, "divisor");
        if (divisor.isZero()) throw new ArithmeticException("division by zero");
        if (divisor.equals(ONE)) return this;
        if (fitsLong() && divisor.fitsLong()) {
            if (fastValue == Long.MIN_VALUE && divisor.fastValue == -1L) return signed(false, LONG_MIN_MAGNITUDE);
            return of(fastValue / divisor.fastValue);
        }
        if (!isWide() && !divisor.isWide()) {
            return signed(isNegative() != divisor.isNegative(),
                    magnitude().divideAndRemainder(divisor.magnitude()).quotient());
        }
        return of(toBigInteger().divide(divisor.toBigInteger()));
    }

    public PlannerAmount remainder(PlannerAmount divisor) {
        Objects.requireNonNull(divisor, "divisor");
        if (divisor.isZero()) throw new ArithmeticException("division by zero");
        if (divisor.equals(ONE)) return ZERO;
        if (fitsLong() && divisor.fitsLong()) {
            if (fastValue == Long.MIN_VALUE && divisor.fastValue == -1L) return ZERO;
            return of(fastValue % divisor.fastValue);
        }
        if (!isWide() && !divisor.isWide()) {
            return signed(isNegative(), magnitude().divideAndRemainder(divisor.magnitude()).remainder());
        }
        return of(toBigInteger().remainder(divisor.toBigInteger()));
    }

    public PlannerAmount[] divideAndRemainder(PlannerAmount divisor) {
        Objects.requireNonNull(divisor, "divisor");
        if (divisor.isZero()) throw new ArithmeticException("division by zero");
        if (fitsLong() && divisor.fitsLong()) {
            if (fastValue == Long.MIN_VALUE && divisor.fastValue == -1L)
                return new PlannerAmount[]{signed(false, LONG_MIN_MAGNITUDE), ZERO};
            return new PlannerAmount[]{of(fastValue / divisor.fastValue), of(fastValue % divisor.fastValue)};
        }
        if (!isWide() && !divisor.isWide()) {
            var division = magnitude().divideAndRemainder(divisor.magnitude());
            return new PlannerAmount[]{signed(isNegative() != divisor.isNegative(), division.quotient()),
                    signed(isNegative(), division.remainder())};
        }
        BigInteger[] division = toBigInteger().divideAndRemainder(divisor.toBigInteger());
        return new PlannerAmount[]{of(division[0]), of(division[1])};
    }

    public PlannerAmount abs() { return signum() < 0 ? negate() : this; }

    public PlannerAmount gcd(PlannerAmount other) {
        PlannerAmount left = abs(), right = other.abs();
        if (left.fitsLong() && right.fitsLong()) {
            long a = left.fastValue, b = right.fastValue;
            while (b != 0L) { long remainder = a % b; a = b; b = remainder; }
            return of(a);
        }
        if (left.isWide() || right.isWide()) return of(left.toBigInteger().gcd(right.toBigInteger()));
        U128 a = left.magnitude(), b = right.magnitude();
        while (!b.isZero()) { U128 remainder = a.divideAndRemainder(b).remainder(); a = b; b = remainder; }
        return signed(false, a);
    }

    public PlannerAmount pow(int exponent) {
        if (exponent < 0) throw new ArithmeticException("Negative exponent");
        PlannerAmount result = ONE, factor = this;
        while (exponent != 0) {
            if ((exponent & 1) != 0) result = result.multiply(factor);
            exponent >>>= 1;
            if (exponent != 0) factor = factor.multiply(factor);
        }
        return result;
    }

    public PlannerAmount shiftLeft(int distance) {
        if (distance == Integer.MIN_VALUE) throw new ArithmeticException("Shift distance out of range");
        if (distance < 0) return shiftRight(-distance);
        if (isZero() || distance == 0) return this;
        if (isWide()) return of(wideValue.shiftLeft(distance));
        U128 shifted = magnitude().shiftLeft(distance);
        return shifted == null ? of(toBigInteger().shiftLeft(distance)) : signed(isNegative(), shifted);
    }

    public PlannerAmount shiftRight(int distance) {
        if (distance == Integer.MIN_VALUE) throw new ArithmeticException("Shift distance out of range");
        if (distance < 0) return shiftLeft(-distance);
        if (isZero() || distance == 0) return this;
        if (fitsLong()) return distance >= 64 ? of(fastValue < 0L ? -1L : 0L) : of(fastValue >> distance);
        if (isWide()) return of(wideValue.shiftRight(distance));
        U128 value = magnitude(), shifted = value.shiftRight(distance);
        if (negative && (distance >= 128 || !value.equals(shifted.shiftLeft(distance)))) shifted = shifted.add(1L);
        return signed(negative, shifted);
    }

    public int bitLength() {
        if (fitsLong()) return 64 - Long.numberOfLeadingZeros(fastValue < 0L ? ~fastValue : fastValue);
        if (isWide()) return wideValue.bitLength();
        return (negative ? magnitude.subtract(1L) : magnitude).bitLength();
    }

    /** Exact planner storage estimate for a stack amount. */
    public static PlannerAmount stackBytes(PlannerAmount amount, long amountPerByte) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0 || amountPerByte <= 0) throw new IllegalArgumentException("stack bytes");
        PlannerAmount divisor = of(amountPerByte);
        PlannerAmount[] division = amount.divideAndRemainder(divisor);
        PlannerAmount whole = division[0].multiply(8L);
        PlannerAmount partial = division[1].multiply(8L).ceilDiv(divisor);
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

    public boolean fitsDoubleLong() { return wideValue == null; }

    public long longValue() {
        if (isWide()) return wideValue.longValue();
        return magnitude == null ? fastValue : negative ? -magnitude.longValue() : magnitude.longValue();
    }

    public int intValue() { return (int) longValue(); }

    public int intValueExact() {
        long value = longValueExact();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new ArithmeticException("Amount out of int range");
        return (int) value;
    }

    public long longValueSaturated() {
        return fitsLong() ? fastValue : signum() < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
    }

    public double doubleValue() {
        if (isWide()) return wideValue.doubleValue();
        if (magnitude == null) return fastValue;
        return negative ? -magnitude.doubleValue() : magnitude.doubleValue();
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
        int sign = Integer.compare(signum(), other.signum());
        if (sign != 0) return sign;
        if (isWide() && other.isWide()) return wideValue.compareTo(other.wideValue);
        if (isWide() != other.isWide()) return isWide() ? signum() : -signum();
        if (fitsLong() != other.fitsLong()) return fitsLong() ? -signum() : signum();
        int byMagnitude = magnitude().compareTo(other.magnitude());
        return isNegative() ? -byMagnitude : byMagnitude;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PlannerAmount amount && compareTo(amount) == 0;
    }

    @Override
    public int hashCode() {
        if (isWide()) return wideValue.hashCode();
        if (magnitude != null) return negative ? -magnitude.hashCode() : magnitude.hashCode();
        long absolute = fastValue < 0L ? -fastValue : fastValue;
        int hash = U128.hashWords(0L, absolute);
        return fastValue < 0L ? -hash : hash;
    }

    @Override
    public String toString() {
        return fitsLong() ? Long.toString(fastValue) : toBigInteger().toString();
    }
}
