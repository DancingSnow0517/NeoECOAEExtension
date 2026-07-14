package cn.dancingsnow.neoecoae.blocks.entity.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOStorageSystemBlockEntityTest {
    @Test
    void migrationProgressIsBounded() {
        assertEquals(0, ECOStorageSystemBlockEntity.migrationProgressPercent(-1, 16));
        assertEquals(50, ECOStorageSystemBlockEntity.migrationProgressPercent(8, 16));
        assertEquals(100, ECOStorageSystemBlockEntity.migrationProgressPercent(16, 16));
        assertEquals(100, ECOStorageSystemBlockEntity.migrationProgressPercent(20, 16));
        assertEquals(0, ECOStorageSystemBlockEntity.migrationProgressPercent(1, 0));
    }
}
