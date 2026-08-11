package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ECOBatchFairnessTrackerTest {
    @Test
    void jobDefersUntilItsAcceptedBatchCompletes() {
        var tracker = new ECOBatchFairnessTracker();
        tracker.setEnabled(true);
        UUID largeJob = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID smallJob = UUID.fromString("00000000-0000-0000-0000-000000000002");

        tracker.noteAccepted(largeJob, 10L);
        assertTrue(tracker.shouldDefer(largeJob, 11L));
        assertFalse(tracker.shouldDefer(smallJob, 11L));
        tracker.noteCompleted(largeJob);
        assertFalse(tracker.shouldDefer(largeJob, 12L));

        tracker.noteAccepted(largeJob, 13L);
        tracker.setEnabled(false);
        assertFalse(tracker.shouldDefer(largeJob, 14L));
        tracker.setEnabled(true);
        assertFalse(tracker.shouldDefer(largeJob, 15L));
    }

    @Test
    void waitingJobsTakeTurnsAfterTheirInFlightBatchCompletes() {
        var tracker = new ECOBatchFairnessTracker();
        tracker.setEnabled(true);
        UUID firstJob = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondJob = UUID.fromString("00000000-0000-0000-0000-000000000002");

        tracker.noteWaiting(firstJob, 10L);
        assertFalse(tracker.shouldDefer(firstJob, 10L));
        tracker.noteAccepted(firstJob, 10L);

        tracker.noteWaiting(firstJob, 11L);
        tracker.noteWaiting(secondJob, 11L);
        assertTrue(tracker.shouldDefer(firstJob, 11L));
        assertFalse(tracker.shouldDefer(secondJob, 11L));
        tracker.noteAccepted(secondJob, 11L);

        tracker.noteCompleted(firstJob);
        assertFalse(tracker.shouldDefer(firstJob, 12L));
        assertTrue(tracker.shouldDefer(secondJob, 12L));
    }
}
