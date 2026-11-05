package cn.dancingsnow.neoecoae.dev;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkProfileActivityTest {
    @Test void longPauseDoesNotBecomeLowTpsAndMenuChangesAreVisible() {
        var activity = new NetworkProfileActivity(0, 30_000_000_000L, "inventory");
        activity.tick(50_000_000L, "inventory");
        activity.tick(100_000_000L, "terminal");
        activity.tick(12_100_000_000L, "inventory");
        var report = activity.snapshot(12_150_000_000L);
        assertEquals(3L, report.get("observedServerTicks"));
        assertEquals(1L, report.get("longTickGapCount"));
        assertEquals(12_000.0, report.get("maxObservedTickGapMillis"));
        assertEquals(2L, report.get("ownerMenuChangeCount"));
        assertEquals(Map.of("inventory", 2L, "terminal", 1L), report.get("observedTicksByOwnerMenu"));
        assertFalse(report.containsKey("tps"));
        assertTrue(report.get("scope").toString().contains("not server TPS"));
    }

    @Test void stoppingWhileNoTicksArriveClipsTheTrailingGapAtTheDeadline() {
        var activity = new NetworkProfileActivity(1_000_000_000L, 30_000_000_000L, "inventory");
        activity.tick(2_000_000_000L, "inventory");
        activity.tick(50_000_000_000L, "late-menu");
        var report = activity.snapshot(50_000_000_000L);
        assertEquals(1L, report.get("observedServerTicks"));
        assertEquals("inventory", report.get("ownerMenuAtLastObservedTick"));
        var gaps = (List<?>) report.get("longTickGaps");
        assertEquals(30.0, ((Map<?, ?>) gaps.getLast()).get("toSeconds"));
        assertEquals(29_000.0, report.get("maxObservedTickGapMillis"));
        assertEquals(report, activity.snapshot(60_000_000_000L));
    }

    @Test void transitionListsRemainBoundedWithoutLosingCounts() {
        var activity = new NetworkProfileActivity(0, 600_000_000_000L, "a");
        for (int i = 1; i <= 400; i++) activity.tick(i * 1_000_000_000L, i % 2 == 0 ? "a" : "b");
        var report = activity.snapshot(400_000_000_000L);
        assertEquals(400L, report.get("longTickGapCount"));
        assertEquals(400L, report.get("ownerMenuChangeCount"));
        assertEquals(272L, report.get("omittedTickGaps"));
        assertEquals(272L, report.get("omittedMenuChanges"));
        assertEquals(128, ((List<?>) report.get("longTickGaps")).size());
    }
}
