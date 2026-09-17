package cn.dancingsnow.neoecoae.gui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class HostTextTest {
    @Test
    void prefersExactCraftingBytesOverSaturatedAe2Value() {
        BigInteger exact = BigInteger.TEN.pow(22);

        assertEquals(exact, HostText.craftingPlanBytes(exact, Long.MAX_VALUE));
        assertEquals("10Z", HostText.ae2Amount(exact));
    }

    @Test
    void formatsExactMaterialScaleBeyondLongRange() {
        assertEquals("100E", HostText.ae2Amount(BigInteger.TEN.pow(20)));
    }

    @Test
    void fallsBackToTheAe2ReportedBytesWhenExactValueIsAbsent() {
        assertEquals(BigInteger.valueOf(4096L), HostText.craftingPlanBytes(BigInteger.ZERO, 4096L));
    }
}
