package cn.dancingsnow.neoecoae.crafting.display.format;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayNumbersTest {
    @Test
    void fullValuesKeepExactDigitsAndFractions() {
        assertEquals("9,223,372,036,854,775,807", DisplayNumbers.grouped(Long.MAX_VALUE));
        assertEquals("-9,223,372,036,854,775,808", DisplayNumbers.grouped(Long.MIN_VALUE));
        assertEquals("12,345,678,901,234,567,890", DisplayNumbers.grouped(new BigInteger("12345678901234567890")));
        assertEquals("-1,234,567.8901", DisplayNumbers.grouped(new BigDecimal("-1234567.8901")));
        assertEquals("+1,234,567.50 FE/t", DisplayNumbers.grouped("+1234567.50 FE/t"));
        assertEquals("1,234.5 AE", DisplayNumbers.grouped("1,234.5 AE"));
        assertEquals("∞", DisplayNumbers.grouped("∞"));
    }

    @Test
    void compactValuesShareUnitsAndStayWithinThreeSignificantDigits() {
        assertEquals("138G", DisplayNumbers.compact(138_936_320_000D));
        assertEquals("16M", DisplayNumbers.compact(16_000_000L));
        assertEquals("999", DisplayNumbers.compact(999));
        assertEquals("1K", DisplayNumbers.compact(1000));
        assertEquals("999K", DisplayNumbers.compact(999_999));
        assertEquals("1M", DisplayNumbers.compact(1_000_000));
        assertEquals("9.22E", DisplayNumbers.compact(Long.MAX_VALUE));
        assertEquals("-9.22E", DisplayNumbers.compact(Long.MIN_VALUE));
        assertEquals("12.3E", DisplayNumbers.compact(new BigInteger("12345678901234567890")));
        assertEquals("1.23K", DisplayNumbers.compact(new BigDecimal("1234.567")));
        assertEquals("1.2K", DisplayNumbers.compact(1234, 1));
        assertEquals("1K", DisplayNumbers.compact(1234, 0));
        assertEquals("0.000001", DisplayNumbers.compact(new BigDecimal("0.000001")));
        assertEquals("1QQ", DisplayNumbers.compact(BigInteger.TEN.pow(60)));
        assertEquals("1×10^63", DisplayNumbers.compact(BigInteger.TEN.pow(63)));
    }

    @Test
    void decimalDisplaysGroupIntegerDigitsAndKeepTheirRequiredPrecision() {
        assertEquals("1,234,567.50", DisplayNumbers.decimal(1_234_567.5, 2));
        assertEquals("0.0", DisplayNumbers.decimal(0, 1));
        assertEquals("+0.00", DisplayNumbers.signedDecimal(-0.001, 2));
        assertEquals("-1,234.57", DisplayNumbers.signedDecimal(-1234.567, 2));
        assertEquals("0", DisplayNumbers.compact(Double.NaN));
        assertEquals("0.00", DisplayNumbers.decimal(Double.POSITIVE_INFINITY, 2));
    }

    @Test
    void systemLocaleDoesNotChangeSeparators() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("1,234.50", DisplayNumbers.decimal(1234.5, 2));
            assertEquals("1.23K", DisplayNumbers.compact(1234));
        } finally {
            Locale.setDefault(original);
        }
    }
}
