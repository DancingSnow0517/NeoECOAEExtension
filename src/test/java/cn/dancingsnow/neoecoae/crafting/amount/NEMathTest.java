package cn.dancingsnow.neoecoae.crafting.amount;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class NEMathTest {
    @Test
    void intSaturatingAddAddsInRange() {
        assertEquals(7, NEMath.saturatingAddInt(3, 4));
        assertEquals(0, NEMath.saturatingAddInt(0, 0));
    }

    @Test
    void intSaturatingAddStopsAtMaxValue() {
        assertEquals(Integer.MAX_VALUE, NEMath.saturatingAddInt(Integer.MAX_VALUE, 1));
        assertEquals(Integer.MAX_VALUE, NEMath.saturatingAddInt(Integer.MAX_VALUE - 1, 2));
        assertEquals(Integer.MAX_VALUE, NEMath.saturatingAddInt(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE - 1, NEMath.saturatingAddInt(Integer.MAX_VALUE - 2, 1));
    }

    @Test
    void intSaturatingAddClampsNegativeInputsToZero() {
        assertEquals(4, NEMath.saturatingAddInt(-1, 4));
        assertEquals(3, NEMath.saturatingAddInt(3, -9));
        assertEquals(0, NEMath.saturatingAddInt(-7, -9));
        assertEquals(Integer.MAX_VALUE, NEMath.saturatingAddInt(Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void clampToLongReturnsZeroForNull() {
        assertEquals(0L, NEMath.clampToLong(null));
    }

    @Test
    void clampToLongReturnsZeroForNegativeValues() {
        assertEquals(0L, NEMath.clampToLong(BigInteger.valueOf(-1L)));
        assertEquals(0L, NEMath.clampToLong(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)));
    }

    @Test
    void clampToLongPassesThroughRepresentableValues() {
        assertEquals(0L, NEMath.clampToLong(BigInteger.ZERO));
        assertEquals(123L, NEMath.clampToLong(BigInteger.valueOf(123L)));
        assertEquals(Long.MAX_VALUE, NEMath.clampToLong(BigInteger.valueOf(Long.MAX_VALUE)));
    }

    @Test
    void clampToLongSaturatesAboveLongMaxValue() {
        assertEquals(Long.MAX_VALUE, NEMath.clampToLong(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)));
        assertEquals(Long.MAX_VALUE, NEMath.clampToLong(BigInteger.ONE.shiftLeft(200)));
    }
}
