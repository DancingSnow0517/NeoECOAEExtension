package cn.dancingsnow.neoecoae.crafting.execution.fastpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ECODurabilityBatchModelTest {
    @Test
    void batchMayIncludeTheCraftThatBreaksTheTool() {
        assertEquals(3, ECODurabilityBatchModel.maxCraftsBeforeBreak(90, 4, 100));
        assertTrue(ECODurabilityBatchModel.calculateFinalDamage(
            90, 4, 100, ECODurabilityBatchModel.BreakBehavior.DISAPPEAR, 3).isEmpty());
    }
}
