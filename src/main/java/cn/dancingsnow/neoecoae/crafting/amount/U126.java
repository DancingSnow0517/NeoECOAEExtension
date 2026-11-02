package cn.dancingsnow.neoecoae.crafting.amount;

import java.math.BigInteger;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable unsigned 126-bit integer, the middle tier between {@code long} and {@link BigInteger}.
 *
 * <p>{@code hi} holds bits 64..125 ({@code 0 <= hi < 2^62}) and {@code lo} bits 0..63, read as unsigned. Every
 * operation that could leave the range returns {@code null} instead, so callers fall back to {@link BigInteger}
 * only past 2^126 - 1 (about 8.5e37). Java 17 compatible.
 */
public final class U126 implements Comparable<U126> {
    private static final long HI_LIMIT = 1L << 62;
    public static final U126 ZERO = new U126(0L, 0L);
    public static final U126 MAX = new U126(HI_LIMIT - 1L, -1L);
    private static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64);

    private final long hi;
    private final long lo;

    private U126(long hi, long lo) {
        this.hi = hi;
        this.lo = lo;
    }

    public static U126 of(long value) {
        if (value < 0L) throw new IllegalArgumentException("U126 must not be negative");
        return value == 0L ? ZERO : new U126(0L, value);
    }

    /** {@code null} when negative or wider than 126 bits. */
    public static @Nullable U126 of(BigInteger value) {
        if (value.signum() < 0 || value.bitLength() > 126) return null;
        return new U126(value.shiftRight(64).longValue(), value.longValue());
    }

    /** {@code null} when the parts do not form a value below 2^126. */
    static @Nullable U126 ofParts(long hi, long lo) {
        return hi < 0L || hi >= HI_LIMIT ? null : hi == 0L && lo == 0L ? ZERO : new U126(hi, lo);
    }

    public boolean isZero() { return hi == 0L && lo == 0L; }

    public boolean fitsLong() { return hi == 0L && lo >= 0L; }

    /** Only valid when {@link #fitsLong()}. */
    public long longValue() { return lo; }

    public long toLongSaturated() { return fitsLong() ? lo : Long.MAX_VALUE; }

    public BigInteger toBigInteger() {
        BigInteger low = lo >= 0L ? BigInteger.valueOf(lo) : BigInteger.valueOf(lo).add(TWO_64);
        return hi == 0L ? low : BigInteger.valueOf(hi).shiftLeft(64).or(low);
    }

    public @Nullable U126 add(U126 other) {
        long low = lo + other.lo;
        long carry = Long.compareUnsigned(low, lo) < 0 ? 1L : 0L;
        return ofParts(hi + other.hi + carry, low);
    }

    /** A negative {@code other} subtracts; {@code null} when the result leaves the range. */
    public @Nullable U126 add(long other) {
        if (other < 0L) {
            if (other != Long.MIN_VALUE) return subtract(-other);
            U126 half = subtract(Long.MAX_VALUE);
            return half == null ? null : half.subtract(1L);
        }
        long low = lo + other;
        return ofParts(hi + (Long.compareUnsigned(low, lo) < 0 ? 1L : 0L), low);
    }

    /** {@code null} when the result would be negative. */
    public @Nullable U126 subtract(U126 other) {
        long low = lo - other.lo;
        long borrow = Long.compareUnsigned(lo, other.lo) < 0 ? 1L : 0L;
        return ofParts(hi - other.hi - borrow, low);
    }

    /** {@code null} when the result would be negative. {@code Long.MIN_VALUE} is handled as 2^63. */
    public @Nullable U126 subtract(long other) {
        if (other < 0L) {
            if (other != Long.MIN_VALUE) return add(-other);
            U126 half = add(Long.MAX_VALUE);
            return half == null ? null : half.add(1L);
        }
        long low = lo - other;
        return ofParts(hi - (Long.compareUnsigned(lo, other) < 0 ? 1L : 0L), low);
    }

    /** Multiplies by a non-negative long; {@code null} past 126 bits. */
    public @Nullable U126 multiply(long factor) {
        if (factor < 0L) throw new IllegalArgumentException("U126 factor must not be negative");
        return multiplyUnsigned(factor);
    }

    public @Nullable U126 multiply(U126 other) {
        if (hi != 0L && other.hi != 0L) return null;
        return hi == 0L ? other.multiplyUnsigned(lo) : multiplyUnsigned(other.lo);
    }

    /** {@code factor} is read as unsigned 64-bit. */
    private @Nullable U126 multiplyUnsigned(long factor) {
        if (factor == 0L || isZero()) return ZERO;
        long low = lo * factor;
        long carry = unsignedMultiplyHigh(lo, factor);
        // hi < 2^62, so hi * factor fits below 2^126 only when the 128-bit product has no high word.
        if (unsignedMultiplyHigh(hi, factor) != 0L) return null;
        long high = hi * factor;
        long sum = high + carry;
        if (Long.compareUnsigned(sum, high) < 0) return null;
        return ofParts(sum, low);
    }

    /** Truncating division by a positive long. */
    public U126 divide(long divisor) {
        if (divisor <= 0L) throw new ArithmeticException("U126 divisor must be positive");
        long qHi = hi / divisor;
        long rem = hi % divisor;
        long qLo = 0L;
        // Restoring division of (rem * 2^64 + lo) by divisor; rem < divisor < 2^63, so 2 * rem + 1 fits unsigned.
        for (int bit = 63; bit >= 0; bit--) {
            long twice = (rem << 1) | ((lo >>> bit) & 1L);
            qLo <<= 1;
            if (Long.compareUnsigned(twice, divisor) >= 0) {
                twice -= divisor;
                qLo |= 1L;
            }
            rem = twice;
        }
        return qHi == 0L && qLo == 0L ? ZERO : new U126(qHi, qLo);
    }

    /** Remainder of division by a positive long. */
    public long remainder(long divisor) {
        if (divisor <= 0L) throw new ArithmeticException("U126 divisor must be positive");
        long rem = hi % divisor;
        for (int bit = 63; bit >= 0; bit--) {
            long twice = (rem << 1) | ((lo >>> bit) & 1L);
            if (Long.compareUnsigned(twice, divisor) >= 0) twice -= divisor;
            rem = twice;
        }
        return rem;
    }

    @Override
    public int compareTo(U126 other) {
        int high = Long.compare(hi, other.hi);
        return high != 0 ? high : Long.compareUnsigned(lo, other.lo);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof U126 value && value.hi == hi && value.lo == lo;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(hi) * 31 + Long.hashCode(lo);
    }

    @Override
    public String toString() {
        return fitsLong() ? Long.toString(lo) : toBigInteger().toString();
    }

    /** {@code Math.unsignedMultiplyHigh} is Java 18+; this is its documented identity over the signed high word. */
    static long unsignedMultiplyHigh(long x, long y) {
        return Math.multiplyHigh(x, y) + ((x >> 63) & y) + ((y >> 63) & x);
    }
}
