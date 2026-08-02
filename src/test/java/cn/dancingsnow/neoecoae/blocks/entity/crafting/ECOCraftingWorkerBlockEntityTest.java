package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOCraftingWorkerBlockEntityTest {
    @Test
    void logicalWorkerAvailabilityUsesTheLogicalCapacity() {
        assertEquals(0, ECOCraftingWorkerBlockEntity.availableThreadSlots(32, 32));
        assertEquals(8, ECOCraftingWorkerBlockEntity.availableThreadSlots(32, 24));
        assertEquals(0, ECOCraftingWorkerBlockEntity.availableThreadSlots(32, 40));
    }

    @Test
    void logicalThreadAvailabilityClampsInvalidState() {
        assertEquals(0, ECOCraftingWorkerBlockEntity.availableThreadSlots(-1, -4));
    }
}
