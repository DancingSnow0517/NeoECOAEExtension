package cn.dancingsnow.neoecoae.crafting.graph;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Bounds;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Box;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Point;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphViewportIndexTest {
    @Test
    void routedConnectionRemainsVisibleWhenBothEndpointsAreOffscreen() {
        var link = new ClientCraftingGraph.Link(0, 1, 1, CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true);
        var layout = new GraphLayoutSnapshot(Map.of(0, new Box(0, -2000, 50, 20, 30),
            1, new Box(1, 2000, 50, 20, 30)), List.of(link),
            Map.of(link, List.of(new Point(-1980, 65), new Point(2000, 65))),
            new Bounds(-2000, 50, 2020, 80), 0, 1);
        var area = new Bounds(-1, 60, 1, 70);
        assertTrue(layout.query(area, 100).isEmpty());
        assertEquals(List.of(link), layout.queryLinks(area));
        assertEquals(List.of(link), layout.linksAt(0));
        assertEquals(1, layout.edgeCount(area));
        assertTrue(layout.queryLinks(new Bounds(-10, 1000, 10, 1100)).isEmpty());
    }

    @Test
    void enormousOverviewQueriesDoNotScanEmptyGridCells() {
        var boxes = new HashMap<Integer, Box>();
        for (int i = 0; i < 3000; i++) boxes.put(i, new Box(i, i * 1_000_000f, i % 2 * 1_000_000f, 20, 30));
        var layout = new GraphLayoutSnapshot(boxes, List.of(), new Bounds(0, 0, 3e9f, 1e6f + 30), 0, 1);
        var area = new Bounds(-Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            assertEquals(3000, layout.query(area, Integer.MAX_VALUE).size());
            assertEquals(17, layout.query(area, 17).size());
            assertTrue(layout.query(area, 0).isEmpty());
            assertEquals(3000, layout.nodeCount(area));
            assertTrue(layout.query(new Bounds(-100, -100, -10, -10), 100).isEmpty());
        });
    }

    @Test
    void rowFramesAreMergedOnceAndQueriedByTheirOwnBounds() {
        var boxes = Map.of(0, new Box(0, 0, 0, 20, 30), 1, new Box(1, 20, 0, 20, 34),
            2, new Box(2, 100, 0, 20, 30), 3, new Box(3, 0, 100, 20, 30));
        var layout = new GraphLayoutSnapshot(boxes, List.of(), new Bounds(0, 0, 120, 130), 0, 1);
        var expected = List.of(new Bounds(0, 0, 40, 34), new Bounds(100, 0, 120, 30), new Bounds(0, 100, 20, 130));
        assertEquals(expected, layout.rowFrames());
        assertSame(layout.rowFrames(), layout.rowFrames());
        assertEquals(List.of(expected.get(0)), layout.queryRowFrames(new Bounds(10, 10, 15, 15)));
        assertTrue(layout.queryRowFrames(new Bounds(50, 50, 60, 60)).isEmpty());
    }

    @Test
    void cachedLayoutDoesNotObserveLaterMutationsOfItsInputs() {
        var boxes = new HashMap<Integer, Box>();
        boxes.put(0, new Box(0, 0, 0, 20, 30));
        boxes.put(1, new Box(1, 100, 100, 20, 30));
        var link = new ClientCraftingGraph.Link(0, 1, 1, CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true);
        var links = new ArrayList<>(List.of(link));
        var route = new ArrayList<>(List.of(new Point(10, 30), new Point(110, 100)));
        var layout = new GraphLayoutSnapshot(boxes, links, Map.of(link, route), new Bounds(0, 0, 120, 130), 0, 1);
        boxes.clear();
        links.clear();
        route.clear();
        assertEquals(2, layout.boxes().size());
        assertEquals(1, layout.links().size());
        assertEquals(2, layout.edgePoints(link).size());
        assertEquals(2, layout.nodeCount(layout.bounds()));
    }
}
