package cn.dancingsnow.neoecoae.core.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class BigIntegersTest {
    @Test
    void nonNegativeReturnsZeroForNull() {
        assertEquals(BigInteger.ZERO, BigIntegers.nonNegative(null));
    }

    @Test
    void nonNegativeReturnsZeroForNegativeValues() {
        assertEquals(BigInteger.ZERO, BigIntegers.nonNegative(BigInteger.valueOf(-1L)));
        assertEquals(BigInteger.ZERO, BigIntegers.nonNegative(BigInteger.ONE.shiftLeft(200).negate()));
    }

    @Test
    void nonNegativePassesThroughZeroAndPositiveValues() {
        assertEquals(BigInteger.ZERO, BigIntegers.nonNegative(BigInteger.ZERO));
        assertEquals(BigInteger.valueOf(42L), BigIntegers.nonNegative(BigInteger.valueOf(42L)));
        assertEquals(BigInteger.ONE.shiftLeft(200), BigIntegers.nonNegative(BigInteger.ONE.shiftLeft(200)));
    }
}
