package cn.dancingsnow.neoecoae.crafting.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable layout and bounds indices for nodes, routed edges and precomputed row frames. */
public final class GraphLayoutSnapshot {
    public static final int MATERIAL_WIDTH = 132;
    public static final int MATERIAL_HEIGHT = 76;
    public static final int PATTERN_WIDTH = 56;
    public static final int PATTERN_HEIGHT = 32;
    public static final int CYCLE_WIDTH = 150;
    public static final int CYCLE_HEIGHT = 72;
    public static final int COMPACT_MATERIAL_WIDTH = 20;
    public static final int COMPACT_MATERIAL_HEIGHT = 30;
    public static final int COMPACT_CYCLE_WIDTH = 96;
    public static final int COMPACT_CYCLE_HEIGHT = 34;
    public static final int COMPACT_FOLDER_WIDTH = COMPACT_MATERIAL_WIDTH;
    public static final int COMPACT_FOLDER_HEIGHT = COMPACT_MATERIAL_HEIGHT;
    public static final int COMPACT_REFERENCE_WIDTH = COMPACT_MATERIAL_WIDTH;
    public static final int COMPACT_REFERENCE_HEIGHT = COMPACT_MATERIAL_HEIGHT;

    public record Box(int nodeId, float x, float y, float width, float height) {
        public float centerX() { return x + width / 2; }
        public float centerY() { return y + height / 2; }
        public boolean intersects(float left, float top, float right, float bottom) {
            return x + width >= left && x <= right && y + height >= top && y <= bottom;
        }
        public boolean contains(float px, float py) { return px >= x && px <= x + width && py >= y && py <= y + height; }
    }

    /** World-space edge path, including attachment points and any bend points. */
    public record Point(float x, float y) {}

    public record Bounds(float left, float top, float right, float bottom) {
        public float width() { return Math.max(1, right - left); }
        public float height() { return Math.max(1, bottom - top); }
        public boolean intersects(Bounds other) {
            return right >= other.left && left <= other.right && bottom >= other.top && top <= other.bottom;
        }
        public Bounds padded(float amount) {
            return new Bounds(left - amount, top - amount, right + amount, bottom + amount);
        }
    }

    private final Map<Integer, Box> boxes;
    private final GraphSpatialIndex<Box> nodeIndex;
    private final GraphSpatialIndex<ClientCraftingGraph.Link> edgeIndex;
    private final GraphSpatialIndex<Bounds> rowIndex;
    private final List<Bounds> rowFrames;
    private final List<ClientCraftingGraph.Link> links;
    private final Map<ClientCraftingGraph.Link, List<Point>> edgeRoutes;
    private final Map<Integer, int[]> edgesByNode;
    private final Bounds bounds;
    private final long layoutNanos;
    private final long spatialIndexNanos;
    private final long version;

    GraphLayoutSnapshot(Map<Integer, Box> boxes, List<ClientCraftingGraph.Link> links, Bounds bounds,
            long layoutNanos, long version) {
        this(boxes, links, Map.of(), bounds, layoutNanos, version);
    }

    GraphLayoutSnapshot(Map<Integer, Box> boxes, List<ClientCraftingGraph.Link> links,
            Map<ClientCraftingGraph.Link, List<Point>> edgeRoutes, Bounds bounds, long layoutNanos, long version) {
        this.boxes = Map.copyOf(boxes);
        this.bounds = bounds;
        this.version = version;
        this.links = List.copyOf(links);
        Map<ClientCraftingGraph.Link, List<Point>> frozenRoutes = new HashMap<>();
        edgeRoutes.forEach((link, points) -> frozenRoutes.put(link, List.copyOf(points)));
        this.edgeRoutes = Map.copyOf(frozenRoutes);
        long spatialStarted = System.nanoTime();
        nodeIndex = new GraphSpatialIndex<>(this.boxes.values().stream()
            .map(box -> new GraphSpatialIndex.Entry<>(boxBounds(box), box)).toList());
        edgeIndex = new GraphSpatialIndex<>(this.links.stream()
            .map(link -> new GraphSpatialIndex.Entry<>(linkBounds(link), link)).toList());
        rowFrames = buildRowFrames();
        rowIndex = new GraphSpatialIndex<>(rowFrames.stream()
            .map(row -> new GraphSpatialIndex.Entry<>(row, row)).toList());

        Map<Integer, List<Integer>> mutableEdges = new HashMap<>();
        for (int i = 0; i < links.size(); i++) {
            var link = links.get(i);
            mutableEdges.computeIfAbsent(link.fromId(), ignored -> new ArrayList<>()).add(i);
            mutableEdges.computeIfAbsent(link.toId(), ignored -> new ArrayList<>()).add(i);
        }
        Map<Integer, int[]> frozenEdges = new HashMap<>();
        mutableEdges.forEach((key, ids) -> frozenEdges.put(key, ids.stream().mapToInt(Integer::intValue).toArray()));
        this.edgesByNode = frozenEdges;
        this.layoutNanos = layoutNanos;
        this.spatialIndexNanos = System.nanoTime() - spatialStarted;
    }

    public Map<Integer, Box> boxes() { return boxes; }
    public Box box(int id) { return boxes.get(id); }
    public List<ClientCraftingGraph.Link> links() { return links; }
    public Bounds bounds() { return bounds; }
    public Map<ClientCraftingGraph.Link, List<Point>> edgeRoutes() { return edgeRoutes; }
    public List<Point> edgePoints(ClientCraftingGraph.Link link) { return edgeRoutes.getOrDefault(link, List.of()); }
    public List<Bounds> rowFrames() { return rowFrames; }
    public int nodeCount(Bounds area) { return nodeIndex.count(area); }
    public int edgeCount(Bounds area) { return edgeIndex.count(area); }
    public List<Bounds> queryRowFrames(Bounds area) { return rowIndex.query(area, Integer.MAX_VALUE); }
    public List<ClientCraftingGraph.Link> linksAt(int nodeId) {
        int[] ids = edgesByNode.get(nodeId);
        if (ids == null) return List.of();
        List<ClientCraftingGraph.Link> result = new ArrayList<>(ids.length);
        for (int id : ids) result.add(links.get(id));
        return result;
    }
    public int bendPointCount() {
        return edgeRoutes.values().stream().mapToInt(points -> Math.max(0, points.size() - 2)).sum();
    }
    public long layoutNanos() { return layoutNanos; }
    public long spatialIndexNanos() { return spatialIndexNanos; }
    public long version() { return version; }

    public List<Box> query(float left, float top, float right, float bottom, float margin) {
        return query(new Bounds(left, top, right, bottom).padded(margin), Integer.MAX_VALUE);
    }

    public List<Box> query(Bounds area, int limit) {
        return nodeIndex.query(area, limit);
    }

    public List<ClientCraftingGraph.Link> queryLinks(float left, float top, float right, float bottom, float margin) {
        return queryLinks(new Bounds(left, top, right, bottom).padded(margin));
    }

    public List<ClientCraftingGraph.Link> queryLinks(Bounds area) {
        return edgeIndex.query(area, Integer.MAX_VALUE);
    }

    int candidateNodeCount(float left, float top, float right, float bottom, float margin) {
        return query(left, top, right, bottom, margin).size();
    }

    private static Bounds boxBounds(Box box) {
        return new Bounds(box.x(), box.y(), box.x() + box.width(), box.y() + box.height());
    }

    public Bounds linkBounds(ClientCraftingGraph.Link link) {
        var route = edgePoints(link);
        if (!route.isEmpty()) {
            float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
            float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
            for (var point : route) {
                left = Math.min(left, point.x());
                top = Math.min(top, point.y());
                right = Math.max(right, point.x());
                bottom = Math.max(bottom, point.y());
            }
            return new Bounds(left, top, right, bottom).padded(8);
        }
        var from = boxes.get(link.fromId());
        var to = boxes.get(link.toId());
        if (from == null || to == null) return new Bounds(0, 0, 0, 0);
        return new Bounds(Math.min(from.x(), to.x()), Math.min(from.y(), to.y()),
            Math.max(from.x() + from.width(), to.x() + to.width()),
            Math.max(from.y() + from.height(), to.y() + to.height())).padded(8);
    }

    private List<Bounds> buildRowFrames() {
        List<Box> ordered = new ArrayList<>(boxes.values());
        ordered.sort(Comparator.comparingDouble(Box::y).thenComparingDouble(Box::x));
        List<Bounds> result = new ArrayList<>();
        Bounds active = null;
        for (var box : ordered) {
            if (active == null || box.y() != active.top() || box.x() > active.right() + 0.001f) {
                if (active != null) result.add(active);
                active = boxBounds(box);
            } else {
                active = new Bounds(active.left(), active.top(), Math.max(active.right(), box.x() + box.width()),
                    Math.max(active.bottom(), box.y() + box.height()));
            }
        }
        if (active != null) result.add(active);
        return List.copyOf(result);
    }

    static float widthFor(ClientCraftingGraph.Node node) {
        return switch (node.kind()) {
            case MATERIAL -> MATERIAL_WIDTH;
            case PATTERN -> PATTERN_WIDTH;
            case CYCLE_GROUP, CYCLE_CLUSTER -> CYCLE_WIDTH;
            case FOLDER -> COMPACT_FOLDER_WIDTH;
            case REFERENCE -> COMPACT_REFERENCE_WIDTH;
        };
    }

    static float heightFor(ClientCraftingGraph.Node node) {
        return switch (node.kind()) {
            case MATERIAL -> MATERIAL_HEIGHT;
            case PATTERN -> PATTERN_HEIGHT;
            case CYCLE_GROUP, CYCLE_CLUSTER -> CYCLE_HEIGHT;
            case FOLDER -> COMPACT_FOLDER_HEIGHT;
            case REFERENCE -> COMPACT_REFERENCE_HEIGHT;
        };
    }

}
