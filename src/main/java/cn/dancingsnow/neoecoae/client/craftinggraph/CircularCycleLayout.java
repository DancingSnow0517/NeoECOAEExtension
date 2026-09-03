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
        GraphPortAllocator ports = new GraphPortAllocator();
        Map<ClientCraftingGraph.Link, BoundaryPort> boundaryPorts = boundaryPorts(graph, boxes, boundary, ports);
        List<ClientCraftingGraph.Link> orderedLinks = graph.links().stream()
            .sorted(Comparator.comparingInt((ClientCraftingGraph.Link link) -> link.kind().ordinal())
                .thenComparingInt(ClientCraftingGraph.Link::fromId)
                .thenComparingInt(ClientCraftingGraph.Link::toId))
            .toList();
        for (var link : orderedLinks) {
            var from = boxes.get(link.fromId());
            var to = boxes.get(link.toId());
            if (from == null || to == null) continue;
            Integer fromIndex = ringIndex.get(link.fromId());
            Integer toIndex = ringIndex.get(link.toId());
            if (fromIndex != null && toIndex != null) {
                routes.put(link, ringRoute(link, from, to, fromIndex, toIndex, ring.size(), centerX, centerY, ports));
            } else {
                routes.put(link, boundaryRoute(link, from, to, ports, boundaryPorts.get(link)));
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
        float satelliteStep = maxDiameter(graph, ids) + 26;
        for (var entry : byAnchor.entrySet()) {
            List<Integer> group = entry.getValue().stream().sorted().toList();
            double baseAngle = entry.getKey() < 0 ? Math.PI
                : ringAngle(entry.getKey(), Math.max(1, ringSize));
            GraphPortAllocator.Side side = sideForAngle(baseAngle);
            var anchor = radialPoint(centerX, centerY, radius, baseAngle);
            for (int i = 0; i < group.size(); i++) {
                int id = group.get(i);
                float offset = (float) ((i - (group.size() - 1) / 2d) * satelliteStep);
                float x = switch (side) {
                    case LEFT -> centerX - satelliteRadius;
                    case RIGHT -> centerX + satelliteRadius;
                    case TOP, BOTTOM -> anchor.x() + offset;
                };
                float y = switch (side) {
                    case TOP -> centerY - satelliteRadius;
                    case BOTTOM -> centerY + satelliteRadius;
                    case LEFT, RIGHT -> anchor.y() + offset;
                };
                boxes.put(id, centeredBox(graph.nodes().get(id), x, y));
            }
        }
    }

    private static GraphPortAllocator.Side sideForAngle(double angle) {
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? GraphPortAllocator.Side.RIGHT
            : GraphPortAllocator.Side.LEFT;
        return dy >= 0 ? GraphPortAllocator.Side.BOTTOM : GraphPortAllocator.Side.TOP;
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

    private static List<GraphLayoutSnapshot.Point> ringRoute(ClientCraftingGraph.Link link,
            GraphLayoutSnapshot.Box from, GraphLayoutSnapshot.Box to, int fromIndex, int toIndex, int count,
            float centerX, float centerY, GraphPortAllocator ports) {
        if (count == 2) {
            // A two-node cycle normally consists of one material -> pattern input and one
            // pattern -> material output. Keep their ports on opposite rectangle sides;
            // automatic nearest-boundary selection would attach both routes to the same
            // top/bottom edge when the nodes happen to be vertically aligned.
            boolean rightLane = switch (link.kind()) {
                case PATTERN_OUTPUT, BYPRODUCT -> true;
                case PATTERN_INPUT -> false;
                case CYCLE_INTERNAL -> fromIndex == 0;
            };
            float laneX = rightLane ? Math.max(from.x() + from.width(), to.x() + to.width()) + 34
                : Math.min(from.x(), to.x()) - 34;
            var control = new GraphLayoutSnapshot.Point(laneX, (from.centerY() + to.centerY()) / 2);
            GraphPortAllocator.Side side = rightLane ? GraphPortAllocator.Side.RIGHT : GraphPortAllocator.Side.LEFT;
            return quadraticRoute(ports.attach(from, control.x(), control.y(), true, side), control,
                ports.attach(to, control.x(), control.y(), false, side));
        }
        int forward = Math.floorMod(toIndex - fromIndex, count);
        if (forward == 1 || forward == count - 1) {
            int direction = forward == 1 ? 1 : -1;
            double middleAngle = ringAngle(fromIndex, count) + direction * Math.PI / count;
            float arcRadius = (float) Math.hypot(from.centerX() - centerX, from.centerY() - centerY) + 20;
            var control = radialPoint(centerX, centerY, arcRadius, middleAngle);
            return quadraticRoute(ports.attach(from, control.x(), control.y(), true, null), control,
                ports.attach(to, control.x(), control.y(), false, null));
        }

        // Long exterior arcs make one highlighted edge look like a second, huge cycle around the
        // whole screen. Keep a compact bowed chord for non-neighbor links instead.
        float dx = to.centerX() - from.centerX();
        float dy = to.centerY() - from.centerY();
        float length = Math.max(1, (float) Math.hypot(dx, dy));
        int signedSteps = forward <= count / 2 ? forward : forward - count;
        float bend = Math.min(54, Math.max(18, length * 0.16f)) * (signedSteps >= 0 ? 1 : -1);
        var control = new GraphLayoutSnapshot.Point(
            (from.centerX() + to.centerX()) / 2 - dy / length * bend,
            (from.centerY() + to.centerY()) / 2 + dx / length * bend);
        return quadraticRoute(ports.attach(from, control.x(), control.y(), true, null), control,
            ports.attach(to, control.x(), control.y(), false, null));
    }

    private static List<GraphLayoutSnapshot.Point> boundaryRoute(ClientCraftingGraph.Link link,
            GraphLayoutSnapshot.Box from, GraphLayoutSnapshot.Box to, GraphPortAllocator ports,
            BoundaryPort boundaryPort) {
        if (boundaryPort != null) {
            boolean coreIsFrom = boundaryPort.nodeId() == link.fromId();
            GraphLayoutSnapshot.Box core = coreIsFrom ? from : to;
            GraphLayoutSnapshot.Box satellite = coreIsFrom ? to : from;
            GraphLayoutSnapshot.Point satellitePort = ports.attach(satellite, core.centerX(), core.centerY(),
                !coreIsFrom, null);
            GraphLayoutSnapshot.Point start = coreIsFrom ? boundaryPort.point() : satellitePort;
            GraphLayoutSnapshot.Point end = coreIsFrom ? satellitePort : boundaryPort.point();
            float length = Math.max(1, distance(start, end));
            float handle = Math.min(42, length * 0.28f);
            var outward = sideVector(boundaryPort.side());
            var coreControl = new GraphLayoutSnapshot.Point(boundaryPort.point().x() + outward.x() * handle,
                boundaryPort.point().y() + outward.y() * handle);
            var satelliteControl = new GraphLayoutSnapshot.Point(satellitePort.x() - outward.x() * handle,
                satellitePort.y() - outward.y() * handle);
            return coreIsFrom
                ? cubicRoute(start, coreControl, satelliteControl, end)
                : cubicRoute(start, satelliteControl, coreControl, end);
        }

        float dx = to.centerX() - from.centerX();
        float dy = to.centerY() - from.centerY();
        float length = Math.max(1, (float) Math.hypot(dx, dy));
        float bend = Math.min(36, length * 0.16f) * (((from.nodeId() ^ to.nodeId()) & 1) == 0 ? 1 : -1);
        var control = new GraphLayoutSnapshot.Point((from.centerX() + to.centerX()) / 2 - dy / length * bend,
            (from.centerY() + to.centerY()) / 2 + dx / length * bend);
        GraphLayoutSnapshot.Point start = ports.attach(from, control.x(), control.y(), true, null);
        GraphLayoutSnapshot.Point end = ports.attach(to, control.x(), control.y(), false, null);
        return quadraticRoute(start, control, end);
    }

    private static GraphLayoutSnapshot.Point sideVector(GraphPortAllocator.Side side) {
        return switch (side) {
            case TOP -> new GraphLayoutSnapshot.Point(0, -1);
            case RIGHT -> new GraphLayoutSnapshot.Point(1, 0);
            case BOTTOM -> new GraphLayoutSnapshot.Point(0, 1);
            case LEFT -> new GraphLayoutSnapshot.Point(-1, 0);
        };
    }

    /**
     * All external materials connected to one core node share its outward-facing side. Their attachment points
     * divide the complete side length evenly instead of competing for the two regular ring ports.
     */
    private static Map<ClientCraftingGraph.Link, BoundaryPort> boundaryPorts(ClientCraftingGraph graph,
            Map<Integer, GraphLayoutSnapshot.Box> boxes, Set<Integer> boundaryIds, GraphPortAllocator ports) {
        Map<Integer, List<BoundaryLink>> byCore = new LinkedHashMap<>();
        for (var link : graph.links()) {
            boolean fromBoundary = boundaryIds.contains(link.fromId());
            boolean toBoundary = boundaryIds.contains(link.toId());
            if (fromBoundary == toBoundary) continue;
            int coreId = fromBoundary ? link.toId() : link.fromId();
            int satelliteId = fromBoundary ? link.fromId() : link.toId();
            byCore.computeIfAbsent(coreId, ignored -> new ArrayList<>())
                .add(new BoundaryLink(link, satelliteId));
        }

        Map<ClientCraftingGraph.Link, BoundaryPort> result = new LinkedHashMap<>();
        for (var entry : byCore.entrySet()) {
            GraphLayoutSnapshot.Box core = boxes.get(entry.getKey());
            if (core == null) continue;
            float averageX = 0;
            float averageY = 0;
            int positioned = 0;
            for (var value : entry.getValue()) {
                GraphLayoutSnapshot.Box satellite = boxes.get(value.satelliteId());
                if (satellite == null) continue;
                averageX += satellite.centerX();
                averageY += satellite.centerY();
                positioned++;
            }
            if (positioned == 0) continue;
            GraphPortAllocator.Side side = GraphPortAllocator.nearestSide(core,
                averageX / positioned, averageY / positioned);
            ports.reserveSide(entry.getKey(), side);
            List<BoundaryLink> links = entry.getValue().stream()
                .sorted(Comparator.comparingDouble((BoundaryLink value) -> boundaryOrder(
                        boxes.get(value.satelliteId()), side))
                    .thenComparingInt(BoundaryLink::satelliteId)
                    .thenComparingInt(value -> value.link().kind().ordinal())
                    .thenComparingInt(value -> value.link().fromId())
                    .thenComparingInt(value -> value.link().toId()))
                .toList();
            for (int i = 0; i < links.size(); i++) {
                var value = links.get(i);
                result.put(value.link(), new BoundaryPort(entry.getKey(),
                    GraphPortAllocator.evenlySpacedPoint(core, side, i, links.size()), side));
            }
        }
        return Map.copyOf(result);
    }

    private static double boundaryOrder(GraphLayoutSnapshot.Box satellite, GraphPortAllocator.Side side) {
        if (satellite == null) return Double.POSITIVE_INFINITY;
        return side == GraphPortAllocator.Side.TOP || side == GraphPortAllocator.Side.BOTTOM
            ? satellite.centerX() : satellite.centerY();
    }

    private record BoundaryLink(ClientCraftingGraph.Link link, int satelliteId) {}
    private record BoundaryPort(int nodeId, GraphLayoutSnapshot.Point point, GraphPortAllocator.Side side) {}

    private static List<GraphLayoutSnapshot.Point> quadraticRoute(GraphLayoutSnapshot.Point start,
            GraphLayoutSnapshot.Point control, GraphLayoutSnapshot.Point end) {
        float controlLength = distance(start, control) + distance(control, end);
        int segments = Math.max(16, Math.min(64, (int) Math.ceil(controlLength / 3f)));
        List<GraphLayoutSnapshot.Point> result = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float inverse = 1 - t;
            append(result, new GraphLayoutSnapshot.Point(
                inverse * inverse * start.x() + 2 * inverse * t * control.x() + t * t * end.x(),
                inverse * inverse * start.y() + 2 * inverse * t * control.y() + t * t * end.y()));
        }
        return List.copyOf(result);
    }

    private static float distance(GraphLayoutSnapshot.Point one, GraphLayoutSnapshot.Point two) {
        return (float) Math.hypot(two.x() - one.x(), two.y() - one.y());
    }

    private static List<GraphLayoutSnapshot.Point> cubicRoute(GraphLayoutSnapshot.Point start,
            GraphLayoutSnapshot.Point firstControl, GraphLayoutSnapshot.Point secondControl,
            GraphLayoutSnapshot.Point end) {
        float controlLength = distance(start, firstControl) + distance(firstControl, secondControl)
            + distance(secondControl, end);
        int segments = Math.max(16, Math.min(64, (int) Math.ceil(controlLength / 3f)));
        List<GraphLayoutSnapshot.Point> result = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float inverse = 1 - t;
            append(result, new GraphLayoutSnapshot.Point(
                inverse * inverse * inverse * start.x()
                    + 3 * inverse * inverse * t * firstControl.x()
                    + 3 * inverse * t * t * secondControl.x()
                    + t * t * t * end.x(),
                inverse * inverse * inverse * start.y()
                    + 3 * inverse * inverse * t * firstControl.y()
                    + 3 * inverse * t * t * secondControl.y()
                    + t * t * t * end.y()));
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
