package cn.dancingsnow.neoecoae.client.craftinggraph;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Compact top-down tree layout. It deliberately uses the projection's repeated occurrences, not DAG geometry. */
public final class CompactTreeLayout implements GraphLayoutEngine {
    public static final float LEVEL_GAP = 16;
    public static final float SIBLING_GAP = 8;

    @Override
    public GraphLayoutSnapshot layout(ClientCraftingGraph graph, long version) {
        if (!graph.isCompactTree() && graph.view() == ClientCraftingGraph.View.MAIN) {
            ClientCraftingGraph projected = CompactTreeProjection.project(graph,
                CompactTreeProjection.DEFAULT_DEPTH, Set.of(), Set.of());
            if (projected != graph) return layout(projected, version);
        }
        long started = System.nanoTime();
        Map<Integer, List<Integer>> children = children(graph);
        Map<Integer, Float> widths = subtreeWidths(graph, children);

        Map<Integer, GraphLayoutSnapshot.Box> boxes = new LinkedHashMap<>();
        List<Integer> roots = roots(graph, children);
        Deque<Placement> placements = new ArrayDeque<>();
        float x = 0;
        float[] rootPositions = new float[roots.size()];
        for (int i = roots.size() - 1; i >= 0; i--) {
            int root = roots.get(i);
            float width = widths.getOrDefault(root, nodeWidth(graph, root));
            rootPositions[i] = x;
            x += width + SIBLING_GAP * 3;
        }
        for (int i = roots.size() - 1; i >= 0; i--) {
            int root = roots.get(i);
            placements.push(new Placement(root, rootPositions[i], 0,
                widths.getOrDefault(root, nodeWidth(graph, root))));
        }
        place(placements, children, widths, graph, boxes);

        Map<ClientCraftingGraph.Link, List<GraphLayoutSnapshot.Point>> routes = new LinkedHashMap<>();
        for (var link : graph.links()) {
            var from = boxes.get(link.fromId());
            var to = boxes.get(link.toId());
            if (from != null && to != null) routes.put(link, route(from, to));
        }
        float right = 1;
        float bottom = 1;
        for (var box : boxes.values()) {
            right = Math.max(right, box.x() + box.width());
            bottom = Math.max(bottom, box.y() + box.height());
        }
        for (var points : routes.values()) for (var point : points) {
            right = Math.max(right, point.x());
            bottom = Math.max(bottom, point.y());
        }
        return new GraphLayoutSnapshot(boxes, graph.links(), routes,
            new GraphLayoutSnapshot.Bounds(0, 0, right, bottom), System.nanoTime() - started, version);
    }

    static float nodeWidth(ClientCraftingGraph graph, int id) {
        return switch (graph.nodes().get(id).kind()) {
            case MATERIAL -> GraphLayoutSnapshot.COMPACT_MATERIAL_WIDTH;
            case CYCLE_GROUP -> GraphLayoutSnapshot.COMPACT_CYCLE_WIDTH;
            case FOLDER -> GraphLayoutSnapshot.COMPACT_FOLDER_WIDTH;
            case REFERENCE -> GraphLayoutSnapshot.COMPACT_REFERENCE_WIDTH;
            case PATTERN -> GraphLayoutSnapshot.PATTERN_WIDTH;
        };
    }

    static float nodeHeight(ClientCraftingGraph graph, int id) {
        return switch (graph.nodes().get(id).kind()) {
            case MATERIAL -> GraphLayoutSnapshot.COMPACT_MATERIAL_HEIGHT;
            case CYCLE_GROUP -> GraphLayoutSnapshot.COMPACT_CYCLE_HEIGHT;
            case FOLDER -> GraphLayoutSnapshot.COMPACT_FOLDER_HEIGHT;
            case REFERENCE -> GraphLayoutSnapshot.COMPACT_REFERENCE_HEIGHT;
            case PATTERN -> GraphLayoutSnapshot.PATTERN_HEIGHT;
        };
    }

    private static Map<Integer, List<Integer>> children(ClientCraftingGraph graph) {
        Map<Integer, List<Integer>> result = new LinkedHashMap<>();
        for (var link : graph.links()) result.computeIfAbsent(link.fromId(), ignored -> new ArrayList<>()).add(link.toId());
        return result;
    }

    private static List<Integer> roots(ClientCraftingGraph graph, Map<Integer, List<Integer>> children) {
        Set<Integer> childIds = new HashSet<>(children.values().stream().flatMap(List::stream).toList());
        List<Integer> roots = new ArrayList<>();
        if (graph.nodes().containsKey(graph.rootId())) roots.add(graph.rootId());
        graph.nodes().keySet().stream().filter(id -> !childIds.contains(id) && !roots.contains(id)).sorted()
            .forEach(roots::add);
        return roots;
    }

    private static Map<Integer, Float> subtreeWidths(ClientCraftingGraph graph,
            Map<Integer, List<Integer>> children) {
        Map<Integer, Float> widths = new HashMap<>();
        Set<Integer> scheduled = new HashSet<>();
        Deque<WidthFrame> stack = new ArrayDeque<>();
        for (int id : graph.nodes().keySet()) {
            if (widths.containsKey(id) || !scheduled.add(id)) continue;
            stack.push(new WidthFrame(id, false));
            while (!stack.isEmpty()) {
                WidthFrame frame = stack.pop();
                if (widths.containsKey(frame.id())) continue;
                List<Integer> childIds = children.getOrDefault(frame.id(), List.of());
                if (frame.exit()) {
                    float childWidth = 0;
                    for (int child : childIds) childWidth += widths.getOrDefault(child, nodeWidth(graph, child));
                    if (childIds.size() > 1) childWidth += SIBLING_GAP * (childIds.size() - 1);
                    widths.put(frame.id(), Math.max(nodeWidth(graph, frame.id()), childWidth));
                    continue;
                }
                stack.push(new WidthFrame(frame.id(), true));
                for (int i = childIds.size() - 1; i >= 0; i--) {
                    int child = childIds.get(i);
                    if (!widths.containsKey(child) && scheduled.add(child)) stack.push(new WidthFrame(child, false));
                }
            }
        }
        return widths;
    }

    private static void place(Deque<Placement> placements,
            Map<Integer, List<Integer>> children, Map<Integer, Float> widths, ClientCraftingGraph graph,
            Map<Integer, GraphLayoutSnapshot.Box> boxes) {
        while (!placements.isEmpty()) {
            Placement placement = placements.pop();
            int id = placement.id();
            float left = placement.left();
            float top = placement.top();
            float allocatedWidth = placement.allocatedWidth();
            float width = nodeWidth(graph, id);
            float height = nodeHeight(graph, id);
            float nodeX = left + (allocatedWidth - width) / 2;
            boxes.put(id, new GraphLayoutSnapshot.Box(id, nodeX, top, width, height));
            List<Integer> childIds = children.getOrDefault(id, List.of());
            if (childIds.isEmpty()) continue;
            float childTotal = (float) childIds.stream()
                .mapToDouble(child -> widths.getOrDefault(child, nodeWidth(graph, child))).sum()
                + SIBLING_GAP * (childIds.size() - 1);
            float childLeft = left + (allocatedWidth - childTotal) / 2;
            float childTop = top + height + LEVEL_GAP;
            float[] positions = new float[childIds.size()];
            for (int i = 0; i < childIds.size(); i++) {
                positions[i] = childLeft;
                childLeft += widths.getOrDefault(childIds.get(i), nodeWidth(graph, childIds.get(i)))
                    + SIBLING_GAP;
            }
            for (int i = childIds.size() - 1; i >= 0; i--) {
                int child = childIds.get(i);
                placements.push(new Placement(child, positions[i], childTop,
                    widths.getOrDefault(child, nodeWidth(graph, child))));
            }
        }
    }

    private static List<GraphLayoutSnapshot.Point> route(GraphLayoutSnapshot.Box from,
            GraphLayoutSnapshot.Box to) {
        float startX = from.centerX();
        float startY = from.y() + from.height();
        float endX = to.centerX();
        float endY = to.y();
        float middleY = (startY + endY) / 2;
        List<GraphLayoutSnapshot.Point> points = new ArrayList<>();
        append(points, startX, startY);
        append(points, startX, middleY);
        append(points, endX, middleY);
        append(points, endX, endY);
        return List.copyOf(points);
    }

    private static void append(List<GraphLayoutSnapshot.Point> points, float x, float y) {
        var point = new GraphLayoutSnapshot.Point(x, y);
        if (points.isEmpty() || !points.getLast().equals(point)) points.add(point);
    }

    private record WidthFrame(int id, boolean exit) {}
    private record Placement(int id, float left, float top, float allocatedWidth) {}
}
