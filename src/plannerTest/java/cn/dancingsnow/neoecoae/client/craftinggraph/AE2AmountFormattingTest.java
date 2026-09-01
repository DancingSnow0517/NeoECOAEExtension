package cn.dancingsnow.neoecoae.client.craftinggraph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.dancingsnow.neoecoae.gui.common.HostText;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class AE2AmountFormattingTest {
    @Test
    void usesAe2StyleSuffixesForPlannerAmounts() {
        assertEquals("9999", HostText.ae2Amount(BigInteger.valueOf(9_999)));
        assertEquals("10K", HostText.ae2Amount(BigInteger.valueOf(10_000)));
        assertEquals("100M", HostText.ae2Amount(BigInteger.valueOf(100_000_000)));
        assertEquals("13M", HostText.ae2Amount(BigInteger.valueOf(13_040_000)));
    }

    @Test
    void continuesAe2StyleBeyondTheRuntimeLongBoundary() {
        assertEquals("100Y", HostText.ae2Amount(BigInteger.TEN.pow(26)));
    }
}
