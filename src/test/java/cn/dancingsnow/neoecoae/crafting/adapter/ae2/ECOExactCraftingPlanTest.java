package cn.dancingsnow.neoecoae.crafting.adapter.ae2;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ECOExactCraftingPlanTest {
    @Test
    void positiveAmountsSaturateAtTheAe2LongBoundary() {
        assertEquals(0L, ECOExactCraftingPlan.bounded(BigInteger.ZERO));
        assertEquals(42L, ECOExactCraftingPlan.bounded(BigInteger.valueOf(42)));
        assertEquals(Long.MAX_VALUE, ECOExactCraftingPlan.bounded(BigInteger.valueOf(Long.MAX_VALUE)));
        assertEquals(Long.MAX_VALUE, ECOExactCraftingPlan.bounded(BigInteger.TEN.pow(100)));
    }

    @Test
    void signedAmountsKeepTheExistingPublicContract() {
        assertEquals(-42L, ECOExactCraftingPlan.bounded(BigInteger.valueOf(-42)));
        assertEquals(Long.MIN_VALUE, ECOExactCraftingPlan.bounded(BigInteger.valueOf(Long.MIN_VALUE)));
        assertThrows(ArithmeticException.class,
            () -> ECOExactCraftingPlan.bounded(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)));
        assertThrows(NullPointerException.class, () -> ECOExactCraftingPlan.bounded(null));
    }
}
