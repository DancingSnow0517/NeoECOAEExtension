package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOCraftingPatternBusBlockEntityTest {
    @Test
    void oneAvailableHostLaneDoesNotCapBatchToOneCraft() {
        assertEquals(128, ECOCraftingPatternBusBlockEntity.calculateBatchOfferSize(256, 128, 192));
        assertEquals(128, ECOCraftingPatternBusBlockEntity.calculateBatchOfferSize(256, 128, 1));
        assertEquals(0, ECOCraftingPatternBusBlockEntity.calculateBatchOfferSize(256, 0, 192));
        assertEquals(0, ECOCraftingPatternBusBlockEntity.calculateBatchOfferSize(256, 128, 0));
        assertEquals(0, ECOCraftingPatternBusBlockEntity.calculateBatchOfferSize(-1, 128, 192));
    }
}
