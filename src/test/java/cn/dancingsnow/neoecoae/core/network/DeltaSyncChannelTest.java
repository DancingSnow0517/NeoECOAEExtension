package cn.dancingsnow.neoecoae.core.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeltaSyncChannelTest {
    @Test void failedTransferDoesNotAdvanceBaselineAndResetPublishesFullSnapshot() {
        var channel = new DeltaSyncChannel<String, Long>();
        var first = channel.prepare(Map.of("a", 1L), false);
        assertTrue(first.full());
        var retry = channel.prepare(Map.of("b", 2L), false);
        assertTrue(retry.full());
        assertEquals(Map.of("b", 2L), retry.delta().updates());
        channel.commit(first);
        var next = channel.prepare(Map.of("b", 2L), false);
        assertFalse(next.full());
        assertEquals(java.util.Set.of("a"), next.delta().removed());
        assertEquals(Map.of("b", 2L), next.delta().apply(first.snapshot()));
        channel.commit(next);
        assertTrue(channel.prepare(Map.of("b", 2L), false).delta().isEmpty());
        assertTrue(channel.prepare(Map.of("b", 2L), true).full());
    }
    @Test void pendingSnapshotIsDetachedFromMutableCallerMap() {
        var channel = new DeltaSyncChannel<String, Long>();
        var current = new HashMap<>(Map.of("a", 0L));
        var pending = channel.prepare(current, false);
        current.clear();
        channel.commit(pending);
        assertEquals(Map.of("a", 0L), pending.snapshot());
        assertEquals(java.util.Set.of("a"), channel.prepare(Map.of(), false).delta().removed());
    }
}
