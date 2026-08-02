package cn.dancingsnow.neoecoae.multiblock.cluster;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NEComputationClusterTest {
    @Test
    void standaloneSubmissionStillRequiresLocalCapacity() {
        assertFalse(NEComputationCluster.hasSufficientSubmissionCapacity(false, 99L, 100L));
        assertTrue(NEComputationCluster.hasSufficientSubmissionCapacity(false, 100L, 100L));
    }

    @Test
    void networkSubmissionUsesAggregateCapacityInsteadOfSelectedHostCapacity() {
        assertTrue(NEComputationCluster.hasSufficientSubmissionCapacity(true, 0L, 100L));
    }
}
