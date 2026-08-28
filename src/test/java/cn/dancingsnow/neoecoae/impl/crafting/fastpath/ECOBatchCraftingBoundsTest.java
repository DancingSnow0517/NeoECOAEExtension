package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ECOBatchCraftingBoundsTest {
    @Test
    void requestAccepts4096AndRejects4097() {
        assertDoesNotThrow(() -> ECOBatchCraftingHelper.validateBatchSize(4096));
        assertThrows(
            IllegalArgumentException.class,
            () -> ECOBatchCraftingHelper.validateBatchSize(4097)
        );
    }

    @Test
    void corruptedPersistedBatchSizeIsClamped() {
        assertEquals(1, ECOBatchCraftingHelper.clampPersistedBatchSize(-1));
        assertEquals(4096, ECOBatchCraftingHelper.clampPersistedBatchSize(999_999));
    }

    @Test
    void stackAmountLimitIsIndependentFromBatchSize() {
        assertEquals(1L << 42, ECOBatchCraftingHelper.MAX_BATCH_STACK_AMOUNT);
    }
}
