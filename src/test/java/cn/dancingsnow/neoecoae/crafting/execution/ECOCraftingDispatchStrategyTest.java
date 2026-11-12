package cn.dancingsnow.neoecoae.crafting.execution;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ECOCraftingDispatchStrategyTest {
    @Test
    void ordinaryBudgetRespectsTheConfiguredLimitOnEveryTick() {
        var strategy = new ECOCraftingDispatchStrategy();
        for (int tick = 0; tick < 8; tick++) {
            assertEquals(64, strategy.beginTick(200_000));
        }
        assertEquals(64, strategy.beginTick(Integer.MAX_VALUE));
        assertEquals(12, strategy.beginTick(12));
        assertEquals(0, strategy.beginTick(0));
        assertEquals(0, strategy.beginTick(-1));
    }
}
