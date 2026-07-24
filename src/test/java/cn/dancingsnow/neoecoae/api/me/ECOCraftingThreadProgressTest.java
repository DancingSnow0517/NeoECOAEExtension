package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOCraftingThreadProgressTest {
    @Test
    void fractionalPowerProgressIsPreserved() {
        ECOCraftingThread.PowerProgress first = ECOCraftingThread.accumulatePoweredProgress(0.75D, 1.0D, 1, 0.0D);
        assertEquals(0, first.completed());
        assertEquals(0.75D, first.remainder(), 1.0E-9D);

        ECOCraftingThread.PowerProgress second =
                ECOCraftingThread.accumulatePoweredProgress(0.5D, 1.0D, 1, first.remainder());
        assertEquals(1, second.completed());
        assertEquals(0.0D, second.remainder(), 1.0E-9D);
    }
}
