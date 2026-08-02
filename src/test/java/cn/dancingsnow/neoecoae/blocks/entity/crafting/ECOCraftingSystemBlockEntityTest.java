package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOCraftingSystemBlockEntityTest {
    @Test
    void laneCapacityScalesBatchSizeWithoutChangingLogicalSlotCount() {
        assertEquals(32, ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(32, 4, false, 1));
        assertEquals(64, ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(32, 4, false, 2));
        assertEquals(256, ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(32, 4, true, 2));
    }

    @Test
    void laneCapacitySaturatesAndRejectsNegativeInputs() {
        assertEquals(
                Integer.MAX_VALUE,
                ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(
                        Integer.MAX_VALUE, Integer.MAX_VALUE, true, Integer.MAX_VALUE));
        assertEquals(0, ECOCraftingSystemBlockEntity.calculateWorkerBatchCapacity(-1, 4, true, 2));
    }
}
