package cn.dancingsnow.neoecoae.terminal.bigamount;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ExactAmountFormatterTest {
    @Test void formatsBeyondLongWithoutNarrowing() {
        assertEquals("12.34E", ExactAmountFormatter.slot(
            ExactAmount.finite(new BigInteger("12345678901234567890"))));
    }

    @Test void formatsUnboundedSeparatelyFromFiniteBigInteger() {
        assertEquals("∞", ExactAmountFormatter.slot(ExactAmount.unbounded()));
    }

    @Test void fallsBackToScientificNotationPastNamedUnits() {
        assertEquals("1e36", ExactAmountFormatter.slot(
            ExactAmount.finite(BigInteger.TEN.pow(36))));
    }
}
