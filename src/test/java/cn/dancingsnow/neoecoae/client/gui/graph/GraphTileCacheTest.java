package cn.dancingsnow.neoecoae.client.gui.graph;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Bounds;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphTileCacheTest {
    private static final Bounds DRAWING = new Bounds(-100_000, -100_000, 100_000, 100_000);

    private static final class Raster implements GraphTileCache.Tile {
        final long[] clock;
        final int required;
        int steps, closes;
        long cost;

        Raster(long[] clock, int required, long cost) {
            this.clock = clock;
            this.required = required;
            this.cost = cost;
        }

        @Override public boolean advance(long deadline) {
            assertTrue(clock[0] < deadline);
            steps++;
            clock[0] += cost;
            return steps >= required;
        }

        @Override public void close() { closes++; }
    }

    @Test
    void completedOverviewDoesNotRasterizeAgain() {
        var cache = new GraphTileCache<Raster>();
        var content = new Object();
        var area = new Bounds(0, 0, 4000, 2000);
        long[] clock = {0};
        List<Raster> created = new ArrayList<>();
        cache.select(content, area, DRAWING, 0.1, 2);
        var visible = cache.visible();
        for (int i = 0; i < GraphTileCache.MAX_VISIBLE; i++) {
            cache.advance(spec -> {
                var raster = new Raster(clock, 1, 1);
                created.add(raster);
                return raster;
            }, () -> clock[0]);
        }
        assertFalse(cache.needsWork());
        assertEquals(visible.size(), created.size());
        cache.select(content, new Bounds(0, 0, 4000, 2000), DRAWING, 0.1, 2);
        assertSame(visible, cache.visible());
        cache.advance(spec -> { throw new AssertionError("Warm view allocated a target"); }, () -> clock[0]);
        assertTrue(created.stream().allMatch(raster -> raster.steps == 1));
        assertEquals(visible.size(), cache.drawable().size());
        cache.close();
    }

    @Test
    void continuousPanningKeepsThePartiallyPaintedWorldTile() {
        var cache = new GraphTileCache<Raster>();
        var content = new Object();
        long[] clock = {0};
        var raster = new Raster(clock, 3, GraphTileCache.FRAME_BUDGET_NANOS + 1);
        cache.select(content, new Bounds(0, 0, 300, 300), DRAWING, 1, 1);
        cache.advance(spec -> raster, () -> clock[0]);
        assertEquals(1, raster.steps);
        assertFalse(cache.drawable().get(0).complete);
        for (int pan = 1; pan <= 2; pan++) {
            cache.select(content, new Bounds(pan * 30, 0, 300 + pan * 30, 300), DRAWING, 1, 1);
            cache.advance(spec -> { throw new AssertionError("Pan restarted an existing painter"); }, () -> clock[0]);
        }
        assertEquals(3, raster.steps);
        assertTrue(cache.drawable().get(0).complete);
        assertEquals(0, raster.closes);
        cache.close();
    }

    @Test
    void workBudgetAndTargetAllocationAreBoundedPerFrame() {
        var cache = new GraphTileCache<Raster>();
        long[] clock = {0};
        List<Raster> created = new ArrayList<>();
        cache.select(new Object(), new Bounds(0, 0, 8000, 4000), DRAWING, 1, 4);
        assertTrue(cache.visible().size() <= GraphTileCache.MAX_VISIBLE);
        for (int frame = 1; frame <= 2; frame++) {
            cache.advance(spec -> {
                var raster = new Raster(clock, 10, GraphTileCache.FRAME_BUDGET_NANOS + 1);
                created.add(raster);
                return raster;
            }, () -> clock[0]);
            assertEquals(frame, created.size());
            assertEquals(frame, created.stream().mapToInt(raster -> raster.steps).sum());
        }
        cache.close();
    }

    @Test
    void adjacentZoomShowsTheCompletedOldLevelWhileNewTilesAreBuilt() {
        var cache = new GraphTileCache<Raster>();
        var content = new Object();
        var area = new Bounds(0, 0, 300, 300);
        long[] clock = {0};
        var ready = new Raster(clock, 1, 1);
        cache.select(content, area, DRAWING, 1, 1);
        cache.advance(spec -> ready, () -> clock[0]);
        assertFalse(cache.needsWork());
        cache.select(content, area, DRAWING, 1.2, 1);
        var pending = new Raster(clock, 10, GraphTileCache.FRAME_BUDGET_NANOS + 1);
        cache.advance(spec -> pending, () -> clock[0]);
        assertTrue(cache.drawable().stream().anyMatch(entry -> entry.tile == ready && entry.complete));
        assertTrue(cache.drawable().stream().anyMatch(entry -> entry.tile == pending && !entry.complete));
        assertEquals(0, ready.closes);
        cache.close();
    }

    @Test
    void longPansEvictOldTargetsAndClosingIsIdempotent() {
        var cache = new GraphTileCache<Raster>();
        var content = new Object();
        long[] clock = {0};
        List<Raster> created = new ArrayList<>();
        for (int pan = 0; pan < 100; pan++) {
            cache.select(content, new Bounds(pan * 600, 0, pan * 600 + 300, 300), DRAWING, 1, 1);
            cache.advance(spec -> {
                var raster = new Raster(clock, 1, 1);
                created.add(raster);
                return raster;
            }, () -> clock[0]);
            assertTrue(cache.size() <= GraphTileCache.MAX_TILES);
            for (var entry : cache.drawable()) assertEquals(0, entry.tile.closes);
        }
        assertTrue(created.stream().anyMatch(raster -> raster.closes == 1));
        cache.close();
        cache.close();
        assertEquals(0, cache.size());
        assertTrue(created.stream().allMatch(raster -> raster.closes == 1));
    }

    @Test
    void graphOrGuiScaleChangesReleaseAllOldTargets() {
        var cache = new GraphTileCache<Raster>();
        var content = new Object();
        var area = new Bounds(0, 0, 100, 100);
        long[] clock = {0};
        var first = new Raster(clock, 1, 1);
        cache.select(content, area, DRAWING, 1, 1);
        cache.advance(spec -> first, () -> clock[0]);
        cache.select(new Object(), area, DRAWING, 1, 1);
        assertEquals(1, first.closes);
        var second = new Raster(clock, 1, 1);
        cache.advance(spec -> second, () -> clock[0]);
        cache.select(content, area, DRAWING, 1, 2);
        assertEquals(1, second.closes);
        assertTrue(cache.needsWork());
        cache.close();
    }

    @Test
    void negativeCoordinatesAndExtremelySmallZoomStillUseFiniteBoundedTiles() {
        var cache = new GraphTileCache<Raster>();
        cache.select(new Object(), new Bounds(-50, -50, 50, 50), DRAWING, 1, 1);
        assertEquals(4, cache.visible().size());
        assertTrue(cache.visible().stream().anyMatch(spec -> spec.key().x() == -1 && spec.key().y() == -1));
        cache.select(new Object(), new Bounds(-1e12f, -1e12f, 1e12f, 1e12f),
            new Bounds(-1e12f, -1e12f, 1e12f, 1e12f), 1e-10, 2);
        assertTrue(cache.visible().size() <= GraphTileCache.MAX_VISIBLE);
        assertTrue(cache.visible().stream().allMatch(spec -> Double.isFinite(spec.pixelsPerUnit())
            && spec.pixelsPerUnit() > 0 && Float.isFinite(spec.bounds().right())));
        cache.select(new Object(), new Bounds(200_000, 200_000, 201_000, 201_000), DRAWING, 1, 1);
        assertTrue(cache.visible().isEmpty());
        assertFalse(cache.needsWork());
        cache.close();
    }
}
