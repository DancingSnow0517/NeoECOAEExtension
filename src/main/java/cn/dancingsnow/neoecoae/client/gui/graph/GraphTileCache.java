package cn.dancingsnow.neoecoae.client.gui.graph;

import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Bounds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Bounded, world-anchored raster work scheduler. It has no dependency on a graphics context. */
final class GraphTileCache<T extends GraphTileCache.Tile> implements AutoCloseable {
    static final int TILE_SIZE = 512;
    static final int MAX_TILES = 32;
    static final int MAX_VISIBLE = 24;
    static final long FRAME_BUDGET_NANOS = 2_000_000;

    interface Tile extends AutoCloseable {
        boolean advance(long deadline);
        @Override void close();
    }

    interface Factory<T> {
        T create(Spec spec);
    }

    record Key(int level, int x, int y) {}
    record Spec(Key key, Bounds bounds, double pixelsPerUnit) {}

    static final class Entry<T> {
        final Spec spec;
        final T tile;
        boolean complete;

        Entry(Spec spec, T tile) {
            this.spec = spec;
            this.tile = tile;
        }
    }

    private final Map<Key, Entry<T>> tiles = new LinkedHashMap<>(16, 0.75f, true);
    private List<Spec> visible = List.of();
    private Bounds viewport;
    private Object content;
    private double guiScale;
    private int cursor;

    void select(Object nextContent, Bounds nextViewport, Bounds drawing, double zoom, double nextGuiScale) {
        if (!Objects.equals(content, nextContent) || guiScale != nextGuiScale) {
            close();
            content = nextContent;
            guiScale = nextGuiScale;
        }
        viewport = nextViewport;
        float left = Math.max(viewport.left(), drawing.left() - 32);
        float top = Math.max(viewport.top(), drawing.top() - 32);
        float right = Math.min(viewport.right(), drawing.right() + 32);
        float bottom = Math.min(viewport.bottom(), drawing.bottom() + 32);
        if (right <= left || bottom <= top) {
            visible = List.of();
            return;
        }
        int level = (int) Math.ceil(2 * Math.log(zoom) / Math.log(2));
        int x0, y0, x1, y1;
        double pixelsPerUnit, side;
        do {
            pixelsPerUnit = guiScale * Math.pow(2, level / 2.0);
            side = TILE_SIZE / pixelsPerUnit;
            x0 = (int) Math.floor(left / side);
            y0 = (int) Math.floor(top / side);
            x1 = (int) Math.floor(Math.nextDown((double) right) / side);
            y1 = (int) Math.floor(Math.nextDown((double) bottom) / side);
            if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) <= MAX_VISIBLE) break;
            level--;
        } while (true);
        List<Spec> requested = new ArrayList<>();
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            requested.add(new Spec(new Key(level, x, y),
                new Bounds((float) (x * side), (float) (y * side),
                    (float) ((x + 1.0) * side), (float) ((y + 1.0) * side)), pixelsPerUnit));
        }
        if (!requested.equals(visible)) visible = List.copyOf(requested);
    }

    boolean needsWork() {
        for (var spec : visible) {
            var entry = tiles.get(spec.key());
            if (entry == null || !entry.complete) return true;
        }
        return false;
    }

    void advance(Factory<T> factory, LongSupplier clock) {
        long deadline = clock.getAsLong() + FRAME_BUDGET_NANOS;
        // One allocation per frame prevents a new overview from allocating all targets at once.
        for (var spec : visible) {
            if (!tiles.containsKey(spec.key())) {
                evict();
                tiles.put(spec.key(), new Entry<>(spec, factory.create(spec)));
                break;
            }
        }
        for (int i = 0; i < visible.size() && clock.getAsLong() < deadline; i++) {
            var spec = visible.get(Math.floorMod(cursor++, visible.size()));
            var entry = tiles.get(spec.key());
            if (entry != null && !entry.complete) entry.complete = entry.tile.advance(deadline);
        }
    }

    private void evict() {
        if (tiles.size() < MAX_TILES) return;
        var iterator = tiles.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (visible.stream().noneMatch(spec -> spec.key().equals(entry.getKey()))) {
                entry.getValue().tile.close();
                iterator.remove();
                return;
            }
        }
        throw new IllegalStateException("Visible tile budget exceeds cache capacity");
    }

    List<Entry<T>> drawable() {
        if (visible.isEmpty()) return List.of();
        List<Entry<T>> result = new ArrayList<>();
        boolean unfinished = false;
        for (var spec : visible) {
            var entry = tiles.get(spec.key());
            if (entry == null || !entry.complete) unfinished = true;
            if (entry != null && !entry.complete) result.add(entry);
        }
        if (unfinished) {
            var key = visible.get(0).key();
            for (var entry : tiles.values()) {
                var other = entry.spec.key();
                if (entry.complete && other.level() != key.level()
                        && entry.spec.bounds().intersects(viewport)) result.add(entry);
            }
        }
        for (var spec : visible) {
            var entry = tiles.get(spec.key());
            if (entry != null && entry.complete) result.add(entry);
        }
        return result;
    }

    List<Spec> visible() { return visible; }
    int size() { return tiles.size(); }

    @Override
    public void close() {
        tiles.values().forEach(entry -> entry.tile.close());
        tiles.clear();
        visible = List.of();
        viewport = null;
        content = null;
        cursor = 0;
    }
}
