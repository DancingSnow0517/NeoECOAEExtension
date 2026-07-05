package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOStorageCellCapacityTest {
    @Test
    void equalDistributionCapacitySaturatesReservedTypeBytes() {
        long maxItemsPerType = ECOStorageCell.calculateEqualDistributionMaxItems(1024L, Integer.MAX_VALUE, 8, 8L);

        assertEquals(0L, maxItemsPerType);
    }

    @Test
    void equalDistributionCapacitySaturatesRemainingItemCapacity() {
        long maxItemsPerType =
                ECOStorageCell.calculateEqualDistributionMaxItems(Long.MAX_VALUE, 1, Integer.MAX_VALUE, 1L);

        assertEquals(Long.MAX_VALUE, maxItemsPerType);
    }

    @Test
    void equalDistributionCapacityRoundsUpPerDistributedType() {
        long maxItemsPerType = ECOStorageCell.calculateEqualDistributionMaxItems(11L, 1, 1, 4L);

        assertEquals(2L, maxItemsPerType);
    }
}
