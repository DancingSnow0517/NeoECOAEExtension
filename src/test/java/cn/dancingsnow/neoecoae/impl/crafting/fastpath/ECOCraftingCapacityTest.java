package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOCraftingCapacityTest {
    @Test
    void f9LengthElevenIsCappedAt5632InFlightCrafts() {
        int structureLength = 11;
        int threadCount = 22 * (256 + 384);
        int threadCountPerWorker = 32 * 16;

        assertEquals(5632, ECOCraftingCapacity.maxInFlightCrafts(threadCount, structureLength, threadCountPerWorker));
    }

    @Test
    void parallelCoreLimitCanBeLowerThanWorkerQueueLimit() {
        int structureLength = 11;
        int threadCount = 22 * (24 + 32);
        int threadCountPerWorker = 32 * 4;

        assertEquals(1232, ECOCraftingCapacity.maxInFlightCrafts(threadCount, structureLength, threadCountPerWorker));
    }

    @Test
    void availableSlotsUseTheExplicitInFlightLimit() {
        assertEquals(32, ECOCraftingCapacity.availableCraftSlots(5632, 5600));
        assertEquals(0, ECOCraftingCapacity.availableCraftSlots(5632, 6000));
    }
}
