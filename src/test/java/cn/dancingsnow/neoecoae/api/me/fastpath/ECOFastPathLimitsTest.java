package cn.dancingsnow.neoecoae.api.me.fastpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ECOFastPathLimitsTest {
    @Test
    void batchSizeIsLimitedByControllerAndWorkerCapacity() {
        assertEquals(4, ECOFastPathLimits.limitBatchSize(64, 32, 4));
        assertEquals(8, ECOFastPathLimits.limitBatchSize(64, 8, 32));
        assertEquals(6, ECOFastPathLimits.limitBatchSize(6, 32, 32));
        assertEquals(0, ECOFastPathLimits.limitBatchSize(64, 0, 32));
    }

    @Test
    void finalAcceptanceRequiresAllCapacityChecksToPass() {
        assertTrue(ECOFastPathLimits.canAcceptBatch(8, 8, 8));
        assertFalse(ECOFastPathLimits.canAcceptBatch(8, 7, 8));
        assertFalse(ECOFastPathLimits.canAcceptBatch(8, 8, 7));
    }
}
