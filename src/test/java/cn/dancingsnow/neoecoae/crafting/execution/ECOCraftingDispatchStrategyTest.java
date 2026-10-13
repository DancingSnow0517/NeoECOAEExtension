package cn.dancingsnow.neoecoae.crafting.execution;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ECOCraftingDispatchStrategyTest {
    @Test
    void fallbackGets64EveryTickWithoutCoProcessors() {
        var strategy = new ECOCraftingDispatchStrategy();
        for (int tick = 0; tick < 8; tick++) {
            assertEquals(64, strategy.beginTick());
            strategy.finishTick(64);
        }
        assertEquals(64, strategy.beginTick());
    }
}
