package cn.dancingsnow.neoecoae.util;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ExactAmountFormatterTest {
    @Test
    void exactDigitsAreNotLostAboveLongOrDoublePrecision() {
        BigInteger amount = new BigInteger("9223372036854775808123456789");
        assertEquals("9223372036854775808123456789", ExactAmountFormatter.full(amount, 1));
        assertEquals("9.2e27", ExactAmountFormatter.compact(amount, 1));
        assertFalse(ExactAmountFormatter.compact(amount, 1).contains("∞"));
    }

    @Test
    void fluidUnitsRetainTheFractionAndScaleTheExponent() {
        BigInteger amount = new BigInteger("9223372036854775808123");
        assertEquals("9223372036854775808.123", ExactAmountFormatter.full(amount, 1000));
        assertEquals("9.2e18", ExactAmountFormatter.compact(amount, 1000));
    }

    @Test
    void arbitraryMagnitudeNeverFallsBackToInfinity() {
        assertEquals("1e400", ExactAmountFormatter.compact(BigInteger.TEN.pow(400), 1));
        assertEquals("9.9e20", ExactAmountFormatter.compact(new BigInteger("999999999999999999999"), 1));
    }
}
