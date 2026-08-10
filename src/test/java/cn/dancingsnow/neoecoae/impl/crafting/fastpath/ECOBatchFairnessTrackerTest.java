package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.config.NEConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ECOBatchFairnessTrackerTest {
    @Test
    void jobDefersUntilItsAcceptedBatchCompletes() {
        boolean previous = NEConfig.debugEcoBatchFairScheduling;
        int previousBatchSize = NEConfig.debugEcoBatchFairSchedulingBatchSize;
        NEConfig.debugEcoBatchFairScheduling = true;
        NEConfig.debugEcoBatchFairSchedulingBatchSize = 32;
        try {
            var tracker = new ECOBatchFairnessTracker();
            UUID largeJob = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID smallJob = UUID.fromString("00000000-0000-0000-0000-000000000002");

            tracker.noteAccepted(largeJob, 10L);
            assertTrue(tracker.shouldDefer(largeJob, 11L));
            assertFalse(tracker.shouldDefer(smallJob, 11L));
            assertTrue(tracker.capBatchSize(5_632L) == 32L);

            tracker.noteCompleted(largeJob);
            assertFalse(tracker.shouldDefer(largeJob, 12L));

            tracker.noteAccepted(largeJob, 13L);
            NEConfig.debugEcoBatchFairScheduling = false;
            assertFalse(tracker.shouldDefer(largeJob, 14L));
            NEConfig.debugEcoBatchFairScheduling = true;
            assertFalse(tracker.shouldDefer(largeJob, 15L));
        } finally {
            NEConfig.debugEcoBatchFairScheduling = previous;
            NEConfig.debugEcoBatchFairSchedulingBatchSize = previousBatchSize;
        }
    }
}
