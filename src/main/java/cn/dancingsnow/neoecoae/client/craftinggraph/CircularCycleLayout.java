package cn.dancingsnow.neoecoae.client.craftinggraph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable circular layout used for one SCC so cycle structure stays visually obvious. */
public final class CircularCycleLayout implements GraphLayoutEngine {
    private static final float PADDING = 32;
    private static final float MIN_RADIUS = 170;

    @Override
    public GraphLayoutSnapshot layout(ClientCraftingGraph graph, long version) {
        long started = System.nanoTime();
        List<Integer> materials = graph.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.MATERIAL)
            .map(ClientCraftingGraph.Node::id).sorted().toList();
        List<Integer> otherNodes = graph.nodes().values().stream()
            .filter(node -> node.kind() != ClientCraftingGraph.Kind.MATERIAL)
            .map(ClientCraftingGraph.Node::id).sorted().toList();
        int ringCount = Math.max(1, materials.size());
        float largestNode = (float) materials.stream().map(graph.nodes()::get)
            .mapToDouble(CircularCycleLayout::diameter).max().orElse(GraphLayoutSnapshot.PATTERN_WIDTH);
        float radius = Math.max(MIN_RADIUS, (float) (ringCount * (largestNode + 58) / (2 * Math.PI)));
        float center = radius + PADDING + largestNode / 2;

        Map<Integer, GraphLayoutSnapshot.Box> boxes = new LinkedHashMap<>();
        Map<Integer, Double> materialAngles = new LinkedHashMap<>();
        for (int i = 0; i < materials.size(); i++) {
            int id = materials.get(i);
            double angle = -Math.PI / 2 + 2 * Math.PI * i / ringCount;
            materialAngles.put(id, angle);
            boxes.put(id, centeredBox(graph.nodes().get(id), center + radius * Math.cos(angle),
                center + radius * Math.sin(angle)));
        }
        for (int i = 0; i < otherNodes.size(); i++) {
            int id = otherNodes.get(i);
            var node = graph.nodes().get(id);
            double angle = relatedAngle(graph, id, materialAngles, -Math.PI / 2 + 2 * Math.PI * i
                / Math.max(1, otherNodes.size()));
            float innerRadius = Math.max(84, radius * 0.58f);
            boxes.put(id, centeredBox(node, center + innerRadius * Math.cos(angle),
                center + innerRadius * Math.sin(angle)));
        }

        float minX = (float) boxes.values().stream().mapToDouble(GraphLayoutSnapshot.Box::x).min().orElse(0);
        float minY = (float) boxes.values().stream().mapToDouble(GraphLayoutSnapshot.Box::y).min().orElse(0);
        float shiftX = PADDING - minX;
        float shiftY = PADDING - minY;
        boxes.replaceAll((id, box) -> new GraphLayoutSnapshot.Box(id, box.x() + shiftX, box.y() + shiftY,
            box.width(), box.height()));
        float shiftedCenterX = center + shiftX;
        float shiftedCenterY = center + shiftY;

        Map<ClientCraftingGraph.Link, List<GraphLayoutSnapshot.Point>> routes = new LinkedHashMap<>();
        for (var link : graph.links()) {
            var from = boxes.get(link.fromId());
            var to = boxes.get(link.toId());
            if (from != null && to != null) {
                routes.put(link, orthogonalCycleRoute(from, to, shiftedCenterX, shiftedCenterY));
            }
        }
        GraphLayoutSnapshot.Bounds bounds = bounds(boxes, routes);
        return new GraphLayoutSnapshot(boxes, graph.links(), routes, bounds,
            System.nanoTime() - started, version);
    }

    private static double relatedAngle(ClientCraftingGraph graph, int id, Map<Integer, Double> angles,
            double fallback) {
        double x = 0;
        double y = 0;
        for (int neighbor : graph.upstream(id)) {
            Double angle = angles.get(neighbor);
            if (angle != null) { x += Math.cos(angle); y += Math.sin(angle); }
        }
        for (int neighbor : graph.downstream(id)) {
            Double angle = angles.get(neighbor);
            if (angle != null) { x += Math.cos(angle); y += Math.sin(angle); }
        }
        return Math.abs(x) + Math.abs(y) < 0.001 ? fallback : Math.atan2(y, x);
    }

    private static GraphLayoutSnapshot.Box centeredBox(ClientCraftingGraph.Node node, double centerX, double centerY) {
        float width = GraphLayoutSnapshot.widthFor(node);
        float height = GraphLayoutSnapshot.heightFor(node);
        return new GraphLayoutSnapshot.Box(node.id(), (float) centerX - width / 2,
            (float) centerY - height / 2, width, height);
    }

    private static List<GraphLayoutSnapshot.Point> orthogonalCycleRoute(GraphLayoutSnapshot.Box from,
            GraphLayoutSnapshot.Box to, float centerX, float centerY) {
        GraphLayoutSnapshot.Point start = boundary(from, to.centerX(), to.centerY());
        GraphLayoutSnapshot.Point end = boundary(to, from.centerX(), from.centerY());
        List<GraphLayoutSnapshot.Point> points = new ArrayList<>();
        append(points, start);
        append(points, new GraphLayoutSnapshot.Point(centerX, start.y()));
        append(points, new GraphLayoutSnapshot.Point(centerX, end.y()));
        append(points, end);
        return List.copyOf(points);
    }

    private static GraphLayoutSnapshot.Point boundary(GraphLayoutSnapshot.Box box, float targetX, float targetY) {
        float dx = targetX - box.centerX();
        float dy = targetY - box.centerY();
        if (Math.abs(dx) >= Math.abs(dy)) {
            return new GraphLayoutSnapshot.Point(dx >= 0 ? box.x() + box.width() : box.x(), box.centerY());
        }
        return new GraphLayoutSnapshot.Point(box.centerX(), dy >= 0 ? box.y() + box.height() : box.y());
    }

    private static void append(List<GraphLayoutSnapshot.Point> points, GraphLayoutSnapshot.Point point) {
        if (points.isEmpty() || !points.getLast().equals(point)) points.add(point);
    }

    private static double diameter(ClientCraftingGraph.Node node) {
        return Math.max(GraphLayoutSnapshot.widthFor(node), GraphLayoutSnapshot.heightFor(node));
    }

    private static GraphLayoutSnapshot.Bounds bounds(Map<Integer, GraphLayoutSnapshot.Box> boxes,
            Map<ClientCraftingGraph.Link, List<GraphLayoutSnapshot.Point>> routes) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float right = 1;
        float bottom = 1;
        for (var box : boxes.values()) {
            minX = Math.min(minX, box.x());
            minY = Math.min(minY, box.y());
            right = Math.max(right, box.x() + box.width());
            bottom = Math.max(bottom, box.y() + box.height());
        }
        for (var route : routes.values()) for (var point : route) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            right = Math.max(right, point.x());
            bottom = Math.max(bottom, point.y());
        }
        if (minX != Float.MAX_VALUE) right += minX;
        if (minY != Float.MAX_VALUE) bottom += minY;
        return new GraphLayoutSnapshot.Bounds(0, 0, right, bottom);
    }
}
