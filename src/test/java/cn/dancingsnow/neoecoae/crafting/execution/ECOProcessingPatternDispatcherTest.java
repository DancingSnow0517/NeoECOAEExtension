package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ECOProcessingPatternDispatcherTest {
    @Test
    void successfulProbesKeepGrowingAndResumeOnTheNextTick() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        var first = state.beginRun(0, 5);
        assertEquals(1, first.offer(16));
        assertTrue(first.record(1, 1, true, 0));
        assertEquals(1, state.remembered);

        assertEquals(2, first.offer(15));
        var warm = state.beginRun(0, 5);
        assertEquals(1, warm.offer(16));
        assertTrue(warm.record(1, 1, true, 0));
        assertEquals(1, warm.offer(15));

        var nextProbe = state.beginRun(1, 5);
        assertEquals(2, nextProbe.offer(16));
        assertTrue(nextProbe.record(2, 2, true, 1));
        assertEquals(2, state.remembered);
        assertEquals(4, nextProbe.offer(100));
        assertTrue(nextProbe.record(4, 4, true, 1));
        assertEquals(8, nextProbe.offer(100));
    }

    @Test
    void initialRejectionHalvesWithinVisitAndRecoverySuccessStops() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        var run = state.beginRun(0, 5);
        assertEquals(64, run.offer(100));
        assertTrue(run.record(64, 0, false, 0));
        assertEquals(32, state.remembered);
        assertEquals(32, run.offer(100));
        assertFalse(run.record(32, 32, true, 0));
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
        assertTrue(run.record(8, 8, true, 0));
        var probe = state.beginRun(5, 5);
        assertEquals(16, probe.offer(100));
        assertTrue(probe.record(16, 0, false, 5));
        assertEquals(8, state.remembered);
        assertEquals(0, probe.owned);
        assertEquals(8, probe.offer(100));
        assertFalse(probe.record(8, 8, true, 5));
        assertEquals(8, probe.owned);
    }

    @Test
    void tailDoesNotReplaceHistory() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        var tail = state.beginRun(0, 5);
        assertEquals(7L, tail.offer(7L));
        assertTrue(tail.record(7, 7, true, 0));
        assertEquals(64, state.remembered);
        var warm = state.beginRun(0, 5);
        // A clipped tail does not suppress the next full growth probe.
        assertEquals(128, warm.offer(130));
        state.nextProbeTick = 5;
        warm = state.beginRun(1, 5);
        assertEquals(64, warm.offer(130));
        assertTrue(warm.record(64, 64, true, 1));
        assertEquals(64, warm.offer(66));
    }

    @Test
    void overflowCountsOwnershipButDoesNotProveCapacity() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        var run = state.beginRun(0, 5);
        assertFalse(run.record(64, 64, false, 0));
        assertEquals(64, run.owned);
        assertEquals(32, state.remembered);
        run = state.beginRun(1, 5);
        assertTrue(run.record(32, 32, true, 1));
        assertFalse(run.record(32, 32, false, 1));
        assertEquals(32, state.remembered);
    }

    @Test
    void partialAcceptanceAndOneCopyRejectionStop() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        var run = state.beginRun(0, 5);
        assertFalse(run.record(64, 3, false, 0));
        assertEquals(3, run.owned);
        assertEquals(3, state.remembered);
        run = state.beginRun(1, 5);
        assertFalse(run.record(1, 0, false, 1));
        assertEquals(3, state.remembered, "A material-limited tail cannot replace the learned batch");
    }

    @Test
    void integerMaximumStopsWithoutOverflow() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = Integer.MAX_VALUE;
        var run = state.beginRun(0, 5);
        assertEquals(Integer.MAX_VALUE, run.offer(Long.MAX_VALUE));
        assertTrue(run.record(Integer.MAX_VALUE, Integer.MAX_VALUE, true, 0));
        assertEquals(Integer.MAX_VALUE, run.offer(Long.MAX_VALUE));
    }

    @Test
    void filledTargetKeepsProvenBatchAndStopsImmediately() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        state.probed = true;
        state.nextProbeTick = 5;
        var run = state.beginRun(1, 5);
        assertTrue(run.record(64, 64, true, 1));
        assertTrue(run.record(64, 64, true, 1));
        assertFalse(run.record(64, 0, false, 1));
        assertEquals(128, run.owned);
        assertEquals(64, state.remembered);
        assertEquals(2, state.nextDispatchTick);
        assertEquals(2, state.nextFallbackTick);
    }

    @Test
    void failureAfterSuccessfulGrowthDoesNotRetryIntoTheFilledTarget() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        var run = state.beginRun(0, 5);
        for (long offer : new long[]{1, 2, 4, 8, 16, 32, 64}) {
            assertEquals(offer, run.offer(1000));
            assertTrue(run.record(offer, offer, true, 0));
        }
        assertFalse(run.record(128, 0, false, 0));
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
            retry = run.record(run.offer(Long.MAX_VALUE), 0, false, 0);
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
            assertFalse(run.record(run.offer(3), accepted, false, 0));
            assertEquals(64, state.remembered);
            assertEquals(accepted, run.owned);
        }
    }
}
