package cn.dancingsnow.neoecoae.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NEConfigTest {
    private final int originalPages = NEConfig.craftingPatternBusPages;

    @AfterEach
    void restorePages() {
        NEConfig.craftingPatternBusPages = originalPages;
    }

    @Test
    void defaultsToTwoPagesAnd126Slots() {
        assertEquals(2, NEConfig.getCraftingPatternBusPages());
        assertEquals(126, NEConfig.getCraftingPatternBusSlotCount());
    }

    @Test
    void supportsConfiguredPageCounts() {
        NEConfig.craftingPatternBusPages = 1;
        assertEquals(1, NEConfig.getCraftingPatternBusPages());
        assertEquals(63, NEConfig.getCraftingPatternBusSlotCount());

        NEConfig.craftingPatternBusPages = 2;
        assertEquals(2, NEConfig.getCraftingPatternBusPages());
        assertEquals(126, NEConfig.getCraftingPatternBusSlotCount());

        NEConfig.craftingPatternBusPages = 4;
        assertEquals(4, NEConfig.getCraftingPatternBusPages());
        assertEquals(252, NEConfig.getCraftingPatternBusSlotCount());
    }
}
