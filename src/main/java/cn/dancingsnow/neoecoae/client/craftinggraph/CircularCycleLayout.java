package cn.dancingsnow.neoecoae.client.craftinggraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stable circular layout used for one SCC so cycle structure stays visually obvious. */
public final class CircularCycleLayout implements GraphLayoutEngine {
    private static final float PADDING = 32;
    private static final float MIN_RADIUS = 72;
    private static final float SATELLITE_GAP = 40;

    @Override
    public GraphLayoutSnapshot layout(ClientCraftingGraph graph, long version) {
        long started = System.nanoTime();
        List<Integer> boundaryInputs = materialIds(graph, graph::isExternalInput);
        List<Integer> boundaryOutputs = materialIds(graph, id -> graph.isBoundaryOutput(id)
            && !graph.isExternalInput(id));
        Set<Integer> boundary = new LinkedHashSet<>(boundaryInputs);
        boundary.addAll(boundaryOutputs);
        List<Integer> ring = ringOrder(graph, graph.nodes().keySet().stream()
            .filter(id -> !boundary.contains(id)).sorted().toList());

        float largestNode = GraphLayoutSnapshot.COMPACT_MATERIAL_HEIGHT;
        float radius = Math.max(MIN_RADIUS,
            (float) (Math.max(1, ring.size()) * (largestNode + 22) / (2 * Math.PI)));
        float centerX = 0;
        float centerY = 0;

        Map<Integer, GraphLayoutSnapshot.Box> boxes = new LinkedHashMap<>();
        Map<Integer, Integer> ringIndex = new HashMap<>();
        for (int i = 0; i < ring.size(); i++) {
            int id = ring.get(i);
            ringIndex.put(id, i);
            double angle = -Math.PI / 2 + 2 * Math.PI * i / Math.max(1, ring.size());
            boxes.put(id, centeredBox(graph.nodes().get(id), centerX + radius * Math.cos(angle),
                centerY + radius * Math.sin(angle)));
        }

        List<Integer> satellites = new ArrayList<>(boundaryInputs);
        satellites.addAll(boundaryOutputs);
        placeBoundarySatellites(graph, boxes, satellites, ringIndex, ring.size(), centerX, centerY, radius,
            largestNode);

        float minX = (float) boxes.values().stream().mapToDouble(GraphLayoutSnapshot.Box::x).min().orElse(0);
        float minY = (float) boxes.values().stream().mapToDouble(GraphLayoutSnapshot.Box::y).min().orElse(0);
        float shiftX = PADDING - minX;
        float shiftY = PADDING - minY;
        boxes.replaceAll((id, box) -> new GraphLayoutSnapshot.Box(id, box.x() + shiftX, box.y() + shiftY,
            box.width(), box.height()));
        centerX += shiftX;
        centerY += shiftY;

        Map<ClientCraftingGraph.Link, List<GraphLayoutSnapshot.Point>> routes = new LinkedHashMap<>();
        for (var link : graph.links()) {
            var from = boxes.get(link.fromId());
            var to = boxes.get(link.toId());
            if (from == null || to == null) continue;
            Integer fromIndex = ringIndex.get(link.fromId());
            Integer toIndex = ringIndex.get(link.toId());
            if (fromIndex != null && toIndex != null) {
                routes.put(link, ringRoute(from, to, fromIndex, toIndex, ring.size(), centerX, centerY));
            } else {
                routes.put(link, boundaryRoute(from, to));
            }
        }
        GraphLayoutSnapshot.Bounds bounds = bounds(boxes, routes);
        return new GraphLayoutSnapshot(boxes, graph.links(), routes, bounds,
            System.nanoTime() - started, version);
    }

    private static List<Integer> materialIds(ClientCraftingGraph graph,
            java.util.function.IntPredicate predicate) {
        return graph.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.MATERIAL && predicate.test(node.id()))
            .map(ClientCraftingGraph.Node::id).sorted().toList();
    }

    /**
     * Starts with an actual directed cycle, then inserts any extra SCC nodes beside a connected ring node.
     * Materials and recipes therefore alternate on one circumference instead of forming two unrelated rings.
     */
    private static List<Integer> ringOrder(ClientCraftingGraph graph, List<Integer> coreIds) {
        if (coreIds.size() <= 1) return coreIds;
        Set<Integer> core = Set.copyOf(coreIds);
        List<Integer> starts = new ArrayList<>(coreIds);
        starts.sort(Comparator.comparingInt(id -> id == graph.rootId() ? Integer.MIN_VALUE : id));
        List<Integer> cycle = new ArrayList<>();
        for (int start : starts) {
            List<Integer> found = findDirectedCycle(graph, start, core);
            if (!found.isEmpty()) {
                cycle.addAll(found);
                break;
            }
        }
        if (cycle.isEmpty()) cycle.addAll(starts);
        rotateTo(cycle, graph.rootId());

        Set<Integer> placed = new LinkedHashSet<>(cycle);
        List<Integer> remaining = coreIds.stream().filter(id -> !placed.contains(id)).toList();
        boolean progress;
        do {
            progress = false;
            for (int id : remaining) {
                if (placed.contains(id)) continue;
                int before = firstIndex(cycle, graph.downstream(id));
                if (before >= 0) {
                    cycle.add(before, id);
                    placed.add(id);
                    progress = true;
                    continue;
                }
                int after = firstIndex(cycle, graph.upstream(id));
                if (after >= 0) {
                    cycle.add(after + 1, id);
                    placed.add(id);
                    progress = true;
                }
            }
        } while (progress && placed.size() < coreIds.size());
        for (int id : coreIds) if (placed.add(id)) cycle.add(id);
        return List.copyOf(cycle);
    }

    private static List<Integer> findDirectedCycle(ClientCraftingGraph graph, int start, Set<Integer> core) {
        List<Integer> path = new ArrayList<>();
        Map<Integer, Integer> position = new HashMap<>();
        int current = start;
        while (core.contains(current) && !position.containsKey(current)) {
            position.put(current, path.size());
            path.add(current);
            var next = graph.downstream(current).stream().filter(core::contains).sorted().findFirst();
            if (next.isEmpty()) return List.of();
            current = next.get();
        }
        Integer cycleStart = position.get(current);
        return cycleStart == null ? List.of() : new ArrayList<>(path.subList(cycleStart, path.size()));
    }

    private static int firstIndex(List<Integer> order, Set<Integer> candidates) {
        for (int i = 0; i < order.size(); i++) if (candidates.contains(order.get(i))) return i;
        return -1;
    }

    private static void rotateTo(List<Integer> values, int id) {
        int index = values.indexOf(id);
        if (index <= 0) return;
        List<Integer> copy = new ArrayList<>(values);
        for (int i = 0; i < values.size(); i++) values.set(i, copy.get((i + index) % copy.size()));
    }

    /** Places non-cycle inputs and outputs as radial satellites beside the recipe they attach to. */
    private static void placeBoundarySatellites(ClientCraftingGraph graph,
            Map<Integer, GraphLayoutSnapshot.Box> boxes, List<Integer> ids, Map<Integer, Integer> ringIndex,
            int ringSize, float centerX, float centerY, float radius, float largestRingNode) {
        if (ids.isEmpty()) return;
        Map<Integer, List<Integer>> byAnchor = new LinkedHashMap<>();
        for (int id : ids) {
            int anchor = relatedRingIndex(graph, id, ringIndex);
            byAnchor.computeIfAbsent(anchor, ignored -> new ArrayList<>()).add(id);
        }
        float satelliteRadius = radius + largestRingNode / 2 + maxDiameter(graph, ids) / 2 + SATELLITE_GAP;
        float angularStep = (maxDiameter(graph, ids) + 26) / satelliteRadius;
        for (var entry : byAnchor.entrySet()) {
            List<Integer> group = entry.getValue().stream().sorted().toList();
            double baseAngle = entry.getKey() < 0 ? Math.PI
                : ringAngle(entry.getKey(), Math.max(1, ringSize));
            for (int i = 0; i < group.size(); i++) {
                int id = group.get(i);
                double angle = baseAngle + (i - (group.size() - 1) / 2d) * angularStep;
                boxes.put(id, centeredBox(graph.nodes().get(id), centerX + satelliteRadius * Math.cos(angle),
                    centerY + satelliteRadius * Math.sin(angle)));
            }
        }
    }

    private static int relatedRingIndex(ClientCraftingGraph graph, int id, Map<Integer, Integer> ringIndex) {
        return java.util.stream.Stream.concat(graph.upstream(id).stream(), graph.downstream(id).stream())
            .map(ringIndex::get).filter(java.util.Objects::nonNull).min(Integer::compareTo).orElse(-1);
    }

    private static float maxDiameter(ClientCraftingGraph graph, List<Integer> ids) {
        return (float) ids.stream().map(graph.nodes()::get).mapToDouble(CircularCycleLayout::diameter)
            .max().orElse(0d);
    }

    private static double ringAngle(int index, int count) {
        return -Math.PI / 2 + 2 * Math.PI * index / count;
    }

    private static GraphLayoutSnapshot.Box centeredBox(ClientCraftingGraph.Node node, double centerX, double centerY) {
        float width = GraphLayoutSnapshot.COMPACT_MATERIAL_WIDTH;
        float height = GraphLayoutSnapshot.COMPACT_MATERIAL_HEIGHT;
        return new GraphLayoutSnapshot.Box(node.id(), (float) centerX - width / 2,
            (float) centerY - height / 2, width, height);
    }

    private static List<GraphLayoutSnapshot.Point> ringRoute(GraphLayoutSnapshot.Box from,
            GraphLayoutSnapshot.Box to, int fromIndex, int toIndex, int count, float centerX, float centerY) {
        if (count == 2) {
            boolean rightLane = fromIndex == 0;
            float laneX = rightLane ? Math.max(from.x() + from.width(), to.x() + to.width()) + 34
                : Math.min(from.x(), to.x()) - 34;
            var control = new GraphLayoutSnapshot.Point(laneX, (from.centerY() + to.centerY()) / 2);
            return quadraticRoute(boundary(from, control.x(), control.y()), control,
                boundary(to, control.x(), control.y()));
        }
        int forward = Math.floorMod(toIndex - fromIndex, count);
        if (forward == 1 || forward == count - 1) {
            int direction = forward == 1 ? 1 : -1;
            double middleAngle = ringAngle(fromIndex, count) + direction * Math.PI / count;
            float arcRadius = (float) Math.hypot(from.centerX() - centerX, from.centerY() - centerY) + 20;
            var control = radialPoint(centerX, centerY, arcRadius, middleAngle);
            return quadraticRoute(boundary(from, control.x(), control.y()), control,
                boundary(to, control.x(), control.y()));
        }

        // As in circular-layout exterior routing, chords that skip ring neighbors travel outside the circle.
        int signedSteps = forward <= count / 2 ? forward : forward - count;
        int segments = Math.max(4, Math.abs(signedSteps) * 3);
        float fromRadius = (float) Math.hypot(from.centerX() - centerX, from.centerY() - centerY);
        float toRadius = (float) Math.hypot(to.centerX() - centerX, to.centerY() - centerY);
        float outerRadius = Math.max(fromRadius, toRadius)
            + Math.max(Math.max(from.width(), from.height()), Math.max(to.width(), to.height())) / 2 + 34;
        List<GraphLayoutSnapshot.Point> route = new ArrayList<>();
        double startAngle = ringAngle(fromIndex, count);
        double angleStep = signedSteps * 2 * Math.PI / count / segments;
        var firstOuter = radialPoint(centerX, centerY, outerRadius, startAngle);
        append(route, boundary(from, firstOuter.x(), firstOuter.y()));
        for (int i = 0; i <= segments; i++) {
            append(route, radialPoint(centerX, centerY, outerRadius, startAngle + angleStep * i));
        }
        var lastOuter = route.getLast();
        append(route, boundary(to, lastOuter.x(), lastOuter.y()));
        return List.copyOf(route);
    }

    private static List<GraphLayoutSnapshot.Point> boundaryRoute(GraphLayoutSnapshot.Box from,
            GraphLayoutSnapshot.Box to) {
        float dx = to.centerX() - from.centerX();
        float dy = to.centerY() - from.centerY();
        float length = Math.max(1, (float) Math.hypot(dx, dy));
        float bend = Math.min(36, length * 0.16f) * (((from.nodeId() ^ to.nodeId()) & 1) == 0 ? 1 : -1);
        var control = new GraphLayoutSnapshot.Point((from.centerX() + to.centerX()) / 2 - dy / length * bend,
            (from.centerY() + to.centerY()) / 2 + dx / length * bend);
        return quadraticRoute(boundary(from, control.x(), control.y()), control,
            boundary(to, control.x(), control.y()));
    }

    private static List<GraphLayoutSnapshot.Point> quadraticRoute(GraphLayoutSnapshot.Point start,
            GraphLayoutSnapshot.Point control, GraphLayoutSnapshot.Point end) {
        List<GraphLayoutSnapshot.Point> result = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            float t = i / 12f;
            float inverse = 1 - t;
            append(result, new GraphLayoutSnapshot.Point(
                inverse * inverse * start.x() + 2 * inverse * t * control.x() + t * t * end.x(),
                inverse * inverse * start.y() + 2 * inverse * t * control.y() + t * t * end.y()));
        }
        return List.copyOf(result);
    }

    private static GraphLayoutSnapshot.Point radialPoint(float centerX, float centerY, float radius, double angle) {
        return new GraphLayoutSnapshot.Point(centerX + radius * (float) Math.cos(angle),
            centerY + radius * (float) Math.sin(angle));
    }

    private static void append(List<GraphLayoutSnapshot.Point> points, GraphLayoutSnapshot.Point point) {
        if (points.isEmpty() || !points.getLast().equals(point)) points.add(point);
    }

    private static GraphLayoutSnapshot.Point boundary(GraphLayoutSnapshot.Box box, float targetX, float targetY) {
        float dx = targetX - box.centerX();
        float dy = targetY - box.centerY();
        if (Math.abs(dx) >= Math.abs(dy)) {
            return new GraphLayoutSnapshot.Point(dx >= 0 ? box.x() + box.width() : box.x(), box.centerY());
        }
        return new GraphLayoutSnapshot.Point(box.centerX(), dy >= 0 ? box.y() + box.height() : box.y());
    }

    private static double diameter(ClientCraftingGraph.Node node) {
        return GraphLayoutSnapshot.COMPACT_MATERIAL_HEIGHT;
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
        if (minX == Float.MAX_VALUE) return new GraphLayoutSnapshot.Bounds(0, 0, 1, 1);
        return new GraphLayoutSnapshot.Bounds(0, 0, Math.max(1, right), Math.max(1, bottom));
    }
}
