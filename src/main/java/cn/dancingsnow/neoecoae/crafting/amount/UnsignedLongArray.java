/*
 * Adapted from Bigger AE2, Copyright (c) 2024 Epimorphism, MIT License.
 * Source: DancingSnow0517/BiggerAE2, commit 61b97d61278a5141556e3b362094d80556847538.
 * The complete license is in META-INF/licenses/bigger-ae2-MIT.txt.
 */
package cn.dancingsnow.neoecoae.crafting.amount;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable non-negative magnitude in little-endian, unsigned 64-bit limbs.
 * Storage uses this after its fixed-width tier; BigInteger remains the public and persistence boundary.
 * This arithmetic source is shared unchanged by the four supported Minecraft branches and uses only Java 8 APIs.
 */
public final class UnsignedLongArray implements Comparable<UnsignedLongArray> {
    // Match BigInteger's supported bit range instead of imposing a smaller storage limit.
    private static final int MAX_LIMBS = (Integer.MAX_VALUE >>> 6) + 1;
    public static final UnsignedLongArray ZERO = new UnsignedLongArray(new long[0], true);

    private final long[] limbs;

    private UnsignedLongArray(long[] limbs, boolean normalized) {
        this.limbs = normalized ? limbs : normalize(limbs);
        if (this.limbs.length > MAX_LIMBS
            || this.limbs.length == MAX_LIMBS && this.limbs[MAX_LIMBS - 1] < 0) {
            throw new ArithmeticException("Unsigned integer exceeds BigInteger's supported range");
        }
    }

    public static UnsignedLongArray fromLong(long value) {
        if (value < 0) throw new IllegalArgumentException("Value must be non-negative: " + value);
        return value == 0 ? ZERO : new UnsignedLongArray(new long[] {value}, true);
    }

    /** Both arguments are unsigned word bit patterns, including negative Java long values. */
    public static UnsignedLongArray fromParts(long high, long low) {
        if (high != 0) return new UnsignedLongArray(new long[] {low, high}, true);
        return low == 0 ? ZERO : new UnsignedLongArray(new long[] {low}, true);
    }

    public static UnsignedLongArray fromLimbs(long[] limbs) {
        Objects.requireNonNull(limbs, "limbs");
        return limbs.length == 0 ? ZERO : new UnsignedLongArray(limbs.clone(), false);
    }

    /** Binary conversion is only needed when admitting an external exact quantity. */
    public static UnsignedLongArray fromBigInteger(BigInteger value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) throw new IllegalArgumentException("Value must be non-negative");
        if (value.signum() == 0) return ZERO;
        byte[] bytes = value.toByteArray();
        long[] limbs = new long[(value.bitLength() + 63) >>> 6];
        for (int i = 0; i < bytes.length && (i >>> 3) < limbs.length; i++) {
            limbs[i >>> 3] |= (bytes[bytes.length - 1 - i] & 0xFFL) << ((i & 7) << 3);
        }
        return new UnsignedLongArray(limbs, true);
    }

    public BigInteger toBigInteger() {
        if (isZero()) return BigInteger.ZERO;
        byte[] bytes = new byte[limbs.length * Long.BYTES];
        for (int i = 0; i < limbs.length; i++) {
            for (int j = 0; j < Long.BYTES; j++) {
                bytes[bytes.length - 1 - i * Long.BYTES - j] = (byte) (limbs[i] >>> (j << 3));
            }
        }
        return new BigInteger(1, bytes);
    }

    public long[] toLongArray() { return limbs.clone(); }

    /** Zero-extends the magnitude when reading an absent high limb. */
    public long limb(int index) {
        if (index < 0) throw new IndexOutOfBoundsException("Negative limb index");
        return index < limbs.length ? limbs[index] : 0L;
    }

    public int bitLength() {
        return isZero() ? 0 : (limbs.length - 1) * Long.SIZE
            + Long.SIZE - Long.numberOfLeadingZeros(limbs[limbs.length - 1]);
    }

    public boolean isZero() { return limbs.length == 0; }

    public UnsignedLongArray add(long value) {
        if (value < 0) throw new IllegalArgumentException("Value must be non-negative: " + value);
        if (value == 0) return this;
        if (limbs.length == 0) return fromLong(value);
        if (limbs.length == 1) {
            long sum = limbs[0] + value;
            return Long.compareUnsigned(sum, limbs[0]) < 0
                ? new UnsignedLongArray(new long[] {sum, 1}, true)
                : new UnsignedLongArray(new long[] {sum}, true);
        }

        long[] result = limbs.clone();
        long sum = result[0] + value;
        result[0] = sum;
        if (Long.compareUnsigned(sum, limbs[0]) >= 0) return new UnsignedLongArray(result, true);
        int index = 1;
        while (index < result.length && result[index] == -1L) result[index++] = 0;
        if (index == result.length) {
            result = Arrays.copyOf(result, result.length + 1);
            result[result.length - 1] = 1;
        } else {
            result[index]++;
        }
        return new UnsignedLongArray(result, true);
    }

    public UnsignedLongArray add(UnsignedLongArray other) {
        Objects.requireNonNull(other, "other");
        if (isZero()) return other;
        if (other.isZero()) return this;
        if (other.limbs.length == 1 && other.limbs[0] >= 0) return add(other.limbs[0]);
        int length = Math.max(limbs.length, other.limbs.length);
        long[] result = new long[length];
        long carry = 0;
        for (int i = 0; i < length; i++) {
            long left = limb(i), right = other.limb(i);
            long sum = left + right;
            long operandCarry = Long.compareUnsigned(sum, left) < 0 ? 1 : 0;
            long withCarry = sum + carry;
            long previousCarry = Long.compareUnsigned(withCarry, sum) < 0 ? 1 : 0;
            result[i] = withCarry;
            carry = operandCarry | previousCarry;
        }
        if (carry != 0) {
            result = Arrays.copyOf(result, length + 1);
            result[length] = carry;
        }
        return new UnsignedLongArray(result, true);
    }

    public UnsignedLongArray subtract(long value) {
        if (value < 0) throw new IllegalArgumentException("Value must be non-negative: " + value);
        if (value == 0) return this;
        if (limbs.length == 0 || limbs.length == 1 && Long.compareUnsigned(limbs[0], value) < 0) {
            throw new ArithmeticException("Unsigned subtraction underflow");
        }
        if (limbs.length == 1) {
            long difference = limbs[0] - value;
            return difference == 0 ? ZERO : new UnsignedLongArray(new long[] {difference}, true);
        }
        int length = limbs.length;
        if (limbs[length - 1] == 1 && lowerPartLessThan(length - 1, value)) length--;
        long[] result = new long[length];
        long borrow = 0;
        for (int i = 0; i < length; i++) {
            long left = limbs[i], right = i == 0 ? value : 0;
            result[i] = left - right - borrow;
            boolean borrowOut = borrow == 0
                ? Long.compareUnsigned(left, right) < 0 : Long.compareUnsigned(left, right) <= 0;
            borrow = borrowOut ? 1 : 0;
        }
        return new UnsignedLongArray(result, true);
    }

    public UnsignedLongArray subtract(UnsignedLongArray other) {
        Objects.requireNonNull(other, "other");
        if (compareTo(other) < 0) throw new ArithmeticException("Unsigned subtraction underflow");
        if (other.isZero()) return this;
        if (other.limbs.length == 1 && other.limbs[0] >= 0) return subtract(other.limbs[0]);
        long[] result = limbs.clone();
        long borrow = 0;
        for (int i = 0; i < result.length; i++) {
            long left = result[i], right = other.limb(i);
            result[i] = left - right - borrow;
            boolean borrowOut = borrow == 0
                ? Long.compareUnsigned(left, right) < 0 : Long.compareUnsigned(left, right) <= 0;
            borrow = borrowOut ? 1 : 0;
        }
        return new UnsignedLongArray(result, false);
    }

    public long toLongSaturated() {
        if (isZero()) return 0;
        return limbs.length > 1 || limbs[0] < 0 ? Long.MAX_VALUE : limbs[0];
    }

    private boolean lowerPartLessThan(int length, long value) {
        for (int i = length - 1; i >= 1; i--) {
            if (limbs[i] != 0) return false;
        }
        return Long.compareUnsigned(limbs[0], value) < 0;
    }

    private static long[] normalize(long[] source) {
        int length = source.length;
        while (length > 0 && source[length - 1] == 0) length--;
        return length == source.length ? source : Arrays.copyOf(source, length);
    }

    @Override
    public int compareTo(UnsignedLongArray other) {
        int length = Integer.compare(limbs.length, other.limbs.length);
        if (length != 0) return length;
        for (int i = limbs.length - 1; i >= 0; i--) {
            int comparison = Long.compareUnsigned(limbs[i], other.limbs[i]);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        return other == this || other instanceof UnsignedLongArray
            && Arrays.equals(limbs, ((UnsignedLongArray) other).limbs);
    }

    /** Same magnitude hash as BigInteger, without allocating a temporary BigInteger. */
    @Override
    public int hashCode() {
        int hash = 0;
        for (int i = limbs.length - 1; i >= 0; i--) {
            hash = 31 * hash + (int) (limbs[i] >>> 32);
            hash = 31 * hash + (int) limbs[i];
        }
        return hash;
    }

    @Override
    public String toString() { return toBigInteger().toString(); }
}
