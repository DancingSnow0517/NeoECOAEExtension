package cn.dancingsnow.neoecoae.crafting.execution;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ECOCraftingDispatchStrategyTest {
    @Test
    void fallbackGets64EveryTickWithoutCoProcessors() {
        var strategy = new ECOCraftingDispatchStrategy();
        for (int tick = 0; tick < 8; tick++) {
            assertEquals(64, strategy.beginTick(0, 200_000));
            strategy.finishTick(64);
        }
        assertEquals(64, strategy.beginTick(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(12, strategy.beginTick(0, 12));
        assertEquals(0, strategy.beginTick(0, 0));
    }
}
