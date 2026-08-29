package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ECOBatchCraftingBoundsTest {
    @Test
    void batchSizeHasNoFixedCeiling() {
        assertDoesNotThrow(() -> ECOBatchCraftingHelper.validateBatchSize(1));
        assertDoesNotThrow(() -> ECOBatchCraftingHelper.validateBatchSize(4097));
        assertDoesNotThrow(() -> ECOBatchCraftingHelper.validateBatchSize(1_000_000));
        assertDoesNotThrow(() -> ECOBatchCraftingHelper.validateBatchSize(Integer.MAX_VALUE));
    }

    @Test
    void nonPositiveBatchSizeIsStillRejected() {
        assertThrows(IllegalArgumentException.class, () -> ECOBatchCraftingHelper.validateBatchSize(0));
        assertThrows(IllegalArgumentException.class, () -> ECOBatchCraftingHelper.validateBatchSize(-1));
    }

    @Test
    void perCraftAmountDerivesTheOnlyRemainingCeiling() {
        assertEquals(
            Integer.MAX_VALUE,
            ECOBatchCraftingHelper.maxBatchSizeForAmount(1L)
        );
        // 2^42 / 2^20 = 2^22, the first quotient small enough to be the binding limit rather than int range.
        assertEquals(
            1 << 22,
            ECOBatchCraftingHelper.maxBatchSizeForAmount(1L << 20)
        );
        assertEquals(
            1,
            ECOBatchCraftingHelper.maxBatchSizeForAmount(ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT)
        );
        assertEquals(0, ECOBatchCraftingHelper.maxBatchSizeForAmount(0L));
        assertEquals(0, ECOBatchCraftingHelper.maxBatchSizeForAmount(-5L));
    }

    @Test
    void emptyRecipeAndEmptyTotalsImposeNoCeiling() {
        assertEquals(
            Integer.MAX_VALUE,
            ECOBatchCraftingHelper.maxBatchSizeForPerCraftStacks(java.util.List.of(), java.util.List.of(),
                java.util.List.of())
        );
        assertEquals(Integer.MAX_VALUE, ECOBatchCraftingHelper.maxBatchSizeFromTotals(java.util.List.of()));
    }

    @Test
    void persistedBatchSizeOnlyEnforcesTheLowerBound() {
        assertEquals(1, ECOBatchCraftingHelper.clampPersistedBatchSize(-1));
        assertEquals(1, ECOBatchCraftingHelper.clampPersistedBatchSize(0));
        assertEquals(999_999, ECOBatchCraftingHelper.clampPersistedBatchSize(999_999));
    }

    @Test
    void stackAmountLimitIsTheHardArithmeticCeiling() {
        assertEquals(1L << 42, ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT);
    }
}
