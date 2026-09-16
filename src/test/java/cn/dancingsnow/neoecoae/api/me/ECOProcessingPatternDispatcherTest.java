package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ECOProcessingPatternDispatcherTest {
    @Test
    void coldRampAndWarmVisitRepeatTheInitialChunk() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        assertEquals(List.of(1L, 1L, 2L, 4L, 8L), acceptAll(state, 16));
        assertEquals(8, state.remembered);
        assertEquals(List.of(8L, 8L, 16L, 32L), acceptAll(state, 64));
        assertEquals(32, state.remembered);
    }

    @Test
    void initialRejectionHalvesWithinVisitAndRecoverySuccessStops() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        var run = state.beginRun();
        for (long offer : new long[]{64, 32, 16}) {
            assertEquals(offer, run.offer(100));
            assertTrue(run.record(offer, 0, false));
        }
        assertEquals(8, run.offer(100));
        assertFalse(run.record(8, 8, true));
        assertEquals(8, state.remembered);
        assertEquals(8, run.owned);
    }

    @Test
    void rejectionAfterGrowthPreservesLastFullChunk() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 8;
        var run = state.beginRun();
        assertTrue(run.record(8, 8, true));
        assertTrue(run.record(8, 8, true));
        assertFalse(run.record(16, 0, false));
        assertEquals(8, state.remembered);
        assertEquals(16, run.owned);
    }

    @Test
    void tailDoesNotReplaceHistory() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        assertEquals(List.of(7L), acceptAll(state, 7));
        assertEquals(64, state.remembered);
        assertEquals(List.of(64L, 64L, 2L), acceptAll(state, 130));
        assertEquals(64, state.remembered);
    }

    @Test
    void overflowCountsOwnershipButDoesNotProveCapacity() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        var run = state.beginRun();
        assertFalse(run.record(64, 64, false));
        assertEquals(64, run.owned);
        assertEquals(32, state.remembered);
        run = state.beginRun();
        assertTrue(run.record(32, 32, true));
        assertFalse(run.record(32, 32, false));
        assertEquals(32, state.remembered);
    }

    @Test
    void partialAcceptanceAndOneCopyRejectionStop() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = 64;
        var run = state.beginRun();
        assertFalse(run.record(64, 3, false));
        assertEquals(3, run.owned);
        assertEquals(32, state.remembered);
        run = state.beginRun();
        assertFalse(run.record(1, 0, false));
        assertEquals(1, state.remembered);
    }

    @Test
    void integerMaximumStopsWithoutOverflow() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.remembered = Integer.MAX_VALUE;
        var run = state.beginRun();
        assertEquals(Integer.MAX_VALUE, run.offer(Long.MAX_VALUE));
        assertFalse(run.record(Integer.MAX_VALUE, Integer.MAX_VALUE, true));
    }

    private static List<Long> acceptAll(ECOProcessingPatternDispatcher.ProbeState state, long allowance) {
        var run = state.beginRun();
        var offers = new ArrayList<Long>();
        while (run.owned < allowance) {
            long offer = run.offer(allowance - run.owned);
            offers.add(offer);
            if (!run.record(offer, offer, true)) break;
        }
        return offers;
    }
}
