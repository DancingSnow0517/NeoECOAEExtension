package cn.dancingsnow.neoecoae.crafting.graph;

import java.util.List;
import java.util.Map;

/** Layout and uniform-grid spatial index. */
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
    }

    private final cn.dancingsnow.neoecoae.core.graph.presentation.GraphLayoutSnapshot delegate;
    private final Map<Integer, Box> boxes;
    private final List<ClientCraftingGraph.Link> links;
    private final Map<ClientCraftingGraph.Link, List<Point>> edgeRoutes;

    GraphLayoutSnapshot(Map<Integer, Box> boxes, List<ClientCraftingGraph.Link> links, Bounds bounds,
            long layoutNanos, long version) {
        this(boxes, links, Map.of(), bounds, layoutNanos, version);
    }

    GraphLayoutSnapshot(Map<Integer, Box> boxes, List<ClientCraftingGraph.Link> links,
            Map<ClientCraftingGraph.Link, List<Point>> routes, Bounds bounds, long layoutNanos, long version) {
        this(toCore(boxes, links, routes, bounds, layoutNanos, version));
    }

    GraphLayoutSnapshot(cn.dancingsnow.neoecoae.core.graph.presentation.GraphLayoutSnapshot delegate) {
        this.delegate = delegate;
        this.boxes = new java.util.LinkedHashMap<>();
        delegate.boxes().forEach((id, box) -> boxes.put(id, fromCore(box)));
        this.links = delegate.links().stream().map(ClientCraftingGraph.Link.class::cast).toList();
        this.edgeRoutes = new java.util.LinkedHashMap<>();
        delegate.edgeRoutes().forEach((link, points) -> edgeRoutes.put((ClientCraftingGraph.Link) link,
            points.stream().map(point -> new Point(point.x(), point.y())).toList()));
    }

    private static cn.dancingsnow.neoecoae.core.graph.presentation.GraphLayoutSnapshot toCore(
            Map<Integer, Box> boxes, List<ClientCraftingGraph.Link> links,
            Map<ClientCraftingGraph.Link, List<Point>> routes, Bounds bounds, long nanos, long version) {
        var coreBoxes = new java.util.LinkedHashMap<Integer, cn.dancingsnow.neoecoae.core.graph.presentation.GraphLayoutSnapshot.Box>();
        boxes.forEach((id, box) -> coreBoxes.put(id,
            new cn.dancingsnow.neoecoae.core.graph.presentation.GraphLayoutSnapshot.Box(
                id, box.x(), box.y(), box.width(), box.height())));
        var coreRoutes = new java.util.LinkedHashMap<cn.dancingsnow.neoecoae.core.graph.presentation.LayoutGraph.Link,
            List<cn.dancingsnow.neoecoae.core.graph.presentation.GraphLayoutSnapshot.Point>>();
        routes.forEach((link, points) -> coreRoutes.put(link, points.stream().map(point ->
            new cn.dancingsnow.neoecoae.core.graph.presentation.GraphLayoutSnapshot.Point(point.x(), point.y())).toList()));
        return new cn.dancingsnow.neoecoae.core.graph.presentation.GraphLayoutSnapshot(coreBoxes, links, coreRoutes,
            new cn.dancingsnow.neoecoae.core.graph.presentation.GraphLayoutSnapshot.Bounds(
                bounds.left(), bounds.top(), bounds.right(), bounds.bottom()), nanos, version);
    }

    private static Box fromCore(cn.dancingsnow.neoecoae.core.graph.presentation.GraphLayoutSnapshot.Box box) {
        return new Box(box.nodeId(), box.x(), box.y(), box.width(), box.height());
    }
    public Map<Integer, Box> boxes() { return boxes; }
    public Box box(int id) { return boxes.get(id); }
    public List<ClientCraftingGraph.Link> links() { return links; }
    public Bounds bounds() {
        var b = delegate.bounds();
        return new Bounds(b.left(), b.top(), b.right(), b.bottom());
    }
    public Map<ClientCraftingGraph.Link, List<Point>> edgeRoutes() { return edgeRoutes; }
    public List<Point> edgePoints(ClientCraftingGraph.Link link) { return edgeRoutes.getOrDefault(link, List.of()); }
    public int bendPointCount() { return delegate.bendPointCount(); }
    public long layoutNanos() { return delegate.layoutNanos(); }
    public long spatialIndexNanos() { return delegate.spatialIndexNanos(); }
    public long version() { return delegate.version(); }
    public List<Box> query(float left, float top, float right, float bottom, float margin) {
        return delegate.query(left, top, right, bottom, margin).stream().map(box -> boxes.get(box.nodeId())).toList();
    }
    public List<ClientCraftingGraph.Link> queryLinks(float left, float top, float right, float bottom, float margin) {
        return delegate.queryLinks(left, top, right, bottom, margin).stream().map(ClientCraftingGraph.Link.class::cast).toList();
    }
    int candidateNodeCount(float left, float top, float right, float bottom, float margin) {
        return delegate.candidateNodeCount(left, top, right, bottom, margin);
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
