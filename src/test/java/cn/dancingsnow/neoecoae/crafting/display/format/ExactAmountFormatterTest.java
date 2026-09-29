package cn.dancingsnow.neoecoae.crafting.display.format;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
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
        assertEquals("1K", ExactAmountFormatter.compact(BigInteger.valueOf(1000), 1));
        assertEquals("12.3K", ExactAmountFormatter.compact(BigInteger.valueOf(12345), 1));
        assertEquals("37.9E", ExactAmountFormatter.compact(new BigInteger("37900000000000000000"), 1));
        assertEquals("92.1Y", ExactAmountFormatter.compact(new BigInteger("92100000000000000000000000"), 1));
        assertEquals("10E", ExactAmountFormatter.compact(BigInteger.TEN.pow(19), 1));
        assertEquals("1D", ExactAmountFormatter.compact(BigInteger.TEN.pow(33), 1));
    }

    @Test
    void amountsBeyondTheLargestSuffixKeepOnlyThreeSignificantDigits() {
        String compact = ExactAmountFormatter.compact(BigInteger.TEN.pow(40), 1);
        assertEquals("10×10^39", compact);
    }

    @Test
    void infiniteMarkerRendersAsInfinityInBothTerminalLocations() {
        assertEquals("∞", ExactAmountFormatter.slot(ExactAmount.unbounded()));
        assertEquals("∞", ExactAmountFormatter.full(ExactAmount.unbounded()));
        assertEquals("9.22E", ExactAmountFormatter.slot(ExactAmount.finite(BigInteger.valueOf(Long.MAX_VALUE))));
    }
}
