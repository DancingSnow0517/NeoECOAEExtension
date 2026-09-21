package cn.dancingsnow.neoecoae.crafting.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ECOProcessingPatternDispatcherTest {
    @Test
    void probeGrowthWaitsForTheConfiguredTickInterval() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        var first = state.beginRun(0, 5);
        assertEquals(1, first.offer(16));
        assertTrue(first.record(1, 1, true, 0));
        assertEquals(1, state.remembered);

        var warm = state.beginRun(4, 5);
        assertEquals(1, warm.offer(16));
        assertTrue(warm.record(1, 1, true, 4));
        assertEquals(1, warm.offer(15));

        var nextProbe = state.beginRun(5, 5);
        assertEquals(2, nextProbe.offer(16));
        assertFalse(nextProbe.record(2, 2, true, 5));
        assertEquals(2, state.remembered);
    }

    @Test
    void initialRejectionHalvesWithinVisitAndRecoverySuccessStops() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        var run = state.beginRun(0, 5);
        for (long offer : new long[]{64}) {
            assertEquals(offer, run.offer(100));
            assertFalse(run.record(offer, 0, false, 0));
        }
        assertEquals(32, state.remembered);
        var recovery = state.beginRun(1, 5);
        assertEquals(32, recovery.offer(100));
        assertTrue(recovery.record(32, 32, true, 1));
        assertEquals(32, state.remembered);
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
        assertFalse(probe.record(16, 0, false, 5));
        assertEquals(8, state.remembered);
        assertEquals(0, probe.owned);
    }

    @Test
    void tailDoesNotReplaceHistory() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        var tail = state.beginRun(0, 5);
        assertEquals(List.of(7L), List.of(tail.offer(7L)));
        assertTrue(tail.record(7, 7, true, 0));
        assertEquals(64, state.remembered);
        var warm = state.beginRun(1, 5);
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
        assertEquals(16, state.remembered);
    }

    @Test
    void partialAcceptanceAndOneCopyRejectionStop() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        var run = state.beginRun(0, 5);
        assertFalse(run.record(64, 3, false, 0));
        assertEquals(3, run.owned);
        assertEquals(32, state.remembered);
        run = state.beginRun(1, 5);
        assertFalse(run.record(1, 0, false, 1));
        assertEquals(1, state.remembered);
    }

    @Test
    void integerMaximumStopsWithoutOverflow() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = Integer.MAX_VALUE;
        var run = state.beginRun(0, 5);
        assertEquals(Integer.MAX_VALUE, run.offer(Long.MAX_VALUE));
        assertTrue(run.record(Integer.MAX_VALUE, Integer.MAX_VALUE, true, 0));
    }
}
