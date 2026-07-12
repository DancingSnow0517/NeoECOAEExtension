package cn.dancingsnow.neoecoae.gui.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoragePriorityTest {

    @Test
    void addsPositiveStep() {
        assertEquals(101, StoragePriority.adjust(1, 100));
    }

    @Test
    void addsNegativeStep() {
        assertEquals(-99, StoragePriority.adjust(1, -100));
    }

    @Test
    void clampsPositiveOverflow() {
        assertEquals(Integer.MAX_VALUE, StoragePriority.adjust(Integer.MAX_VALUE - 5, 1000));
    }

    @Test
    void clampsNegativeOverflow() {
        assertEquals(Integer.MIN_VALUE, StoragePriority.adjust(Integer.MIN_VALUE + 5, -1000));
    }
}
