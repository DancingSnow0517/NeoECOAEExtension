package cn.dancingsnow.neoecoae.impl.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class StorageByteAccountingTest {
    @Test
    void exactUsedBytesRoundsPartialBucketAndTypeReservation() {
        assertEquals(BigInteger.valueOf(7), StorageByteAccounting.usedBytes(2, BigInteger.valueOf(17), 4, 1));
    }

    @Test
    void remainingCapacitySaturatesAtLongMax() {
        assertEquals(
                Long.MAX_VALUE,
                StorageByteAccounting.remainingInsertAmount(
                        Long.MAX_VALUE, BigInteger.ZERO, BigInteger.ZERO, Long.MAX_VALUE, 1, false));
    }

    @Test
    void newTypeReservesTypeBytesBeforeContentBytes() {
        assertEquals(
                4L, StorageByteAccounting.remainingInsertAmount(8L, BigInteger.ZERO, BigInteger.ZERO, 1L, 4L, true));
    }

    @Test
    void byteBucketCanBeCompletedAtCapacity() {
        assertEquals(3L, StorageByteAccounting.remainingForCell(1L, 0L, BigInteger.ONE, 1L, 4L, 0L));
    }

    @Test
    void newTypeCannotUseExistingBucketSlack() {
        assertEquals(0L, StorageByteAccounting.remainingForCell(2L, 1L, BigInteger.ONE, 0L, 4L, 1L));
    }
}
