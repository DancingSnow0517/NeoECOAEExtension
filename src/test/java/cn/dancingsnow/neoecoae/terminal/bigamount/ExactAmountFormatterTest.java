package cn.dancingsnow.neoecoae.terminal.bigamount;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ExactAmountFormatterTest {
    @Test void formatsBeyondLongWithoutNarrowing() {
        assertEquals("12.3E", ExactAmountFormatter.slot(
            ExactAmount.finite(new BigInteger("12345678901234567890"))));
    }

    @Test void limitsCompactDisplayToThreeSignificantDigits() {
        assertEquals("1.23K", ExactAmountFormatter.slot(ExactAmount.finite(BigInteger.valueOf(1234))));
        assertEquals("12.3K", ExactAmountFormatter.slot(ExactAmount.finite(BigInteger.valueOf(12345))));
        assertEquals("123K", ExactAmountFormatter.slot(ExactAmount.finite(BigInteger.valueOf(123456))));
    }

    @Test void formatsUnboundedSeparatelyFromFiniteBigInteger() {
        assertEquals("∞", ExactAmountFormatter.slot(ExactAmount.unbounded()));
    }

    @Test void continuesPastQWithoutLosingSignificantDigits() {
        int[] exponents = {30, 33, 36, 57, 60, 63, 90, 93};
        String[] units = {"Q", "KQ", "MQ", "RQ", "QQ", "KQQ", "QQQ", "KQQQ"};
        for (int i = 0; i < exponents.length; i++) {
            BigInteger unit = BigInteger.TEN.pow(exponents[i]);
            assertEquals("1" + units[i], ExactAmountFormatter.slot(ExactAmount.finite(unit)));
            assertEquals("12.3" + units[i], ExactAmountFormatter.slot(
                ExactAmount.finite(unit.multiply(BigInteger.valueOf(12345)).divide(BigInteger.valueOf(1000)))));
        }
        assertEquals("999Q", ExactAmountFormatter.slot(
            ExactAmount.finite(BigInteger.TEN.pow(33).subtract(BigInteger.ONE))));
    }
}
