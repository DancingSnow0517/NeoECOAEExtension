package cn.dancingsnow.neoecoae.client.craftinggraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable layout and uniform-grid spatial index. */
public final class GraphLayoutSnapshot {
    public static final int MATERIAL_WIDTH = 132;
    public static final int MATERIAL_HEIGHT = 76;
    public static final int PATTERN_WIDTH = 56;
    public static final int PATTERN_HEIGHT = 32;
    public static final int CYCLE_WIDTH = 150;
    public static final int CYCLE_HEIGHT = 72;
    private static final int CELL_SIZE = 256;

    public record Box(int nodeId, float x, float y, float width, float height) {
        public float centerX() { return x + width / 2; }
        public float centerY() { return y + height / 2; }
        public boolean intersects(float left, float top, float right, float bottom) {
            return x + width >= left && x <= right && y + height >= top && y <= bottom;
        }
        public boolean contains(float px, float py) { return px >= x && px <= x + width && py >= y && py <= y + height; }
    }

    public record Bounds(float left, float top, float right, float bottom) {
        public float width() { return Math.max(1, right - left); }
        public float height() { return Math.max(1, bottom - top); }
    }

    private final Map<Integer, Box> boxes;
    private final Map<Long, int[]> grid;
    private final List<ClientCraftingGraph.Link> links;
    private final Map<Integer, int[]> edgesByNode;
    private final Bounds bounds;
    private final long layoutNanos;
    private final long spatialIndexNanos;
    private final long version;

    GraphLayoutSnapshot(Map<Integer, Box> boxes, List<ClientCraftingGraph.Link> links, Bounds bounds,
            long layoutNanos, long version) {
        this.boxes = Map.copyOf(boxes);
        this.bounds = bounds;
        this.version = version;
        this.links = List.copyOf(links);
        long spatialStarted = System.nanoTime();
        Map<Long, List<Integer>> mutable = new HashMap<>();
        for (Box box : boxes.values()) {
            int minX = floorCell(box.x());
            int maxX = floorCell(box.x() + box.width());
            int minY = floorCell(box.y());
            int maxY = floorCell(box.y() + box.height());
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) {
                mutable.computeIfAbsent(cellKey(x, y), ignored -> new ArrayList<>()).add(box.nodeId());
            }
        }
        Map<Long, int[]> frozen = new HashMap<>();
        mutable.forEach((key, ids) -> frozen.put(key, ids.stream().mapToInt(Integer::intValue).toArray()));
        this.grid = Map.copyOf(frozen);

        Map<Integer, List<Integer>> mutableEdges = new HashMap<>();
        for (int i = 0; i < links.size(); i++) {
            var link = links.get(i);
            mutableEdges.computeIfAbsent(link.fromId(), ignored -> new ArrayList<>()).add(i);
            mutableEdges.computeIfAbsent(link.toId(), ignored -> new ArrayList<>()).add(i);
        }
        Map<Integer, int[]> frozenEdges = new HashMap<>();
        mutableEdges.forEach((key, ids) -> frozenEdges.put(key, ids.stream().mapToInt(Integer::intValue).toArray()));
        this.edgesByNode = Map.copyOf(frozenEdges);
        this.layoutNanos = layoutNanos;
        this.spatialIndexNanos = System.nanoTime() - spatialStarted;
    }

    public Map<Integer, Box> boxes() { return boxes; }
    public Box box(int id) { return boxes.get(id); }
    public Bounds bounds() { return bounds; }
    public long layoutNanos() { return layoutNanos; }
    public long spatialIndexNanos() { return spatialIndexNanos; }
    public long version() { return version; }

    public List<Box> query(float left, float top, float right, float bottom, float margin) {
        left -= margin;
        top -= margin;
        right += margin;
        bottom += margin;
        Set<Integer> candidates = new LinkedHashSet<>();
        for (int x = floorCell(left); x <= floorCell(right); x++) {
            for (int y = floorCell(top); y <= floorCell(bottom); y++) {
                int[] ids = grid.get(cellKey(x, y));
                if (ids != null) for (int id : ids) candidates.add(id);
            }
        }
        List<Box> visible = new ArrayList<>(candidates.size());
        for (int id : candidates) {
            Box box = boxes.get(id);
            if (box.intersects(left, top, right, bottom)) visible.add(box);
        }
        return visible;
    }

    public List<ClientCraftingGraph.Link> queryLinks(float left, float top, float right, float bottom, float margin) {
        // Deliberate v1 approximation: an edge is considered only when at least one endpoint node is near the
        // viewport. A long edge whose two endpoints are both off-screen is culled even if its line crosses the view.
        Set<Integer> candidates = new LinkedHashSet<>();
        for (Box box : query(left, top, right, bottom, margin)) {
            int[] ids = edgesByNode.get(box.nodeId());
            if (ids != null) for (int id : ids) candidates.add(id);
        }
        List<ClientCraftingGraph.Link> visible = new ArrayList<>(candidates.size());
        for (int id : candidates) visible.add(links.get(id));
        return visible;
    }

    int candidateNodeCount(float left, float top, float right, float bottom, float margin) {
        left -= margin;
        top -= margin;
        right += margin;
        bottom += margin;
        Set<Integer> candidates = new LinkedHashSet<>();
        for (int x = floorCell(left); x <= floorCell(right); x++) {
            for (int y = floorCell(top); y <= floorCell(bottom); y++) {
                int[] ids = grid.get(cellKey(x, y));
                if (ids != null) for (int id : ids) candidates.add(id);
            }
        }
        return candidates.size();
    }

    private static int floorCell(float value) { return (int) Math.floor(value / CELL_SIZE); }
    private static long cellKey(int x, int y) { return ((long) x << 32) ^ (y & 0xffffffffL); }
}
