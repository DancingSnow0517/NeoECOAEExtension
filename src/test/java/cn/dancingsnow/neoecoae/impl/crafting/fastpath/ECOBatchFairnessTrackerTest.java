package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ECOBatchFairnessTrackerTest {
    @Test
    void acceptedJobWaitsUntilItsBatchCompletes() {
        ECOBatchFairnessTracker tracker = new ECOBatchFairnessTracker();
        UUID jobId = UUID.randomUUID();

        tracker.setEnabled(true);
        tracker.noteAccepted(jobId);

        assertTrue(tracker.shouldDefer(jobId));
        tracker.noteCompleted(jobId);
        assertFalse(tracker.shouldDefer(jobId));
    }

    @Test
    void disablingClearsInFlightJobs() {
        ECOBatchFairnessTracker tracker = new ECOBatchFairnessTracker();
        UUID jobId = UUID.randomUUID();

        tracker.setEnabled(true);
        tracker.noteAccepted(jobId);
        tracker.setEnabled(false);

        assertFalse(tracker.shouldDefer(jobId));
    }
}
