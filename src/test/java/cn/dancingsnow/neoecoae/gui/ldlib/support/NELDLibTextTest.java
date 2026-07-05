package cn.dancingsnow.neoecoae.gui.ldlib.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class NELDLibTextTest {
    @Test
    void preciseHugeAmountUsesMoreDigitsThanPanelSummary() {
        BigInteger amount =
                BigInteger.valueOf(35L).multiply(BigInteger.valueOf(1024L).pow(6));

        assertEquals("35.00E", NELDLibText.hugeAmount(amount.toString()));
        assertEquals("36,700,160.00T", NELDLibText.preciseHugeAmount(amount.toString()));
    }
}
