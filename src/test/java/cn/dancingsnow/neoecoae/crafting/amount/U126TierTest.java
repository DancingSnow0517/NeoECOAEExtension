package cn.dancingsnow.neoecoae.crafting.amount;

import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Every tier boundary of U126, PlannerAmount and HugeAmount checked against BigInteger as the reference. */
class U126TierTest {
    private static final BigInteger LIMIT = BigInteger.ONE.shiftLeft(126);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private final Random random = new Random(126);

    /** Non-negative values clustered around 0, 2^63, 2^64, 2^126 and beyond. */
    private BigInteger magnitude() {
        int[] bits = {0, 8, 62, 63, 64, 65, 100, 125, 126, 127, 140};
        int base = bits[random.nextInt(bits.length)];
        BigInteger center = base == 0 ? BigInteger.ZERO : BigInteger.ONE.shiftLeft(base);
        BigInteger offset = BigInteger.valueOf(random.nextInt(2001) - 1000);
        BigInteger value = random.nextInt(4) == 0 ? new BigInteger(Math.max(1, base), random) : center.add(offset);
        return value.signum() < 0 ? value.negate() : value;
    }

    private BigInteger signed() {
        BigInteger value = magnitude();
        return random.nextBoolean() ? value.negate() : value;
    }

    private static BigInteger expectU126(BigInteger value) {
        return value.signum() < 0 || value.compareTo(LIMIT) >= 0 ? null : value;
    }

    private static BigInteger value(U126 value) {
        return value == null ? null : value.toBigInteger();
    }

    @Test void u126MatchesBigIntegerAndReportsEveryOverflow() {
        assertEquals(LIMIT.subtract(BigInteger.ONE), U126.MAX.toBigInteger());
        for (int i = 0; i < 200_000; i++) {
            BigInteger a = magnitude(), b = magnitude();
            U126 x = U126.of(a), y = U126.of(b);
            assertEquals(expectU126(a), value(x));
            if (x == null || y == null) continue;
            assertEquals(expectU126(a.add(b)), value(x.add(y)), a + " + " + b);
            assertEquals(expectU126(a.subtract(b)), value(x.subtract(y)), a + " - " + b);
            assertEquals(Integer.signum(a.compareTo(b)), Integer.signum(x.compareTo(y)));
            assertEquals(a.equals(b), x.equals(y));
            assertEquals(expectU126(a.multiply(b)), value(x.multiply(y)), a + " * " + b);
            long l = b.bitLength() < 64 ? b.longValue() : random.nextLong() & Long.MAX_VALUE;
            BigInteger lb = BigInteger.valueOf(l);
            assertEquals(expectU126(a.add(lb)), value(x.add(l)));
            assertEquals(expectU126(a.subtract(lb)), value(x.subtract(l)));
            assertEquals(expectU126(a.add(lb.negate())), value(x.add(-l)));
            assertEquals(expectU126(a.multiply(lb)), value(x.multiply(l)));
            if (l > 0) {
                assertEquals(a.divide(lb), x.divide(l).toBigInteger(), a + " / " + l);
                assertEquals(a.remainder(lb).longValueExact(), x.remainder(l), a + " % " + l);
            }
            assertEquals(a.compareTo(LONG_MAX) <= 0, x.fitsLong());
            assertEquals(a.min(LONG_MAX).longValueExact(), x.toLongSaturated());
        }
        assertEquals(expectU126(BigInteger.TEN.add(LONG_MIN.negate())), value(U126.of(10).subtract(Long.MIN_VALUE)));
        assertEquals(LONG_MAX.add(BigInteger.ONE), U126.of(Long.MAX_VALUE).add(1L).toBigInteger());
    }

    @Test void plannerAmountMatchesBigIntegerAcrossAllTiers() {
        for (int i = 0; i < 200_000; i++) {
            BigInteger a = signed(), b = signed();
            PlannerAmount x = PlannerAmount.of(a), y = PlannerAmount.of(b);
            assertEquals(a, x.toBigInteger());
            assertEquals(a.compareTo(LONG_MIN) >= 0 && a.compareTo(LONG_MAX) <= 0, x.fitsLong());
            assertEquals(a.add(b), x.add(y).toBigInteger(), a + " + " + b);
            assertEquals(a.subtract(b), x.subtract(y).toBigInteger(), a + " - " + b);
            assertEquals(a.multiply(b), x.multiply(y).toBigInteger(), a + " * " + b);
            assertEquals(Integer.signum(a.compareTo(b)), Integer.signum(x.compareTo(y)));
            assertEquals(a.signum(), x.signum());
            assertEquals(a.equals(b), x.equals(y));
            if (a.equals(b)) assertEquals(x.hashCode(), y.hashCode());
            if (b.signum() != 0) {
                assertEquals(a.divide(b), x.divide(y).toBigInteger(), a + " / " + b);
                assertEquals(a.remainder(b), x.remainder(y).toBigInteger(), a + " % " + b);
            }
            if (a.signum() >= 0 && b.signum() > 0) {
                BigInteger[] qr = a.divideAndRemainder(b);
                BigInteger ceil = qr[1].signum() == 0 ? qr[0] : qr[0].add(BigInteger.ONE);
                assertEquals(ceil, x.ceilDiv(y).toBigInteger(), "ceil " + a + " / " + b);
            }
        }
        PlannerAmount min = PlannerAmount.of(Long.MIN_VALUE);
        assertEquals(LONG_MIN.negate(), min.divide(PlannerAmount.of(-1)).toBigInteger());
        assertEquals(LONG_MIN.negate(), PlannerAmount.ZERO.subtract(min).toBigInteger());
        assertTrue(PlannerAmount.ZERO.subtract(min).add(min).isZero());
        assertTrue(PlannerAmount.ZERO.subtract(min).add(min).fitsLong());
    }

    @Test void hugeAmountKeepsTiersAndItsSerializedForm() {
        for (int i = 0; i < 100_000; i++) {
            BigInteger a = magnitude(), b = magnitude();
            HugeAmount x = HugeAmount.of(a), y = HugeAmount.of(b);
            assertEquals(a, x.toBigInteger());
            assertEquals(a.compareTo(LONG_MAX) > 0, x.isBig());
            assertEquals(a.min(LONG_MAX).longValueExact(), x.toLongSaturated());
            assertEquals(a.add(b), x.add(y).toBigInteger());
            assertEquals(Integer.signum(a.compareTo(b)), Integer.signum(x.compareTo(y)));
            assertEquals(a.equals(b), x.equals(y));
            if (a.compareTo(b) >= 0) assertEquals(a.subtract(b), x.subtract(y).toBigInteger());
            else assertThrows(IllegalArgumentException.class, () -> x.subtract(y));
            long l = random.nextLong() & Long.MAX_VALUE;
            assertEquals(a.add(BigInteger.valueOf(l)), x.add(l).toBigInteger());
            if (a.compareTo(BigInteger.valueOf(l)) >= 0) {
                assertEquals(a.subtract(BigInteger.valueOf(l)), x.subtract(l).toBigInteger());
            }
            assertEquals(x, HugeAmount.read(x.write()));
            assertEquals(x.isBig(), x.write().contains("big"), "Serialized form must not change");
        }
    }
}
