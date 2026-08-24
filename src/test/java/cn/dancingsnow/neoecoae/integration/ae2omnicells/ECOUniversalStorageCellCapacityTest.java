package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.api.storage.IBatchedECOCellSaveProvider;
import appeng.api.storage.cells.ISaveProvider;
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

    @Test
    void onlyAnEmptyUniversalCellCanFitInsideAnotherCell() {
        assertTrue(ECOUniversalStorageCell.isEmptyStorage(0L));
        assertFalse(ECOUniversalStorageCell.isEmptyStorage(1L));
    }

    @Test
    void rewrittenUniversalCellIdIsRejected() {
        assertTrue(ECOUniversalCellHandler.isStorageIdStable("", "generated"));
        assertTrue(ECOUniversalCellHandler.isStorageIdStable("same", "same"));
        assertFalse(ECOUniversalCellHandler.isStorageIdStable("original", "generated"));
    }

    @Test
    void removedBatchedHostCanReleaseItsRuntimeClaim() {
        ISaveProvider removedHost = new IBatchedECOCellSaveProvider() {
            @Override
            public boolean isHostRemoved() {
                return true;
            }

            @Override
            public void saveChanges() {}
        };

        assertTrue(ECOUniversalCellHandler.isDeadHost(removedHost));
    }

    private static Long2LongOpenHashMap buckets(long... values) {
        Long2LongOpenHashMap result = new Long2LongOpenHashMap();
        for (int i = 0; i < values.length; i += 2) {
            result.put(values[i], values[i + 1]);
        }
        return result;
    }
}
