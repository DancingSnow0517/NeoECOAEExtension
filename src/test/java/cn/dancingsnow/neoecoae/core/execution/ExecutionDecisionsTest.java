package cn.dancingsnow.neoecoae.core.execution;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ExecutionDecisionsTest {
    @Test void ordinaryProbeAndAdmissionBudgetsRemainIndependent() {
        var budget = new DispatchBudget(1, 2);
        budget.recordNormalProbe();
        assertTrue(budget.canAttemptOrdinary());
        budget.recordNormalProbe();
        assertFalse(budget.canAttemptOrdinary());
        assertEquals(0, budget.acceptedNormalPushes());
        var admitted = new DispatchBudget(1, 64);
        admitted.recordAcceptedNormalPush();
        assertFalse(admitted.canAttemptOrdinary());
        assertEquals(0, admitted.normalProbes());
    }

    @Test void exactBatchesNeverNarrowWhileNormalSchedulingIsBounded() {
        var huge = BigInteger.ONE.shiftLeft(100);
        assertEquals(huge, BatchLimitPlanner.exact(huge.add(BigInteger.ONE), huge, huge, huge));
        assertThrows(IllegalArgumentException.class,
            () -> BatchLimitPlanner.exact(huge, BigInteger.valueOf(-1), huge, huge));
        assertEquals(1, BatchLimitPlanner.count(500, true, false, 5));
        assertEquals(5, BatchLimitPlanner.count(500, false, true, 5));
        assertEquals(0, BatchLimitPlanner.count(0, false, true, 5));
        assertEquals(1, DispatchScheduling.taskDispatchLimit(0));
        assertEquals(64, DispatchScheduling.taskDispatchLimit(Integer.MAX_VALUE));
        assertEquals(64, DispatchScheduling.probeLimit(0));
    }

    @Test void acceptedCraftTotalsSaturateWithoutOverflowingLong() {
        assertEquals(Integer.MAX_VALUE, DispatchScheduling.addAccepted(1, Long.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, DispatchScheduling.addAccepted(Integer.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(15, DispatchScheduling.addAccepted(10, 5));
        assertEquals(10, DispatchScheduling.addAccepted(10, -5));
    }

    @Test void reportsHonorInitialDelayIntervalProgressAndTickReversal() {
        var clock = new StallReportClock(200, 1200);
        clock.progress(1000);
        assertFalse(clock.shouldReport(1199));
        assertTrue(clock.shouldReport(1200));
        assertFalse(clock.shouldReport(2399));
        assertTrue(clock.shouldReport(2400));
        clock.progress(2500);
        assertFalse(clock.shouldReport(2699));
        assertTrue(clock.shouldReport(2700));
        assertTrue(clock.timeReversed(2499));
        assertEquals(0, clock.stalledTicks(2499));
    }

    @Test void dagDependenciesStreamButUnfinishedCyclesRetainTheirBarrier() {
        var barriers = new PhaseBarriers(new int[][] {{}, {}, {0, 1}}, new boolean[] {false, true, false});
        var completed = new java.util.BitSet();
        assertFalse(barriers.ready(2, completed));
        completed.set(1);
        assertTrue(barriers.ready(2, completed));
        assertFalse(completed.get(0));
    }

    @Test void repeatedWitnessConsumesAcceptedFutureCopiesBeforeStartingNextLap() {
        var witness = new OrderedStepSchedule(new long[] {2, 3}, new int[] {1, 2});
        long[] remaining = {0, 0};
        long[] laps = {1, 4};
        long[] ahead = {4, 7};
        int cursor = witness.advance(0, remaining, laps, ahead);
        assertEquals(0, cursor);
        assertArrayEquals(new long[] {2, 2}, remaining);
        assertArrayEquals(new long[] {1, 1}, laps);
        assertArrayEquals(new long[] {0, 0}, ahead);
        remaining[0] = 0;
        cursor = witness.advance(cursor, remaining, laps, ahead);
        assertEquals(1, cursor);
        remaining[1] = 0;
        assertEquals(2, witness.advance(cursor, remaining, laps, ahead));
    }

    @Test void orderedAllowanceIncludesOnlyUnacceptedFutureCopies() {
        var witness = new OrderedStepSchedule(new long[] {2, 3}, new int[] {1, 2});
        assertEquals(5, witness.allowance(0, 1, new long[] {1, 3}, new long[] {1, 4}, new long[] {2, 0}));
        assertEquals(1, witness.allowance(0, -1, new long[] {1, 3}, new long[] {1, 4}, new long[] {2, 0}));
    }
}
