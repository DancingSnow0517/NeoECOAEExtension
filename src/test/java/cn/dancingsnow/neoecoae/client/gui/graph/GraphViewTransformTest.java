package cn.dancingsnow.neoecoae.client.gui.graph;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Bounds;
import org.junit.jupiter.api.Test;

class GraphViewTransformTest {
    @Test
    void wideTreesRespectTheFivePercentWheelFloor() {
        var bounds = new Bounds(-200_000, -100, 12_000_000, 5000);
        float zoom = GraphViewTransform.fitZoom(bounds, 560, 260);
        assertEquals(GraphViewTransform.MIN_ZOOM, zoom);
    }

    @Test
    void fitAndWheelZoomUseTheSameMinimum() {
        var bounds = new Bounds(0, 0, 1e12f, 5000);
        float zoom = GraphViewTransform.fitZoom(bounds, 560, 260);
        assertEquals(GraphViewTransform.MIN_ZOOM, zoom);
        assertTrue(GraphViewTransform.scrollZoom(zoom, -1) <= zoom);
        assertTrue(GraphViewTransform.scrollZoom(zoom, 1) > zoom);
    }

    @Test
    void smallOrEmptyBoundsAndSmallWindowsKeepFiniteCameras() {
        assertEquals(GraphViewTransform.MAX_ZOOM, GraphViewTransform.fitZoom(new Bounds(0, 0, 1, 1), 500, 300));
        float zoom = GraphViewTransform.fitZoom(new Bounds(0, 0, 0, 0), 0, -10);
        assertTrue(Float.isFinite(zoom) && zoom > 0);
        assertEquals(GraphViewTransform.MAX_ZOOM, GraphViewTransform.scrollZoom(2, 1000));
        assertTrue(GraphViewTransform.scrollZoom(1, -1000) > 0);
    }

    @Test
    void overviewDensityUsesHysteresisToAvoidCacheModeThrashing() {
        assertFalse(GraphRenderer.useCache(256, false));
        assertTrue(GraphRenderer.useCache(257, false));
        assertTrue(GraphRenderer.useCache(200, true));
        assertFalse(GraphRenderer.useCache(192, true));
        assertFalse(GraphRenderer.useCache(1, false));
    }
}
