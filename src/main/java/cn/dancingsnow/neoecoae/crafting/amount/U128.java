package cn.dancingsnow.neoecoae.crafting.amount;

import java.math.BigInteger;
import org.jetbrains.annotations.Nullable;

/**
 * Unsigned 128-bit magnitude stored in two longs. Arithmetic never allocates BigInteger;
 * an overflowing add, multiply or left shift returns null for the caller's wider tier.
 */
public final class U128 implements Comparable<U128> {
    public static final U128 ZERO = new U128(0L, 0L);
    public static final U128 ONE = new U128(0L, 1L);
    public static final U128 MAX = new U128(-1L, -1L);
    private static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64);

    private final long hi;
    private final long lo;

    private U128(long hi, long lo) { this.hi = hi; this.lo = lo; }

    public static U128 of(long value) {
        if (value < 0L) throw new IllegalArgumentException("U128 must not be negative");
        return ofParts(0L, value);
    }

    public static @Nullable U128 of(BigInteger value) {
        if (value.signum() < 0 || value.bitLength() > 128) return null;
        return ofParts(value.shiftRight(64).longValue(), value.longValue());
    }

    /** Both arguments are unsigned word bit patterns, including negative Java long values. */
    public static U128 ofParts(long hi, long lo) {
        if (hi == 0L) {
            if (lo == 0L) return ZERO;
            if (lo == 1L) return ONE;
        }
        return new U128(hi, lo);
    }

    public boolean isZero() { return hi == 0L && lo == 0L; }
    public long highWord() { return hi; }
    public long lowWord() { return lo; }
    public boolean fitsLong() { return hi == 0L && lo >= 0L; }
    public long longValue() { return lo; }
    public long toLongSaturated() { return fitsLong() ? lo : Long.MAX_VALUE; }
    public int bitLength() { return hi == 0L ? 64 - Long.numberOfLeadingZeros(lo) : 128 - Long.numberOfLeadingZeros(hi); }

    public BigInteger toBigInteger() {
        BigInteger low = unsigned(lo);
        return hi == 0L ? low : unsigned(hi).shiftLeft(64).or(low);
    }

    private static BigInteger unsigned(long word) {
        return word >= 0L ? BigInteger.valueOf(word) : BigInteger.valueOf(word).add(TWO_64);
    }

    public double doubleValue() {
        double high = hi >= 0L ? hi : (double) (hi & Long.MAX_VALUE) + 0x1.0p63;
        double low = lo >= 0L ? lo : (double) (lo & Long.MAX_VALUE) + 0x1.0p63;
        return high * 0x1.0p64 + low;
    }

    public @Nullable U128 add(U128 other) {
        if (other.isZero()) return this;
        if (isZero()) return other;
        long low = lo + other.lo;
        long high = hi + other.hi;
        if (Long.compareUnsigned(high, hi) < 0) return null;
        if (Long.compareUnsigned(low, lo) < 0) {
            if (high == -1L) return null;
            high++;
        }
        return ofParts(high, low);
    }

    public @Nullable U128 add(long other) {
        if (other < 0L) return subtract(ofParts(0L, -other));
        if (other == 0L) return this;
        long low = lo + other;
        if (Long.compareUnsigned(low, lo) < 0) {
            if (hi == -1L) return null;
            return ofParts(hi + 1L, low);
        }
        return ofParts(hi, low);
    }

    public @Nullable U128 subtract(U128 other) {
        if (compareTo(other) < 0) return null;
        if (other.isZero()) return this;
        long borrow = Long.compareUnsigned(lo, other.lo) < 0 ? 1L : 0L;
        return ofParts(hi - other.hi - borrow, lo - other.lo);
    }

    public @Nullable U128 subtract(long other) {
        return other < 0L ? add(ofParts(0L, -other)) : subtract(of(other));
    }

    public @Nullable U128 multiply(long factor) {
        if (factor < 0L) throw new IllegalArgumentException("U128 factor must not be negative");
        return multiplyUnsigned(factor);
    }

    public @Nullable U128 multiply(U128 other) {
        if (hi != 0L && other.hi != 0L) return null;
        return hi == 0L ? other.multiplyUnsigned(lo) : multiplyUnsigned(other.lo);
    }

    private @Nullable U128 multiplyUnsigned(long factor) {
        if (factor == 0L || isZero()) return ZERO;
        if (factor == 1L) return this;
        if (unsignedMultiplyHigh(hi, factor) != 0L) return null;
        long high = hi * factor;
        long sum = high + unsignedMultiplyHigh(lo, factor);
        return Long.compareUnsigned(sum, high) < 0 ? null : ofParts(sum, lo * factor);
    }

    public @Nullable U128 shiftLeft(int distance) {
        if (distance < 0) throw new IllegalArgumentException("Negative unsigned shift");
        if (isZero() || distance == 0) return this;
        if (distance > 128 - bitLength()) return null;
        return distance >= 64 ? ofParts(lo << (distance - 64), 0L)
                : ofParts((hi << distance) | (lo >>> (64 - distance)), lo << distance);
    }

    public U128 shiftRight(int distance) {
        if (distance < 0) throw new IllegalArgumentException("Negative unsigned shift");
        if (distance == 0) return this;
        if (distance >= 128) return ZERO;
        return distance >= 64 ? ofParts(0L, hi >>> (distance - 64))
                : ofParts(hi >>> distance, (lo >>> distance) | (hi << (64 - distance)));
    }

    public record Division(U128 quotient, U128 remainder) {}

    /** One division computes both results; the loop uses primitive words, with no per-bit allocation. */
    public Division divideAndRemainder(U128 divisor) {
        if (divisor.isZero()) throw new ArithmeticException("division by zero");
        int comparison = compareTo(divisor);
        if (comparison < 0) return new Division(ZERO, this);
        if (comparison == 0) return new Division(ONE, ZERO);
        if (hi == 0L) {
            return new Division(ofParts(0L, Long.divideUnsigned(lo, divisor.lo)),
                    ofParts(0L, Long.remainderUnsigned(lo, divisor.lo)));
        }
        int trailing = divisor.lo == 0L ? 64 + Long.numberOfTrailingZeros(divisor.hi)
                : Long.numberOfTrailingZeros(divisor.lo);
        if (divisor.bitLength() == trailing + 1) {
            U128 quotient = shiftRight(trailing);
            U128 remainder = trailing >= 64
                    ? ofParts(hi & ((1L << (trailing - 64)) - 1L), lo)
                    : ofParts(0L, lo & ((1L << trailing) - 1L));
            return new Division(quotient, remainder);
        }
        if (divisor.hi == 0L) {
            long qHi = Long.divideUnsigned(hi, divisor.lo);
            long remainder = Long.remainderUnsigned(hi, divisor.lo);
            if (remainder == 0L) {
                return new Division(ofParts(qHi, Long.divideUnsigned(lo, divisor.lo)),
                        ofParts(0L, Long.remainderUnsigned(lo, divisor.lo)));
            }
            // Knuth's two quotient digits in base 2^32: at most two corrections per digit,
            // replacing 64 restoring-division iterations for the common long divisor.
            long base = 1L << 32, mask = base - 1L;
            int normalization = Long.numberOfLeadingZeros(divisor.lo);
            long normalized = divisor.lo << normalization;
            long v1 = normalized >>> 32, v0 = normalized & mask;
            long upper = normalization == 0 ? remainder
                    : (remainder << normalization) | (lo >>> (64 - normalization));
            long lower = lo << normalization, u1 = lower >>> 32, u0 = lower & mask;
            long q1 = Long.divideUnsigned(upper, v1), rhat = Long.remainderUnsigned(upper, v1);
            while (q1 >= base || Long.compareUnsigned(q1 * v0, (rhat << 32) + u1) > 0) {
                q1--;
                rhat += v1;
                if (rhat >= base) break;
            }
            long middle = (upper << 32) + u1 - q1 * normalized;
            long q0 = Long.divideUnsigned(middle, v1);
            rhat = Long.remainderUnsigned(middle, v1);
            while (q0 >= base || Long.compareUnsigned(q0 * v0, (rhat << 32) + u0) > 0) {
                q0--;
                rhat += v1;
                if (rhat >= base) break;
            }
            remainder = ((middle << 32) + u0 - q0 * normalized) >>> normalization;
            return new Division(ofParts(qHi, (q1 << 32) + q0), ofParts(0L, remainder));
        }
        int shift = bitLength() - divisor.bitLength();
        U128 aligned = divisor.shiftLeft(shift);
        long dHi = aligned.hi, dLo = aligned.lo, rHi = hi, rLo = lo, qHi = 0L, qLo = 0L;
        for (int bit = shift; bit >= 0; bit--) {
            int high = Long.compareUnsigned(rHi, dHi);
            if (high > 0 || (high == 0 && Long.compareUnsigned(rLo, dLo) >= 0)) {
                rHi -= dHi + (Long.compareUnsigned(rLo, dLo) < 0 ? 1L : 0L);
                rLo -= dLo;
                if (bit >= 64) qHi |= 1L << (bit - 64); else qLo |= 1L << bit;
            }
            dLo = (dLo >>> 1) | (dHi << 63);
            dHi >>>= 1;
        }
        return new Division(ofParts(qHi, qLo), ofParts(rHi, rLo));
    }

    public U128 divide(long divisor) {
        if (divisor <= 0L) throw new ArithmeticException("U128 divisor must be positive");
        return divideAndRemainder(of(divisor)).quotient();
    }

    public long remainder(long divisor) {
        if (divisor <= 0L) throw new ArithmeticException("U128 divisor must be positive");
        return divideAndRemainder(of(divisor)).remainder().lo;
    }

    @Override public int compareTo(U128 other) {
        int high = Long.compareUnsigned(hi, other.hi);
        return high != 0 ? high : Long.compareUnsigned(lo, other.lo);
    }

    @Override public boolean equals(Object other) {
        return other instanceof U128 value && value.hi == hi && value.lo == lo;
    }

    /** Same magnitude hash as BigInteger, without converting or allocating its int array. */
    @Override public int hashCode() {
        return hashWords(hi, lo);
    }

    public static int hashWords(long high, long low) {
        int hash = (int) (high >>> 32);
        hash = 31 * hash + (int) high;
        hash = 31 * hash + (int) (low >>> 32);
        return 31 * hash + (int) low;
    }

    @Override public String toString() { return fitsLong() ? Long.toString(lo) : toBigInteger().toString(); }

    static long unsignedMultiplyHigh(long x, long y) {
        return Math.multiplyHigh(x, y) + ((x >> 63) & y) + ((y >> 63) & x);
    }
}
