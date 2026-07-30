package cn.dancingsnow.neoecoae.impl.storage.infinite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class WideAmountTest {
    @Test
    void keepsExactValuePastLongMaximum() {
        WideAmount amount = WideAmount.of(Long.MAX_VALUE);

        amount.add(1L);

        assertFalse(amount.fitsLong());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), amount.toBigInteger());
    }

    @Test
    void demotesThroughExactSubtractionBoundary() {
        WideAmount amount = WideAmount.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(4L)));

        amount.subtract(4L);

        assertTrue(amount.fitsLong());
        assertEquals(Long.MAX_VALUE, amount.toLongExact());
    }

    @Test
    void hybridStorePromotesAndDemotesWithoutLosingQuantity() {
        HybridAmountStore<String> amounts = new HybridAmountStore<>();
        amounts.add("test", Long.MAX_VALUE);
        amounts.add("test", 1L);

        assertTrue(amounts.isWide("test"));
        assertEquals(
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                amounts.get("test").toBigInteger());

        assertEquals(1L, amounts.subtractAtMost("test", 1L));
        assertFalse(amounts.isWide("test"));
        assertEquals(HugeAmount.of(Long.MAX_VALUE), amounts.get("test"));
    }
}
