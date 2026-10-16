package cn.dancingsnow.neoecoae.core.planner;

import static org.junit.jupiter.api.Assertions.*;
import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndexedCycleSearchTest {
    private static final long[][] CONSUMED = {{1, 0, 0}, {0, 1, 0}};
    private static final long[][] PRODUCED = {{0, 1, 0}, {0, 0, 1}};
    private static final IndexedCycleSearch.Data DATA = new IndexedCycleSearch.Data(CONSUMED,
        new boolean[3], new boolean[] {true, true, false}, new boolean[] {false, true}, amounts(0, 0, 1));
    private static final IndexedCycleSearch.Memory MEMORY = new IndexedCycleSearch.Memory() {
        public void retain(long bytes) {}
        public void check(long bytes) {}
        public long markingBytes(PlannerAmount[] marking) { return 192 + 16L * marking.length; }
    };

    private static PlannerAmount[] amounts(long... values) {
        return java.util.Arrays.stream(values).mapToObj(PlannerAmount::of).toArray(PlannerAmount[]::new);
    }

    private static IndexedCycleSearch.Callbacks<String> transitions(long[][] consumed, long[][] produced) {
        return new IndexedCycleSearch.Callbacks<>() {
            public long[] batches(PlannerAmount[] marking, int transition) {
                for (int i = 0; i < marking.length; i++) {
                    if (marking[i].compareTo(PlannerAmount.of(consumed[transition][i])) < 0) return new long[0];
                }
                return new long[] {1};
            }
            public PlannerAmount[] fire(PlannerAmount[] marking, int transition, long batch) {
                var next = marking.clone();
                for (int i = 0; i < next.length; i++) {
                    next[i] = next[i].subtract(consumed[transition][i]).add(produced[transition][i]);
                }
                return next;
            }
            public PlannerAmount[] accelerate(PlannerAmount[] marking, List<String> witness) { return marking; }
            public String firing(int transition, long batch) { return transition + "x" + batch; }
        };
    }

    @Test void numericSearchReconstructsOrderedWitnessWithoutPlatformKeys() throws Exception {
        var result = new IndexedCycleSearch.Result<String>();
        IndexedCycleSearch.search(DATA, amounts(1, 0, 0), 100, 100, Cancellation.NONE, MEMORY,
            transitions(CONSUMED, PRODUCED), result);
        assertEquals(IndexedCycleSearch.Result.Kind.REACHED, result.kind);
        assertEquals(List.of("0x1", "1x1"), result.witness);
        assertEquals(3, result.statesVisited);
    }

    @Test void stateAndDepthTruncationRemainUnknownRatherThanExhaustion() throws Exception {
        var stateBound = new IndexedCycleSearch.Result<String>();
        IndexedCycleSearch.search(DATA, amounts(1, 0, 0), 1, 100, Cancellation.NONE, MEMORY,
            transitions(CONSUMED, PRODUCED), stateBound);
        assertEquals(IndexedCycleSearch.Result.Kind.STATE_BUDGET, stateBound.kind);
        assertTrue(stateBound.stateBudgetExhausted);
        var depthBound = new IndexedCycleSearch.Result<String>();
        IndexedCycleSearch.search(DATA, amounts(1, 0, 0), 100, 1, Cancellation.NONE, MEMORY,
            transitions(CONSUMED, PRODUCED), depthBound);
        assertEquals(IndexedCycleSearch.Result.Kind.DEPTH_TRUNCATED, depthBound.kind);
        assertTrue(depthBound.firingDepthTruncated);
    }

    @Test void disabledTransitionRetainsRankedStartupDeficit() throws Exception {
        var result = new IndexedCycleSearch.Result<String>();
        IndexedCycleSearch.search(DATA, amounts(0, 0, 0), 100, 100, Cancellation.NONE, MEMORY,
            transitions(CONSUMED, PRODUCED), result);
        assertEquals(IndexedCycleSearch.Result.Kind.EXHAUSTED, result.kind);
        assertArrayEquals(amounts(0, 1, 0), result.unblockDeficit);
    }

    @Test void terminalProductSurplusDoesNotCreateUnboundedDistinctMarkings() throws Exception {
        long[][] consumed = {{0, 0}}, produced = {{1, 0}};
        var data = new IndexedCycleSearch.Data(consumed, new boolean[2], new boolean[2], new boolean[1], amounts(0, 1));
        var result = new IndexedCycleSearch.Result<String>();
        IndexedCycleSearch.search(data, amounts(0, 0), 100, 100, Cancellation.NONE, MEMORY,
            transitions(consumed, produced), result);
        assertEquals(IndexedCycleSearch.Result.Kind.EXHAUSTED, result.kind);
        assertEquals(1, result.statesVisited);
    }

    @Test void cancellationAndMemoryRejectionPropagateWithoutFeasibilityClaims() {
        var result = new IndexedCycleSearch.Result<String>();
        assertThrows(InterruptedException.class, () -> IndexedCycleSearch.search(DATA, amounts(1, 0, 0), 100, 100,
            () -> { throw new InterruptedException("cancelled"); }, MEMORY,
            transitions(CONSUMED, PRODUCED), result));
        var rejecting = new IndexedCycleSearch.Memory() {
            public void retain(long bytes) { throw new IllegalStateException("memory"); }
            public void check(long bytes) {}
            public long markingBytes(PlannerAmount[] marking) { return 1; }
        };
        assertThrows(IllegalStateException.class, () -> IndexedCycleSearch.search(DATA, amounts(1, 0, 0), 100, 100,
            Cancellation.NONE, rejecting, transitions(CONSUMED, PRODUCED), result));
        assertTrue(result.witness.isEmpty());
    }
}
