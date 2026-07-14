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

    @Test
    void computationTaskTooltipUsesCompactMetricsAndPreciseProgress() {
        assertEquals("12.67K", NELDLibText.compactMetric(12_670L));
        assertEquals("14.49%", NELDLibText.precisePercentOrNA(14_490L, 100_000L));
        assertEquals("N/A", NELDLibText.precisePercentOrNA(0L, 0L));
    }
}
