package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ECOBatchLimitsTest {
    @Test
    void affordableBatchUsesLongCountsAndRespectsAvailableEnergy() {
        long requested = (long) Integer.MAX_VALUE + 1000;
        assertEquals(requested, ECOBatchCraftingHelper.maxAffordableCrafts(2, requested, amount -> amount));
        assertEquals(7, ECOBatchCraftingHelper.maxAffordableCrafts(2, requested, amount -> Math.min(15, amount)));
        assertEquals(0, ECOBatchCraftingHelper.maxAffordableCrafts(Double.NaN, requested, amount -> amount));
        assertEquals(0, ECOBatchCraftingHelper.maxAffordableCrafts(2, requested, amount -> 0));
    }

    @Test
    void perEntryLimitPreventsOverflowWithoutImposingAnIntBatchLimit() {
        assertEquals(1L << 42, ECOBatchCraftingHelper.maxBatchSizeForAmount(1));
        assertEquals(0, ECOBatchCraftingHelper.maxBatchSizeForAmount(Long.MAX_VALUE));
        assertEquals(0, ECOBatchCraftingHelper.maxBatchSizeForAmount(0));
    }
}
