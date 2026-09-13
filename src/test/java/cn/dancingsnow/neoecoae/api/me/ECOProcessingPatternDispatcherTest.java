package cn.dancingsnow.neoecoae.api.me;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ECOProcessingPatternDispatcherTest {
    @Test
    void probeFollowsWarmupLadderThenDoubles() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        assertEquals(1L, state.next);
        state.success(1, 1); assertEquals(64L, state.next);
        state.success(64, 64); assertEquals(1_024L, state.next);
        state.success(1_024, 1_024); assertEquals(8_192L, state.next);
        state.success(8_192, 8_192); assertEquals(50_000L, state.next);
        state.success(50_000, 50_000); assertEquals(100_000L, state.next);
    }

    @Test
    void partialAcceptanceAndRejectionBackOffPerState() {
        var state = new ECOProcessingPatternDispatcher.ProbeState();
        state.success(1_024, 300);
        assertEquals(300L, state.next);
        state.fail(300);
        assertEquals(150L, state.next);
        state.fail(1);
        assertEquals(1L, state.next);
    }
}
