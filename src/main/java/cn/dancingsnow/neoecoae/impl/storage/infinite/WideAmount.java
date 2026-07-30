package cn.dancingsnow.neoecoae.impl.storage.infinite;

import java.math.BigInteger;
import java.util.Arrays;

/** Mutable non-negative arbitrary precision counter optimized for long deltas. */
final class WideAmount {
    private static final int LIMB_BITS = 60;
    private static final long BASE = 1L << LIMB_BITS;
    private static final long MASK = BASE - 1L;

    private long[] limbs;
    private int size;

    private WideAmount(long[] limbs, int size) {
        this.limbs = limbs;
        this.size = size;
        normalize();
    }

    static WideAmount of(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        long low = value & MASK;
        long high = value >>> LIMB_BITS;
        return high == 0L
                ? new WideAmount(new long[] {low}, low == 0L ? 0 : 1)
                : new WideAmount(new long[] {low, high}, 2);
    }

    static WideAmount of(BigInteger value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (value.signum() == 0) {
            return of(0L);
        }
        int count = 1 + (value.bitLength() - 1) / LIMB_BITS;
        long[] limbs = new long[count];
        BigInteger remaining = value;
        BigInteger mask = BigInteger.valueOf(MASK);
        for (int i = 0; i < count; i++) {
            limbs[i] = remaining.and(mask).longValue();
            remaining = remaining.shiftRight(LIMB_BITS);
        }
        return new WideAmount(limbs, count);
    }

    void add(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (value == 0L) {
            return;
        }
        ensureSize(Math.max(2, size));
        long sum = limbs[0] + (value & MASK);
        limbs[0] = sum & MASK;
        long carry = (value >>> LIMB_BITS) + (sum >>> LIMB_BITS);
        int index = 1;
        while (carry != 0L) {
            ensureSize(index + 1);
            sum = limbs[index] + carry;
            limbs[index] = sum & MASK;
            carry = sum >>> LIMB_BITS;
            index++;
        }
        normalize();
    }

    void add(WideAmount value) {
        if (value == null || value.size == 0) {
            return;
        }
        ensureSize(Math.max(size, value.size));
        long carry = 0L;
        int index = 0;
        while (index < value.size || carry != 0L) {
            ensureSize(index + 1);
            long addend = index < value.size ? value.limbs[index] : 0L;
            long sum = limbs[index] + addend + carry;
            limbs[index] = sum & MASK;
            carry = sum >>> LIMB_BITS;
            index++;
        }
        normalize();
    }

    void subtract(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (compareTo(value) < 0) {
            throw new ArithmeticException("Amount subtraction would become negative");
        }
        subtractUpTo(value);
    }

    void subtract(WideAmount value) {
        if (value == null || value.isZero()) {
            return;
        }
        if (compareTo(value) < 0) {
            throw new ArithmeticException("Amount subtraction would become negative");
        }
        long borrow = 0L;
        int index = 0;
        while (index < value.size || borrow != 0L) {
            long subtrahend = (index < value.size ? value.limbs[index] : 0L) + borrow;
            long next = limbs[index] - subtrahend;
            if (next < 0L) {
                limbs[index] = next + BASE;
                borrow = 1L;
            } else {
                limbs[index] = next;
                borrow = 0L;
            }
            index++;
        }
        normalize();
    }

    long subtractUpTo(long requested) {
        if (requested < 0L) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        if (requested == 0L || size == 0) {
            return 0L;
        }
        long taken = fitsLong() ? Math.min(requested, toLongExact()) : requested;
        long low = taken & MASK;
        long high = taken >>> LIMB_BITS;

        long next = limbs[0] - low;
        long borrow = 0L;
        if (next < 0L) {
            next += BASE;
            borrow = 1L;
        }
        limbs[0] = next;
        long remaining = high + borrow;
        int index = 1;
        while (remaining != 0L) {
            next = limbs[index] - remaining;
            if (next < 0L) {
                limbs[index] = next + BASE;
                remaining = 1L;
            } else {
                limbs[index] = next;
                remaining = 0L;
            }
            index++;
        }
        normalize();
        return taken;
    }

    void clear() {
        Arrays.fill(limbs, 0L);
        size = 0;
    }

    boolean isZero() {
        return size == 0;
    }

    boolean fitsLong() {
        return size <= 1 || size == 2 && limbs[1] <= 7L;
    }

    long toLongExact() {
        if (!fitsLong()) {
            throw new ArithmeticException("Amount does not fit in a long");
        }
        return size == 0 ? 0L : limbs[0] + (size == 1 ? 0L : limbs[1] << LIMB_BITS);
    }

    int compareTo(long value) {
        if (value < 0L || !fitsLong()) {
            return 1;
        }
        return Long.compare(toLongExact(), value);
    }

    BigInteger toBigInteger() {
        BigInteger result = BigInteger.ZERO;
        for (int i = size - 1; i >= 0; i--) {
            result = result.shiftLeft(LIMB_BITS).add(BigInteger.valueOf(limbs[i]));
        }
        return result;
    }

    private int compareTo(WideAmount other) {
        if (size != other.size) {
            return Integer.compare(size, other.size);
        }
        for (int i = size - 1; i >= 0; i--) {
            int comparison = Long.compare(limbs[i], other.limbs[i]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private void ensureSize(int required) {
        if (required > limbs.length) {
            limbs = Arrays.copyOf(limbs, Math.max(required, Math.max(2, limbs.length * 2)));
        }
        if (required > size) {
            size = required;
        }
    }

    private void normalize() {
        while (size > 0 && limbs[size - 1] == 0L) {
            size--;
        }
    }
}
