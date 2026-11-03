package cn.dancingsnow.neoecoae.crafting.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unrolls one SCC into a left-to-right production strip.
 *
 * <p>Core nodes keep their directed-cycle order on one row, so every forward step is a short horizontal edge.
 * Edges that jump back (the loop closure) or skip ahead run through dedicated horizontal lanes below the row.
 * External inputs drop in from above and boundary outputs leave below. Every route is orthogonal, which keeps
 * pixel-aligned GUI lines crisp and makes the flow direction readable at any zoom.</p>
 */
public final class CycleStripLayout implements GraphLayoutEngine {
    public static final float MATERIAL_WIDTH = 36;
    public static final float MATERIAL_HEIGHT = 36;
    public static final float PATTERN_WIDTH = 44;
    public static final float PATTERN_HEIGHT = 36;

    private static final float PADDING = 32;
    private static final float NODE_GAP = 46;
    private static final float BOUNDARY_GAP = 10;
    private static final float INPUT_RISE = 70;
    private static final float INPUT_BEND_STEP = 5;
    private static final float LANE_START = 18;
    private static final float LANE_STEP = 12;
    private static final float LANE_MARGIN = 6;
    private static final float OUTPUT_DROP = 34;
    private static final float PARALLEL_STEP = 6;

    @Override
    public GraphLayoutSnapshot layout(ClientCraftingGraph graph, long version) {
        long started = System.nanoTime();
        List<Integer> inputs = materialIds(graph, graph::isExternalInput);
        List<Integer> outputs = materialIds(graph, id -> graph.isBoundaryOutput(id) && !graph.isExternalInput(id));
        Set<Integer> boundary = new LinkedHashSet<>(inputs);
        boundary.addAll(outputs);
        List<Integer> order = new ArrayList<>(CircularCycleLayout.ringOrder(graph, graph.nodes().keySet().stream()
            .filter(id -> !boundary.contains(id)).sorted().toList()));
        moveToEnd(order, graph.rootId());
        Map<Integer, Integer> index = new HashMap<>();
        for (int i = 0; i < order.size(); i++) index.put(order.get(i), i);

        Map<Integer, GraphLayoutSnapshot.Box> boxes = new LinkedHashMap<>();
        float x = 0;
        float coreTop = 0;
        float coreBottom = 0;
        for (int id : order) {
            var node = graph.nodes().get(id);
            float width = width(node);
            float height = height(node);
            boxes.put(id, new GraphLayoutSnapshot.Box(id, x, -height / 2, width, height));
            coreTop = Math.min(coreTop, -height / 2);
            coreBottom = Math.max(coreBottom, height / 2);
            x += width + NODE_GAP;
        }

        List<ClientCraftingGraph.Link> forward = new ArrayList<>();
        List<ClientCraftingGraph.Link> laneLinks = new ArrayList<>();
        List<ClientCraftingGraph.Link> inputLinks = new ArrayList<>();
        List<ClientCraftingGraph.Link> outputLinks = new ArrayList<>();
        List<ClientCraftingGraph.Link> otherLinks = new ArrayList<>();
        for (var link : sortedLinks(graph)) {
            Integer from = index.get(link.fromId());
            Integer to = index.get(link.toId());
            if (from != null && to != null) {
                if (to == from + 1) forward.add(link);
                else laneLinks.add(link);
            } else if (to != null && inputs.contains(link.fromId())) inputLinks.add(link);
            else if (from != null && outputs.contains(link.toId())) outputLinks.add(link);
            else otherLinks.add(link);
        }

        // Lanes: shortest spans sit closest to the row so longer loops wrap around them without crossing.
        Map<ClientCraftingGraph.Link, Integer> lanes = assignLanes(laneLinks, boxes);
        int laneCount = lanes.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        float laneBottom = coreBottom + (laneCount == 0 ? 0 : LANE_START + (laneCount - 1) * LANE_STEP);

        float inputCenterY = coreTop - INPUT_RISE;
        placeBoundary(graph, boxes, inputs, index, order, inputCenterY, true);
        float outputTop = laneBottom + OUTPUT_DROP;
        placeBoundary(graph, boxes, outputs, index, order, outputTop + MATERIAL_HEIGHT / 2, false);

        Map<ClientCraftingGraph.Link, Float> bottomPorts = bottomPorts(boxes, laneLinks, outputLinks);
        Map<ClientCraftingGraph.Link, Float> topPorts = topPorts(boxes, inputLinks);
        alignToPorts(boxes, inputs, inputLinks, topPorts, true);
        alignToPorts(boxes, outputs, outputLinks, bottomPorts, false);

        Map<ClientCraftingGraph.Link, List<GraphLayoutSnapshot.Point>> routes = new LinkedHashMap<>();
        routeForward(forward, boxes, routes);
        for (var link : laneLinks) {
            var from = boxes.get(link.fromId());
            var to = boxes.get(link.toId());
            float laneY = coreBottom + LANE_START + lanes.get(link) * LANE_STEP;
            float startX = bottomPorts.get(link);
            float endX = bottomPorts.get(reverseKey(link));
            routes.put(link, List.of(point(startX, bottom(from)), point(startX, laneY), point(endX, laneY),
                point(endX, bottom(to))));
        }
        routeInputs(inputLinks, boxes, topPorts, routes);
        for (var link : outputLinks) {
            var from = boxes.get(link.fromId());
            var to = boxes.get(link.toId());
            float startX = bottomPorts.get(link);
            if (Math.abs(startX - to.centerX()) < 0.5f) {
                routes.put(link, List.of(point(startX, bottom(from)), point(startX, to.y())));
            } else {
                float bendY = to.y() - 10;
                routes.put(link, List.of(point(startX, bottom(from)), point(startX, bendY),
                    point(to.centerX(), bendY), point(to.centerX(), to.y())));
            }
        }
        for (var link : otherLinks) {
            var from = boxes.get(link.fromId());
            var to = boxes.get(link.toId());
            if (from == null || to == null) continue;
            routes.put(link, List.of(point(from.centerX(), from.centerY()), point(to.centerX(), to.centerY())));
        }

        return normalized(graph, boxes, routes, System.nanoTime() - started, version);
    }

    private static List<Integer> materialIds(ClientCraftingGraph graph, java.util.function.IntPredicate predicate) {
        return graph.nodes().values().stream()
            .filter(node -> node.kind() == ClientCraftingGraph.Kind.MATERIAL && predicate.test(node.id()))
            .map(ClientCraftingGraph.Node::id).sorted().toList();
    }

    /** The required output ends the strip, so its output arrow leaves to the right and the loop closes below. */
    private static void moveToEnd(List<Integer> order, int id) {
        int position = order.indexOf(id);
        if (position < 0 || position == order.size() - 1) return;
        List<Integer> rotated = new ArrayList<>(order.size());
        for (int i = 1; i <= order.size(); i++) rotated.add(order.get((position + i) % order.size()));
        order.clear();
        order.addAll(rotated);
    }

    private static List<ClientCraftingGraph.Link> sortedLinks(ClientCraftingGraph graph) {
        return graph.links().stream()
            .sorted(Comparator.comparingInt((ClientCraftingGraph.Link link) -> link.kind().ordinal())
                .thenComparingInt(ClientCraftingGraph.Link::fromId)
                .thenComparingInt(ClientCraftingGraph.Link::toId)
                .thenComparingLong(ClientCraftingGraph.Link::amount))
            .toList();
    }

    private static Map<ClientCraftingGraph.Link, Integer> assignLanes(List<ClientCraftingGraph.Link> links,
            Map<Integer, GraphLayoutSnapshot.Box> boxes) {
        List<ClientCraftingGraph.Link> sorted = links.stream()
            .sorted(Comparator.comparingDouble((ClientCraftingGraph.Link link) -> span(link, boxes))
                .thenComparingDouble(link -> Math.min(boxes.get(link.fromId()).x(), boxes.get(link.toId()).x())))
            .toList();
        List<List<float[]>> occupied = new ArrayList<>();
        Map<ClientCraftingGraph.Link, Integer> result = new LinkedHashMap<>();
        for (var link : sorted) {
            var from = boxes.get(link.fromId());
            var to = boxes.get(link.toId());
            float[] interval = {Math.min(from.x(), to.x()) - LANE_MARGIN,
                Math.max(from.x() + from.width(), to.x() + to.width()) + LANE_MARGIN};
            int lane = 0;
            while (lane < occupied.size() && overlaps(occupied.get(lane), interval)) lane++;
            if (lane == occupied.size()) occupied.add(new ArrayList<>());
            occupied.get(lane).add(interval);
            result.put(link, lane);
        }
        return result;
    }

    private static float span(ClientCraftingGraph.Link link, Map<Integer, GraphLayoutSnapshot.Box> boxes) {
        return Math.abs(boxes.get(link.fromId()).centerX() - boxes.get(link.toId()).centerX());
    }

    private static boolean overlaps(List<float[]> intervals, float[] candidate) {
        for (float[] interval : intervals) {
            if (candidate[0] <= interval[1] && interval[0] <= candidate[1]) return true;
        }
        return false;
    }

    /** Spreads boundary materials beside the core node they feed or leave, then removes horizontal overlap. */
    private static void placeBoundary(ClientCraftingGraph graph, Map<Integer, GraphLayoutSnapshot.Box> boxes,
            List<Integer> ids, Map<Integer, Integer> index, List<Integer> order, float centerY, boolean input) {
        if (ids.isEmpty()) return;
        Map<Integer, List<Integer>> byAnchor = new LinkedHashMap<>();
        for (int id : ids) {
            Set<Integer> neighbors = input ? graph.downstream(id) : graph.upstream(id);
            int anchor = neighbors.stream().map(index::get).filter(java.util.Objects::nonNull)
                .min(Integer::compareTo).orElse(-1);
            byAnchor.computeIfAbsent(anchor, ignored -> new ArrayList<>()).add(id);
        }
        float step = MATERIAL_WIDTH + BOUNDARY_GAP;
        List<float[]> desired = new ArrayList<>();
        for (var entry : byAnchor.entrySet()) {
            float anchorX = entry.getKey() < 0 || order.isEmpty() ? 0
                : boxes.get(order.get(entry.getKey())).centerX();
            List<Integer> group = entry.getValue();
            for (int i = 0; i < group.size(); i++) {
                float offset = (float) ((i - (group.size() - 1) / 2d) * step);
                desired.add(new float[] {anchorX + offset, group.get(i), entry.getKey()});
            }
        }
        desired.sort(Comparator.comparingDouble((float[] value) -> value[2]).thenComparingDouble(value -> value[0]));
        float previousRight = Float.NEGATIVE_INFINITY;
        for (float[] value : desired) {
            float centerX = Math.max(value[0], previousRight + BOUNDARY_GAP + MATERIAL_WIDTH / 2);
            int id = (int) value[1];
            boxes.put(id, new GraphLayoutSnapshot.Box(id, centerX - MATERIAL_WIDTH / 2,
                centerY - MATERIAL_HEIGHT / 2, MATERIAL_WIDTH, MATERIAL_HEIGHT));
            previousRight = centerX + MATERIAL_WIDTH / 2;
        }
    }

    /**
     * Bottom attachments are ordered by the x position of the other endpoint. Nested lanes therefore leave a node
     * in the same order they wrap around each other, so lane edges never cross one another at the ports.
     * Lane links get two entries: the link itself (source port) and {@link #reverseKey} (target port).
     */
    private static Map<ClientCraftingGraph.Link, Float> bottomPorts(Map<Integer, GraphLayoutSnapshot.Box> boxes,
            List<ClientCraftingGraph.Link> laneLinks, List<ClientCraftingGraph.Link> outputLinks) {
        Map<Integer, List<PortRequest>> byNode = new LinkedHashMap<>();
        for (var link : laneLinks) {
            byNode.computeIfAbsent(link.fromId(), ignored -> new ArrayList<>())
                .add(new PortRequest(link, boxes.get(link.toId()).centerX(), 0));
            byNode.computeIfAbsent(link.toId(), ignored -> new ArrayList<>())
                .add(new PortRequest(reverseKey(link), boxes.get(link.fromId()).centerX(), 1));
        }
        for (var link : outputLinks) {
            byNode.computeIfAbsent(link.fromId(), ignored -> new ArrayList<>())
                .add(new PortRequest(link, boxes.get(link.toId()).centerX(), 2));
        }
        return spread(boxes, byNode);
    }

    private static Map<ClientCraftingGraph.Link, Float> topPorts(Map<Integer, GraphLayoutSnapshot.Box> boxes,
            List<ClientCraftingGraph.Link> inputLinks) {
        Map<Integer, List<PortRequest>> byNode = new LinkedHashMap<>();
        for (var link : inputLinks) {
            byNode.computeIfAbsent(link.toId(), ignored -> new ArrayList<>())
                .add(new PortRequest(link, boxes.get(link.fromId()).centerX(), 0));
        }
        return spread(boxes, byNode);
    }

    private static Map<ClientCraftingGraph.Link, Float> spread(Map<Integer, GraphLayoutSnapshot.Box> boxes,
            Map<Integer, List<PortRequest>> byNode) {
        Map<ClientCraftingGraph.Link, Float> result = new HashMap<>();
        for (var entry : byNode.entrySet()) {
            var box = boxes.get(entry.getKey());
            List<PortRequest> requests = entry.getValue().stream()
                .sorted(Comparator.comparingDouble(PortRequest::otherX).thenComparingInt(PortRequest::role))
                .toList();
            for (int i = 0; i < requests.size(); i++) {
                // Ports snap to whole units so vertical segments stay on a single pixel column.
                result.put(requests.get(i).key(),
                    (float) Math.round(box.x() + box.width() * (i + 1) / (requests.size() + 1)));
            }
        }
        return result;
    }

    /**
     * A boundary material with a single link slides onto its port column when that does not collide with another
     * boundary material, turning the usual dog-leg into a straight vertical drop.
     */
    private static void alignToPorts(Map<Integer, GraphLayoutSnapshot.Box> boxes, List<Integer> ids,
            List<ClientCraftingGraph.Link> links, Map<ClientCraftingGraph.Link, Float> ports, boolean input) {
        Map<Integer, List<ClientCraftingGraph.Link>> byNode = new HashMap<>();
        for (var link : links) {
            byNode.computeIfAbsent(input ? link.fromId() : link.toId(), ignored -> new ArrayList<>()).add(link);
        }
        List<Integer> ordered = ids.stream().filter(boxes::containsKey)
            .sorted(Comparator.comparingDouble(id -> boxes.get(id).x())).toList();
        for (int id : ordered) {
            List<ClientCraftingGraph.Link> own = byNode.getOrDefault(id, List.of());
            if (own.size() != 1) continue;
            var box = boxes.get(id);
            float targetX = ports.get(own.getFirst()) - box.width() / 2;
            var moved = new GraphLayoutSnapshot.Box(id, targetX, box.y(), box.width(), box.height());
            boolean collides = false;
            for (int other : ordered) {
                if (other == id) continue;
                var otherBox = boxes.get(other);
                if (moved.x() < otherBox.x() + otherBox.width() + BOUNDARY_GAP
                        && otherBox.x() < moved.x() + moved.width() + BOUNDARY_GAP) {
                    collides = true;
                    break;
                }
            }
            if (!collides) boxes.put(id, moved);
        }
    }

    /** Distinct map key for the target-side port of a lane link. */
    private static ClientCraftingGraph.Link reverseKey(ClientCraftingGraph.Link link) {
        return new ClientCraftingGraph.Link(link.toId(), link.fromId(), link.amount(), link.kind(), link.selected(),
            Integer.MIN_VALUE + link.materialNodeId());
    }

    private static void routeForward(List<ClientCraftingGraph.Link> links,
            Map<Integer, GraphLayoutSnapshot.Box> boxes,
            Map<ClientCraftingGraph.Link, List<GraphLayoutSnapshot.Point>> routes) {
        Map<Long, List<ClientCraftingGraph.Link>> byPair = new LinkedHashMap<>();
        for (var link : links) {
            byPair.computeIfAbsent(((long) link.fromId() << 32) ^ (link.toId() & 0xffffffffL),
                ignored -> new ArrayList<>()).add(link);
        }
        for (var group : byPair.values()) {
            for (int i = 0; i < group.size(); i++) {
                var link = group.get(i);
                var from = boxes.get(link.fromId());
                var to = boxes.get(link.toId());
                float y = Math.round(from.centerY() + (i - (group.size() - 1) / 2f) * PARALLEL_STEP);
                routes.put(link, List.of(point(from.x() + from.width(), y), point(to.x(), y)));
            }
        }
    }

    /**
     * Inputs that must shift sideways bend at staggered heights: the input nearest its target turns lowest, so a
     * neighbor's vertical drop never cuts through another input's horizontal run.
     */
    private static void routeInputs(List<ClientCraftingGraph.Link> links,
            Map<Integer, GraphLayoutSnapshot.Box> boxes, Map<ClientCraftingGraph.Link, Float> ports,
            Map<ClientCraftingGraph.Link, List<GraphLayoutSnapshot.Point>> routes) {
        List<ClientCraftingGraph.Link> leftward = new ArrayList<>();
        List<ClientCraftingGraph.Link> rightward = new ArrayList<>();
        for (var link : links) {
            var from = boxes.get(link.fromId());
            var to = boxes.get(link.toId());
            float endX = ports.get(link);
            if (Math.abs(from.centerX() - endX) < 0.5f) {
                routes.put(link, List.of(point(endX, bottom(from)), point(endX, to.y())));
            } else if (endX < from.centerX()) leftward.add(link);
            else rightward.add(link);
        }
        leftward.sort(Comparator.comparingDouble(link -> boxes.get(link.fromId()).centerX()));
        rightward.sort(Comparator.comparingDouble(link -> -boxes.get(link.fromId()).centerX()));
        for (List<ClientCraftingGraph.Link> group : List.of(leftward, rightward)) {
            for (int level = 0; level < group.size(); level++) {
                var link = group.get(level);
                var from = boxes.get(link.fromId());
                var to = boxes.get(link.toId());
                float endX = ports.get(link);
                float bendY = Math.min(to.y() - 8, bottom(from) + 10 + level * INPUT_BEND_STEP);
                routes.put(link, List.of(point(from.centerX(), bottom(from)), point(from.centerX(), bendY),
                    point(endX, bendY), point(endX, to.y())));
            }
        }
    }

    private static GraphLayoutSnapshot normalized(ClientCraftingGraph graph,
            Map<Integer, GraphLayoutSnapshot.Box> boxes,
            Map<ClientCraftingGraph.Link, List<GraphLayoutSnapshot.Point>> routes, long nanos, long version) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        for (var box : boxes.values()) {
            minX = Math.min(minX, box.x());
            minY = Math.min(minY, box.y());
        }
        for (var route : routes.values()) for (var point : route) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
        }
        if (minX == Float.MAX_VALUE) {
            return new GraphLayoutSnapshot(boxes, graph.links(), routes, new GraphLayoutSnapshot.Bounds(0, 0, 1, 1),
                nanos, version);
        }
        // Integer shift keeps every snapped port and lane on whole units after normalization.
        float shiftX = (float) Math.ceil(PADDING - minX);
        float shiftY = (float) Math.ceil(PADDING - minY);
        Map<Integer, GraphLayoutSnapshot.Box> shiftedBoxes = new LinkedHashMap<>();
        float right = 1;
        float bottom = 1;
        for (var box : boxes.values()) {
            var shifted = new GraphLayoutSnapshot.Box(box.nodeId(), box.x() + shiftX, box.y() + shiftY,
                box.width(), box.height());
            shiftedBoxes.put(box.nodeId(), shifted);
            right = Math.max(right, shifted.x() + shifted.width());
            bottom = Math.max(bottom, shifted.y() + shifted.height());
        }
        Map<ClientCraftingGraph.Link, List<GraphLayoutSnapshot.Point>> shiftedRoutes = new LinkedHashMap<>();
        for (var entry : routes.entrySet()) {
            List<GraphLayoutSnapshot.Point> points = new ArrayList<>(entry.getValue().size());
            for (var point : entry.getValue()) {
                var shifted = point(point.x() + shiftX, point.y() + shiftY);
                points.add(shifted);
                right = Math.max(right, shifted.x());
                bottom = Math.max(bottom, shifted.y());
            }
            shiftedRoutes.put(entry.getKey(), points);
        }
        // Leave room on the right for the required-output arrow drawn by the renderer.
        return new GraphLayoutSnapshot(shiftedBoxes, graph.links(), shiftedRoutes,
            new GraphLayoutSnapshot.Bounds(0, 0, right + PADDING + 30, bottom + PADDING), nanos, version);
    }

    static float width(ClientCraftingGraph.Node node) {
        return node != null && node.kind() == ClientCraftingGraph.Kind.PATTERN ? PATTERN_WIDTH : MATERIAL_WIDTH;
    }

    static float height(ClientCraftingGraph.Node node) {
        return node != null && node.kind() == ClientCraftingGraph.Kind.PATTERN ? PATTERN_HEIGHT : MATERIAL_HEIGHT;
    }

    private static float bottom(GraphLayoutSnapshot.Box box) {
        return box.y() + box.height();
    }

    private static GraphLayoutSnapshot.Point point(float x, float y) {
        return new GraphLayoutSnapshot.Point(x, y);
    }

    private record PortRequest(ClientCraftingGraph.Link key, float otherX, int role) {}
}
