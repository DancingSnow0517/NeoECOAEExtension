package cn.dancingsnow.neoecoae.crafting.display.format;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ExactAmountFormatterTest {
    @Test
    void exactDigitsAreNotLostAboveLongOrDoublePrecision() {
        BigInteger amount = new BigInteger("9223372036854775808123456789");
        assertEquals("9,223,372,036,854,775,808,123,456,789", ExactAmountFormatter.full(amount, 1));
        assertEquals("9.22B", ExactAmountFormatter.compact(amount, 1));
    }

    @Test
    void fluidUnitsRetainTheFractionAndUseGroupedTooltipDigits() {
        BigInteger amount = new BigInteger("9223372036854775808123");
        assertEquals("9,223,372,036,854,775,808.123", ExactAmountFormatter.full(amount, 1000));
        assertEquals("9.22E", ExactAmountFormatter.compact(amount, 1000));
    }

    @Test
    void compactAmountsUseStorageSuffixesAtEveryBoundary() {
        assertEquals("999", ExactAmountFormatter.compact(BigInteger.valueOf(999), 1));
        assertEquals("1.00K", ExactAmountFormatter.compact(BigInteger.valueOf(1000), 1));
        assertEquals("12.3K", ExactAmountFormatter.compact(BigInteger.valueOf(12345), 1));
        assertEquals("37.9E", ExactAmountFormatter.compact(new BigInteger("37900000000000000000"), 1));
        assertEquals("92.1Y", ExactAmountFormatter.compact(new BigInteger("92100000000000000000000000"), 1));
        assertEquals("10.0E", ExactAmountFormatter.compact(BigInteger.TEN.pow(19), 1));
        assertEquals("1.00D", ExactAmountFormatter.compact(BigInteger.TEN.pow(33), 1));
    }

    @Test
    void amountsBeyondTheLargestSuffixNeverUseScientificNotation() {
        String compact = ExactAmountFormatter.compact(BigInteger.TEN.pow(40), 1);
        assertEquals("10000000D", compact);
        assertFalse(compact.contains("e"));
    }
}
