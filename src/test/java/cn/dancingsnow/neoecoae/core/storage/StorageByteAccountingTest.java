package cn.dancingsnow.neoecoae.core.storage;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageByteAccountingTest {
    @Test void primitiveAccountingMatchesExactArithmeticWithSaturation() {
        Random random = new Random(40815);
        for (int i = 0; i < 2000; i++) {
            long amount = i % 17 == 0 ? Long.MAX_VALUE : random.nextLong(Long.MAX_VALUE);
            long types = random.nextInt(316);
            long perByte = i % 13 == 0 ? 0 : random.nextInt(8001);
            long typeBytes = i % 19 == 0 ? Long.MAX_VALUE : random.nextInt(1_000_000);
            BigInteger exact = StorageByteAccounting.usedBytes(types, BigInteger.valueOf(amount), perByte, typeBytes);
            assertEquals(exact.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact(),
                StorageByteAccounting.usedBytes(types, amount, perByte, typeBytes));
        }
    }

    @Test void ordinaryOverCapacityCellsKeepOnlyTheirAlreadyAllocatedPartialByte() {
        assertEquals(7, StorageByteAccounting.remainingCellAmount(10, 11, 9, 8));
        assertEquals(0, StorageByteAccounting.remainingInsertAmount(10, 11, 9, 8));
        assertEquals(7, StorageByteAccounting.migrationCapacity(10, 315, 1, 8, 9, 1, 81));
        assertEquals(0, StorageByteAccounting.migrationCapacity(10, 315, 1, 8, 0, 1, 81));
    }

    @Test void migrationHonorsTypeOverheadPartialBytesAndLongHeadroom() {
        assertEquals(0, StorageByteAccounting.migrationCapacity(1, 315, 1, 8, 0, 0, 0));
        assertEquals(7, StorageByteAccounting.migrationCapacity(3, 315, 1, 8, 0, 1, 1));
        assertEquals(0, StorageByteAccounting.migrationCapacity(100, 1, 1, 8, 0, 1, 1));
        assertEquals(0, StorageByteAccounting.migrationCapacity(Long.MAX_VALUE, 315, 1, 8,
            Long.MAX_VALUE, 1, Long.MAX_VALUE));
    }
}
