package cn.dancingsnow.neoecoae.gui.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StoragePriorityTest {
    @Test
    void largeAdjustmentsSaturateInsteadOfWrapping() {
        assertEquals(Integer.MAX_VALUE, StoragePriority.adjust(Integer.MAX_VALUE - 1, 1000));
        assertEquals(Integer.MIN_VALUE, StoragePriority.adjust(Integer.MIN_VALUE + 1, -1000));
        assertEquals(-999, StoragePriority.adjust(1, -1000));
    }
}
