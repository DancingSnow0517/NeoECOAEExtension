package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ECOCraftingCPULogicTest {
    @Test
    void batchBudgetTracksTheWholeTick() {
        var budget = new ECOCraftingCPULogic.FastPathBatchBudget(5632);

        budget.consume(512);
        budget.consume(4608);

        assertEquals(512, budget.remaining());
        assertThrows(IllegalArgumentException.class, () -> budget.consume(513));
        assertEquals(512, budget.remaining());
    }

    @Test
    void batchBudgetIsIndependentFromSlowOperationBudget() {
        assertEquals(5632, ECOCraftingCPULogic.DEFAULT_BATCH_FAST_PATH_LIMIT);
        assertEquals(5632, ECOCraftingCPULogic.DEFAULT_BATCH_FAST_PATH_TICK_LIMIT);
        assertEquals(5632, ECOCraftingCPULogic.totalPatternBudget(64, 5632));
    }
}
