package cn.dancingsnow.neoecoae.dev;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-thread observations; a gap can be a pause or a slow tick, never an inferred TPS. */
final class NetworkProfileActivity {
    private static final long GAP_THRESHOLD_NANOS = 250_000_000;
    private static final int MAX_TRANSITIONS = 128;
    private final long started, duration;
    private final String initialMenu;
    private final Map<String, Long> menuTicks = new LinkedHashMap<>();
    private final List<Map<String, Object>> gaps = new ArrayList<>();
    private final List<Map<String, Object>> menuChanges = new ArrayList<>();
    private long lastObservation, observedTicks, maxGap, gapCount, menuChangeCount;
    private String menu;

    NetworkProfileActivity(long started, long duration, String menu) {
        this.started = started;
        this.duration = duration;
        this.lastObservation = started;
        this.initialMenu = menu;
        this.menu = menu;
    }

    void tick(long now, String currentMenu) {
        if (now - started < 0 || now - started >= duration) return;
        observeGap(now);
        observedTicks++;
        menuTicks.merge(currentMenu, 1L, Long::sum);
        if (!menu.equals(currentMenu)) {
            menuChangeCount++;
            if (menuChanges.size() < MAX_TRANSITIONS)
                menuChanges.add(Map.of("atSeconds", (now - started) / 1e9, "from", menu, "to", currentMenu));
            menu = currentMenu;
        }
    }

    private void observeGap(long now) {
        if (now <= lastObservation) return;
        long gap = now - lastObservation;
        maxGap = Math.max(maxGap, gap);
        if (gap > GAP_THRESHOLD_NANOS) {
            gapCount++;
            if (gaps.size() < MAX_TRANSITIONS)
                gaps.add(Map.of("fromSeconds", (lastObservation - started) / 1e9,
                        "toSeconds", (now - started) / 1e9, "durationMillis", gap / 1e6));
        }
        lastObservation = now;
    }

    Map<String, Object> snapshot(long now) {
        observeGap(started + Math.min(duration, Math.max(0, now - started)));
        var result = new LinkedHashMap<String, Object>();
        result.put("observedServerTicks", observedTicks);
        result.put("longTickGapThresholdMillis", GAP_THRESHOLD_NANOS / 1e6);
        result.put("longTickGapCount", gapCount);
        result.put("maxObservedTickGapMillis", maxGap / 1e6);
        result.put("longTickGaps", List.copyOf(gaps));
        result.put("omittedTickGaps", gapCount - gaps.size());
        result.put("ownerMenuAtStart", initialMenu);
        result.put("ownerMenuAtLastObservedTick", menu);
        result.put("observedTicksByOwnerMenu", new LinkedHashMap<>(menuTicks));
        result.put("ownerMenuChangeCount", menuChangeCount);
        result.put("ownerMenuChanges", List.copyOf(menuChanges));
        result.put("omittedMenuChanges", menuChangeCount - menuChanges.size());
        result.put("scope", "Observed server post-ticks and the command owner's server menu only. A long gap may be a "
                + "paused world, a stalled tick or scheduling; it is not measured pause time. The trailing gap is clipped "
                + "to the recording deadline. Dividing ticks or calls by wall time is not server TPS or peak throughput. "
                + "Menu changes are observed at tick boundaries and do not identify which network a menu views.");
        return result;
    }
}
