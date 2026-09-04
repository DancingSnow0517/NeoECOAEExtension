package cn.dancingsnow.neoecoae.integration.ae2omnicells;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ECOUniversalStorageCellTest {
    @Test
    void calculatesBytesPerAmountBucket() {
        Long2LongOpenHashMap buckets = new Long2LongOpenHashMap();
        buckets.put(64L, 65L);
        buckets.put(1L, 5L);

        assertEquals(7L, ECOUniversalStorageCell.calculateUsedBytes(buckets));
    }

    @Test
    void includesPartialBucketWhenCalculatingRemainingCapacity() {
        assertEquals(
            6_335L,
            ECOUniversalStorageCell.calculateRemainingAmount(100L, 2L, 1L, 64L));
        assertEquals(
            6_272L,
            ECOUniversalStorageCell.calculateRemainingAmount(100L, 2L, 0L, 64L));
    }

    @Test
    void prefersCellUuidComponentAndKeepsLegacyFallback() {
        UUID componentId = UUID.randomUUID();
        UUID legacyId = UUID.randomUUID();
        var legacy = new net.minecraft.nbt.CompoundTag();
        legacy.putString("ae_universal_cell_uuid", legacyId.toString());

        assertEquals(componentId, ECOUniversalCellHandler.resolveStorageId(componentId, legacy));
        assertEquals(legacyId, ECOUniversalCellHandler.resolveStorageId(null, legacy));
        assertNull(ECOUniversalCellHandler.resolveStorageId(null, new net.minecraft.nbt.CompoundTag()));
    }

}
