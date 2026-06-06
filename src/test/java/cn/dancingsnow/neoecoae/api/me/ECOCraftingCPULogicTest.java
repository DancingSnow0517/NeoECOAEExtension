package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ECOCraftingCPULogicTest {
    @Test
    void batchBudgetTracksTheWholeTick() {
        var budget = new ECOCraftingCPULogic.FastPathBatchBudget(256);

        budget.consume(64);
        budget.consume(128);

        assertEquals(64, budget.remaining());
        assertThrows(IllegalArgumentException.class, () -> budget.consume(65));
        assertEquals(64, budget.remaining());
    }
}
