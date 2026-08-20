package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.junit.jupiter.api.Test;

class ECOUniversalStorageCellCapacityTest {
    @Test
    void usedBytesAreCeiledPerAmountPerByteBucket() {
        Long2LongOpenHashMap buckets = buckets(4L, 5L, 1L, 3L);

        assertEquals(5L, ECOUniversalStorageCell.calculateUsedBytes(buckets));
    }

    @Test
    void anAggregatedBucketConsumesOneCombinedBucket() {
        Long2LongOpenHashMap buckets = buckets(4L, 7L);

        assertEquals(2L, ECOUniversalStorageCell.calculateUsedBytes(buckets));
    }

    @Test
    void remainingAmountIncludesThePartialTargetBucket() {
        assertEquals(29L, ECOUniversalStorageCell.calculateRemainingAmount(10L, 3L, 7L, 4L));
    }

    @Test
    void anAlreadyOverCapacityCellAcceptsNothing() {
        assertEquals(0L, ECOUniversalStorageCell.calculateRemainingAmount(10L, 11L, 3L, 4L));
    }

    private static Long2LongOpenHashMap buckets(long... values) {
        Long2LongOpenHashMap result = new Long2LongOpenHashMap();
        for (int i = 0; i < values.length; i += 2) {
            result.put(values[i], values[i + 1]);
        }
        return result;
    }
}
