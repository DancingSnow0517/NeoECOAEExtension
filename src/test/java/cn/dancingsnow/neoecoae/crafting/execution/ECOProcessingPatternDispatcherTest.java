package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission.StopReason;
import org.junit.jupiter.api.Test;

class ECOProcessingPatternDispatcherTest {
    @Test
    void successfulProbesKeepGrowingAndResumeOnTheNextTick() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        var first = state.beginRun(0, 5);
        assertEquals(1, first.offer(16));
        assertTrue(first.record(1, inserted(1), 0));
        assertEquals(1, state.remembered);

        assertEquals(2, first.offer(15));
        var warm = state.beginRun(0, 5);
        assertEquals(1, warm.offer(16));
        assertTrue(warm.record(1, inserted(1), 0));
        assertEquals(1, warm.offer(15));

        var nextProbe = state.beginRun(1, 5);
        assertEquals(2, nextProbe.offer(16));
        assertTrue(nextProbe.record(2, inserted(2), 1));
        assertEquals(2, state.remembered);
        assertEquals(4, nextProbe.offer(100));
        assertTrue(nextProbe.record(4, inserted(4), 1));
        assertEquals(8, nextProbe.offer(100));
    }

    @Test
    void initialRejectionHalvesWithinVisitAndRecoverySuccessStops() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        state.nextProbeTick = 5;
        var run = state.beginRun(0, 5);
        assertEquals(64, run.offer(100));
        assertTrue(run.record(64, rejected(), 0));
        assertEquals(32, state.remembered);
        assertEquals(32, run.offer(100));
        assertFalse(run.record(32, inserted(32), 0));
        assertEquals(32, state.remembered);
        assertEquals(32, state.beginRun(4, 5).offer(100));
        assertEquals(64, state.beginRun(5, 5).offer(100));
    }

    @Test
    void rejectionAfterGrowthPreservesLastFullChunk() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 8;
        var run = state.beginRun(0, 5);
        assertEquals(8, run.offer(100));
        assertTrue(run.record(8, inserted(8), 0));
        var probe = state.beginRun(5, 5);
        assertEquals(16, probe.offer(100));
        assertTrue(probe.record(16, rejected(), 5));
        assertEquals(8, state.remembered);
        assertEquals(0, probe.owned);
        assertEquals(8, probe.offer(100));
        assertFalse(probe.record(8, inserted(8), 5));
        assertEquals(8, probe.owned);
    }

    @Test
    void tailDoesNotReplaceHistory() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        var tail = state.beginRun(0, 5);
        assertEquals(7L, tail.offer(7L));
        assertTrue(tail.record(7, inserted(7), 0));
        assertEquals(64, state.remembered);
        var warm = state.beginRun(0, 5);
        // A clipped tail does not suppress the next full growth probe.
        assertEquals(128, warm.offer(130));
        state.nextProbeTick = 5;
        warm = state.beginRun(1, 5);
        assertEquals(64, warm.offer(130));
        assertTrue(warm.record(64, inserted(64), 1));
        assertEquals(64, warm.offer(66));
    }

    @Test
    void overflowCountsOwnershipButDoesNotProveCapacity() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        state.nextProbeTick = 5;
        var run = state.beginRun(0, 5);
        assertFalse(run.record(64, ECOBatchAdmission.accepted(64, 0, StopReason.BUFFERED), 0));
        assertEquals(64, run.owned);
        assertEquals(32, state.remembered);
        run = state.beginRun(1, 5);
        assertTrue(run.record(32, inserted(32), 1));
        assertFalse(run.record(32, ECOBatchAdmission.accepted(32, 0, StopReason.BUFFERED), 1));
        assertEquals(32, state.remembered);
    }

    @Test
    void partialAcceptanceAndOneCopyRejectionStop() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        var run = state.beginRun(0, 5);
        assertFalse(run.record(64, ECOBatchAdmission.accepted(3, 0, StopReason.CAPACITY), 0));
        assertEquals(3, run.owned);
        assertEquals(3, state.remembered);
        run = state.beginRun(1, 5);
        assertFalse(run.record(1, rejected(), 1));
        assertEquals(3, state.remembered, "A material-limited tail cannot replace the learned batch");
    }

    @Test
    void integerMaximumStopsWithoutOverflow() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = Integer.MAX_VALUE;
        var run = state.beginRun(0, 5);
        assertEquals(Integer.MAX_VALUE, run.offer(Long.MAX_VALUE));
        assertTrue(run.record(Integer.MAX_VALUE, inserted(Integer.MAX_VALUE), 0));
        assertEquals(Integer.MAX_VALUE, run.offer(Long.MAX_VALUE));
    }

    @Test
    void filledTargetKeepsProvenBatchAndStopsImmediately() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        state.probed = true;
        state.nextProbeTick = 5;
        var run = state.beginRun(1, 5);
        assertTrue(run.record(64, inserted(64), 1));
        assertTrue(run.record(64, inserted(64), 1));
        assertFalse(run.record(64, rejected(), 1));
        assertEquals(128, run.owned);
        assertEquals(64, state.remembered);
        assertEquals(2, state.nextDispatchTick);
        assertEquals(2, state.nextFallbackTick);
        assertEquals(5, state.nextProbeTick);
    }

    @Test
    void failureAfterSuccessfulGrowthDoesNotRetryIntoTheFilledTarget() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        var run = state.beginRun(0, 5);
        for (long offer : new long[]{1, 2, 4, 8, 16, 32, 64}) {
            assertEquals(offer, run.offer(1000));
            assertTrue(run.record(offer, inserted(offer), 0));
        }
        assertFalse(run.record(128, rejected(), 0));
        assertEquals(64, state.remembered);
    }

    @Test
    void completelyBlockedTargetHasBoundedRecoveryEvenWithHugeHistory() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = Integer.MAX_VALUE;
        var run = state.beginRun(0, 5);
        int attempts = 0;
        boolean retry;
        do {
            attempts++;
            retry = run.record(run.offer(Long.MAX_VALUE), rejected(), 0);
        } while (retry && attempts < 100);
        assertEquals(4, attempts);
        assertTrue(state.remembered < Integer.MAX_VALUE);
        assertEquals(5, state.nextDispatchTick);
        assertEquals(state.remembered, state.beginRun(5, 5).offer(Long.MAX_VALUE));
    }

    @Test
    void bufferedOrRejectedTailCannotEraseHistory() {
        for (long accepted : new long[]{0, 3}) {
            var state = new ECOProcessingPatternDispatcher.ProbeState();
            state.remembered = 64;
            var run = state.beginRun(0, 5);
            assertFalse(run.record(run.offer(3), accepted == 0 ? rejected()
                    : ECOBatchAdmission.accepted(accepted, 0, StopReason.BUFFERED), 0));
            assertEquals(64, state.remembered);
            assertEquals(accepted, run.owned);
        }
    }

    @Test
    void refillSaturationCannotPostponeTheNextGrowthProbe() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        var cold = state.beginRun(0, 5);
        assertTrue(cold.record(1, inserted(1), 0));
        assertFalse(cold.record(2, rejected(), 0));
        for (int tick = 1; tick < 5; tick++) {
            var refill = state.beginRun(tick, 5);
            assertEquals(1, refill.offer(100));
            assertTrue(refill.record(1, inserted(1), tick));
            assertFalse(refill.record(1, rejected(), tick));
            assertEquals(5, state.nextProbeTick, "Refills must not slide the growth deadline");
        }
        var probe = state.beginRun(5, 5);
        assertEquals(2, probe.offer(100));
        assertTrue(probe.record(2, inserted(2), 5));
        assertEquals(4, probe.offer(100));
    }

    @Test
    void physicalProofSurvivesAStopCausedByOtherBuffersOrFairness() {
        for (var reason : new StopReason[]{StopReason.BUFFERED, StopReason.FAIRNESS}) {
            var state = new ECOProcessingPatternDispatcher.ProbeState();
            state.probed = true;
            state.nextProbeTick = 5;
            for (int tick = 1; tick <= 8; tick++) {
                var run = state.beginRun(tick, 5);
                long expected = tick < 5 ? 1 : 1L << (tick - 4);
                assertEquals(expected, run.offer(100));
                assertFalse(run.record(expected, ECOBatchAdmission.accepted(expected, expected, reason), tick));
                assertEquals(expected, state.remembered);
                assertEquals(tick < 5 ? 5 : tick + 1, state.nextProbeTick);
            }
        }
    }

    @Test
    void pausesKeepAnOverdueProbeAndTheLearnedBatch() {
        for (var reason : new StopReason[]{StopReason.FAIRNESS, StopReason.RESOURCES,
                StopReason.TARGET_UNAVAILABLE, StopReason.TRANSPORT_LIMIT, StopReason.UNKNOWN}) {
            var state = new ECOProcessingPatternDispatcher.ProbeState();
            state.remembered = 64;
            state.probed = true;
            state.nextProbeTick = 5;
            for (int tick = 5; tick <= 10; tick++) {
                var run = state.beginRun(tick, 5);
                assertEquals(128, run.offer(1000));
                assertFalse(run.record(128, ECOBatchAdmission.rejected(reason), tick));
                assertEquals(64, state.remembered);
                assertEquals(5, state.nextProbeTick);
                assertEquals(tick + 1, state.nextFallbackTick);
            }
        }
    }

    @Test
    void aRecoveryPausedByTheFairnessQueueDoesNotHalveAgain() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        state.probed = true;
        var probe = state.beginRun(0, 5);
        assertEquals(128, probe.offer(1000));
        assertTrue(probe.record(128, rejected(), 0));
        assertEquals(64, probe.offer(1000));
        assertFalse(probe.record(64, ECOBatchAdmission.rejected(StopReason.FAIRNESS), 0));
        assertEquals(64, state.remembered);
        assertEquals(5, state.nextProbeTick);
        assertEquals(64, state.beginRun(1, 5).offer(1000));
    }

    @Test
    void clippedRequestsDoNotConsumeAnOutstandingGrowthProbe() {
        for (boolean accepted : new boolean[]{false, true}) {
            var state = new ECOProcessingPatternDispatcher.ProbeState();
            state.remembered = 64;
            state.probed = true;
            state.nextProbeTick = 0;
            var run = state.beginRun(0, 5);
            assertEquals(3, run.offer(3));
            run.record(3, accepted ? inserted(3) : rejected(), 0);
            if (accepted) {
                // Transport/material clipping can recur while the visit still has budget.
                for (int retry = 0; retry < 3; retry++) {
                    assertEquals(128, run.offer(1000));
                    assertTrue(run.record(3, inserted(3), 0));
                    assertEquals(0, state.nextProbeTick);
                }
            }
            assertEquals(64, state.remembered);
            assertEquals(0, state.nextProbeTick);
            assertEquals(128, state.beginRun(1, 5).offer(1000));
        }
    }

    @Test
    void partialAggregatePreservesProofFromSuccessfulTargets() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 8;
        state.probed = true;
        var run = state.beginRun(0, 5);
        assertEquals(16, run.offer(100));
        assertFalse(run.record(16, ECOBatchAdmission.accepted(12, 10, StopReason.CAPACITY), 0));
        assertEquals(12, run.owned);
        assertEquals(10, state.remembered);
        assertEquals(5, state.nextProbeTick);
    }

    @Test
    void queueAcceptanceDoesNotInventPhysicalCapacity() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        var run = state.beginRun(0, 5);
        assertFalse(run.record(8, ECOBatchAdmission.accepted(8, true), 0));
        assertEquals(8, run.owned);
        assertEquals(1, state.remembered);
        assertFalse(state.probed);
    }

    private static ECOBatchAdmission inserted(long copies) {
        return ECOBatchAdmission.accepted(copies, copies, StopReason.NONE);
    }

    private static ECOBatchAdmission rejected() {
        return ECOBatchAdmission.rejected(StopReason.CAPACITY);
    }
}
