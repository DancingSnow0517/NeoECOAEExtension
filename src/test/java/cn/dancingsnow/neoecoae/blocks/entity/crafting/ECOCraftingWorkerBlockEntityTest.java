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

    @Test
    void proportionalOutputAllocationDoesNotOverflowLongCounters() {
        assertEquals(
                Long.MAX_VALUE / 2L,
                ECOCraftingWorkerBlockEntity.proportionalShare(Long.MAX_VALUE, Long.MAX_VALUE / 2L, Long.MAX_VALUE));
    }
}
