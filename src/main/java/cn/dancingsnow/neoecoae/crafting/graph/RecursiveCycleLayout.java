package cn.dancingsnow.neoecoae.crafting.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.graph.GraphPortAllocator.Side;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Bounds;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Box;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.Point;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayoutSnapshot.RecursionRegion;
import cn.dancingsnow.neoecoae.crafting.graph.RecursiveCycleNode.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Left-to-right checkpoints centered on each execution icon, with separate input and delivery routes. */
final class RecursiveCycleLayout implements GraphLayoutEngine {
    private static final float WIDTH = GraphLayoutSnapshot.COMPACT_MATERIAL_WIDTH;
    private static final float HEIGHT = GraphLayoutSnapshot.COMPACT_MATERIAL_HEIGHT;
    private static final float GAP = CompactTreeLayout.SIBLING_GAP;
    private static final float LANE_GAP = 6;
    private static final float EDGE_GAP = 12;
    private static final float ROUTE_GAP = 6;
    private static final float REGION_BOTTOM_PADDING = 6;
    private static final float RESULT_HEADER = 28;
    private static final float RESULT_GAP = 64;
    private static final float STEP_SIZE = 20;
    private static final float INPUT_Y = -HEIGHT - 40;

    @Override
    public GraphLayoutSnapshot layout(ClientCraftingGraph graph, long version) {
        long started = System.nanoTime();
        Map<Integer, List<ClientCraftingGraph.Node>> levels = new TreeMap<>();
        Map<AEKey, Integer> lanes = new LinkedHashMap<>();
        var ordered = graph.nodes().values().stream().sorted(Comparator.comparingInt(ClientCraftingGraph.Node::id)).toList();
        for (var node : ordered) {
            levels.computeIfAbsent(node.recursion().level(), ignored -> new ArrayList<>()).add(node);
            if (node.key() != null && (node.recursion().role() == Role.SEED || node.recursion().role() == Role.STOCK)) {
                lanes.computeIfAbsent(node.key(), ignored -> lanes.size());
            }
        }
        for (var node : ordered) if (node.key() != null && node.recursion().role() == Role.RETAINED) {
            lanes.computeIfAbsent(node.key(), ignored -> lanes.size());
        }
        Map<Integer, List<AEKey>> columnLanes = new LinkedHashMap<>();
        float coreHeight = HEIGHT;
        for (var entry : levels.entrySet()) {
            var keys = entry.getValue().stream()
                .filter(node -> node.key() != null && node.recursion().role() != Role.INPUT
                    && node.recursion().role() != Role.OUTPUT)
                .map(ClientCraftingGraph.Node::key).distinct().sorted(Comparator.comparingInt(lanes::get)).toList();
            columnLanes.put(entry.getKey(), keys);
            if (entry.getValue().stream().noneMatch(node -> node.recursion().role() == Role.RETAINED)) {
                coreHeight = Math.max(coreHeight, height(keys.size()));
            }
        }
        int deliveryCount = (int) graph.links().stream()
            .filter(link -> graph.nodes().get(link.toId()).recursion().role() == Role.OUTPUT).count();
        int maxInputs = levels.values().stream().mapToInt(nodes -> (int) nodes.stream()
            .filter(node -> node.recursion().role() == Role.INPUT).count()).max().orElse(0);
        float inputTop = INPUT_Y - Math.max(0, (maxInputs - 1) / 2) * ROUTE_GAP;
        int lastLevel = levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        Map<Integer, Float> columnX = new LinkedHashMap<>();
        Map<Integer, Float> columnWidth = new LinkedHashMap<>();
        Map<Integer, Float> stepX = new LinkedHashMap<>();
        float x = 0;
        for (int level = 0; level <= lastLevel; level += 2) {
            var nodes = levels.getOrDefault(level, List.of());
            Map<AEKey, Integer> occurrences = new LinkedHashMap<>();
            int inputs = 0, outputs = 0, materials = 0;
            float width = WIDTH;
            for (var node : nodes) {
                if (node.recursion().role() == Role.INPUT) inputs++;
                else if (node.recursion().role() == Role.OUTPUT) outputs++;
                else if (node.key() == null) width = Math.max(width, 60);
                else {
                    materials++;
                    width = Math.max(width, width(occurrences.merge(node.key(), 1, Integer::sum)));
                }
            }
            width = Math.max(width, width(outputs));
            columnX.put(level, x);
            columnWidth.put(level, width);
            int nextMaterials = (int) levels.getOrDefault(level + 2, List.of()).stream()
                .filter(node -> node.key() != null && node.recursion().role() == Role.STOCK).count();
            int nextOutputs = (int) levels.getOrDefault(level + 2, List.of()).stream()
                .filter(node -> node.recursion().role() == Role.OUTPUT).count();
            float fanGap = Math.max(EDGE_GAP, ((Math.max(materials, nextMaterials) + 1) / 2 + 1) * ROUTE_GAP);
            float leftGap = fanGap + (level == 0 ? regionPadding(width) : 0);
            float rightGap = nextOutputs == 0 ? fanGap : Math.max(fanGap,
                regionPadding(width(nextOutputs)) + EDGE_GAP * 2 + Math.max(0, deliveryCount - 1) * ROUTE_GAP);
            float gap = levels.containsKey(level + 1)
                ? Math.max(STEP_SIZE + leftGap + rightGap, width(inputs) + EDGE_GAP)
                : RESULT_GAP + Math.max(0, deliveryCount - 1) * ROUTE_GAP;
            stepX.put(level + 1, x + width + leftGap + (gap - STEP_SIZE - leftGap - rightGap) / 2);
            x += width + gap;
        }
        Map<Integer, Box> boxes = new LinkedHashMap<>();
        for (var entry : levels.entrySet()) {
            int level = entry.getKey();
            if (level % 2 != 0) continue;
            int inputIndex = 0, outputIndex = 0;
            int inputCount = (int) entry.getValue().stream()
                .filter(node -> node.recursion().role() == Role.INPUT).count();
            float inputLeft = stepX.get(level + 1) + STEP_SIZE / 2 - width(inputCount) / 2;
            var materialLanes = new ArrayList<>(columnLanes.get(level));
            int centeredLanes = materialLanes.size();
            boolean retained = entry.getValue().stream().anyMatch(node -> node.recursion().role() == Role.RETAINED);
            if (retained) {
                var previous = columnLanes.getOrDefault(level - 2, List.of()).stream()
                    .filter(materialLanes::contains).toList();
                if (!previous.isEmpty()) {
                    materialLanes.removeAll(previous);
                    materialLanes.addAll(0, previous);
                    centeredLanes = previous.size();
                }
            }
            float materialTop = (coreHeight - height(centeredLanes)) / 2;
            // Leave a full routing corridor between the delivery frame and the result heading.
            float outputTop = retained ? Math.min(INPUT_Y, materialTop - RESULT_HEADER - HEIGHT
                - REGION_BOTTOM_PADDING - EDGE_GAP * 2 - Math.max(0, deliveryCount - 1) * ROUTE_GAP) : INPUT_Y;
            Map<AEKey, Integer> occurrences = new LinkedHashMap<>();
            for (var node : entry.getValue()) {
                float cardX = columnX.get(level), cardY, cardWidth = WIDTH;
                if (node.recursion().role() == Role.INPUT) {
                    cardX = inputLeft + inputIndex++ * (WIDTH + GAP);
                    cardY = inputTop;
                } else if (node.recursion().role() == Role.OUTPUT) {
                    cardX += outputIndex++ * (WIDTH + GAP);
                    cardY = outputTop;
                } else if (node.key() != null) {
                    cardX += (occurrences.merge(node.key(), 1, Integer::sum) - 1) * (WIDTH + GAP);
                    cardY = materialTop + materialLanes.indexOf(node.key()) * (HEIGHT + LANE_GAP);
                } else {
                    cardY = (coreHeight - HEIGHT) / 2;
                    cardWidth = columnWidth.get(level);
                }
                boxes.put(node.id(), new Box(node.id(), cardX, cardY, cardWidth, HEIGHT));
            }
        }
        for (var node : ordered) {
            int level = node.recursion().level();
            if (level % 2 == 0) continue;
            boxes.put(node.id(), new Box(node.id(), stepX.get(level), (coreHeight - STEP_SIZE) / 2,
                STEP_SIZE, STEP_SIZE));
        }

        List<RecursionRegion> regions = new ArrayList<>();
        for (int level = 0; level <= lastLevel; level += 2) {
            var nodes = levels.getOrDefault(level, List.of());
            var material = nodes.stream()
                .filter(node -> node.recursion().role() != Role.INPUT && node.recursion().role() != Role.OUTPUT).toList();
            Role role = level == 0 ? Role.SEED
                : material.stream().anyMatch(node -> node.recursion().role() == Role.RETAINED) ? Role.RETAINED : Role.STOCK;
            addRegion(regions, boxes, material, role, level, role == Role.STOCK ? 18 : RESULT_HEADER);
            addRegion(regions, boxes, nodes.stream().filter(node -> node.recursion().role() == Role.INPUT).toList(), Role.INPUT, level, 8);
            addRegion(regions, boxes, nodes.stream().filter(node -> node.recursion().role() == Role.OUTPUT).toList(), Role.OUTPUT, level, 8);
        }
        Map<Integer, Bounds> materialRegions = new LinkedHashMap<>(), outputRegions = new LinkedHashMap<>();
        for (var region : regions) {
            if (region.role() == Role.OUTPUT) outputRegions.put(region.level(), region.bounds());
            else if (region.role() != Role.INPUT) materialRegions.put(region.level(), region.bounds());
        }
        var sourcePorts = ports(graph, boxes, true);
        var targetPorts = ports(graph, boxes, false);
        var deliveries = graph.links().stream()
            .filter(link -> graph.nodes().get(link.toId()).recursion().role() == Role.OUTPUT)
            .sorted(Comparator.<ClientCraftingGraph.Link>comparingDouble(link -> boxes.get(link.toId()).centerX())
                .thenComparingDouble(link -> boxes.get(link.fromId()).centerY())
                .thenComparingInt(ClientCraftingGraph.Link::fromId)).toList();
        Map<ClientCraftingGraph.Link, Integer> deliveryTracks = new LinkedHashMap<>();
        for (int i = 0; i < deliveries.size(); i++) deliveryTracks.put(deliveries.get(i), i);
        Map<ClientCraftingGraph.Link, List<Point>> routes = new LinkedHashMap<>();
        for (var link : graph.links()) {
            Box from = boxes.get(link.fromId()), to = boxes.get(link.toId());
            var sourcePort = sourcePorts.get(link);
            var targetPort = targetPorts.get(link);
            var start = sourcePort.point();
            var end = targetPort.point();
            boolean external = graph.nodes().get(link.fromId()).recursion().role() == Role.INPUT;
            if (external) {
                // Keep the horizontal input collector below the region frame, not alongside its bottom edge.
                float bendY = from.y() + from.height() + REGION_BOTTOM_PADDING + EDGE_GAP + targetPort.inputBendOffset();
                routes.put(link, List.of(start, new Point(start.x(), bendY), new Point(end.x(), bendY), end));
            } else {
                boolean fromStep = graph.nodes().get(link.fromId()).key() == null;
                boolean toStep = graph.nodes().get(link.toId()).key() == null;
                boolean output = graph.nodes().get(link.toId()).recursion().role() == Role.OUTPUT;
                int fromLevel = graph.nodes().get(link.fromId()).recursion().level();
                float sourceRight = fromLevel % 2 == 0 ? columnX.get(fromLevel) + columnWidth.get(fromLevel) : start.x();
                float fraction = fromStep ? sourcePort.bendFraction() : toStep ? targetPort.bendFraction() : 0.5f;
                float middle = sourceRight + (end.x() - sourceRight) * fraction;
                float outputBendY = 0;
                if (output) {
                    int toLevel = graph.nodes().get(link.toId()).recursion().level();
                    var outputRegion = outputRegions.get(toLevel);
                    var materialRegion = materialRegions.get(toLevel);
                    float left = materialRegion == null ? outputRegion.left()
                        : Math.min(outputRegion.left(), materialRegion.left());
                    int track = deliveryTracks.get(link);
                    middle = left - EDGE_GAP - (deliveryCount - 1 - track) * ROUTE_GAP;
                    outputBendY = outputRegion.bottom() + EDGE_GAP + track * ROUTE_GAP;
                }
                List<Point> route = new ArrayList<>();
                route.add(start);
                if (!fromStep && start.x() < sourceRight) {
                    // Separate seed and pre-existing stock can share a material lane; bypass the later card.
                    float side = start.x() + GAP / 2, bypassY = from.y() - LANE_GAP / 2;
                    route.add(new Point(side, start.y()));
                    route.add(new Point(side, bypassY));
                    route.add(new Point(middle, bypassY));
                } else if (!output && start.y() == end.y()) {
                    routes.put(link, List.of(start, end));
                    continue;
                } else route.add(new Point(middle, start.y()));
                if (output) {
                    route.add(new Point(middle, outputBendY));
                    route.add(new Point(end.x(), outputBendY));
                } else {
                    route.add(new Point(middle, end.y()));
                }
                route.add(end);
                routes.put(link, route);
            }
        }

        float minX = 0, top = 0, maxX = 0, bottom = coreHeight;
        for (Box box : boxes.values()) {
            minX = Math.min(minX, box.x()); top = Math.min(top, box.y());
            maxX = Math.max(maxX, box.x() + box.width()); bottom = Math.max(bottom, box.y() + box.height());
        }
        for (var region : regions) {
            minX = Math.min(minX, region.bounds().left()); top = Math.min(top, region.bounds().top());
            maxX = Math.max(maxX, region.bounds().right()); bottom = Math.max(bottom, region.bounds().bottom());
        }
        for (var route : routes.values()) for (var point : route) {
            minX = Math.min(minX, point.x()); top = Math.min(top, point.y());
            maxX = Math.max(maxX, point.x()); bottom = Math.max(bottom, point.y());
        }
        return new GraphLayoutSnapshot(boxes, graph.links(), routes,
            new Bounds(minX - 8, top - 6, maxX + 8, bottom + 8),
            System.nanoTime() - started, version, regions);
    }

    private record Port(Point point, float bendFraction, float inputBendOffset) {}

    private static Map<ClientCraftingGraph.Link, Port> ports(ClientCraftingGraph graph, Map<Integer, Box> boxes,
            boolean outgoing) {
        Map<Integer, List<ClientCraftingGraph.Link>> groups = new LinkedHashMap<>();
        for (var link : graph.links()) {
            groups.computeIfAbsent(outgoing ? link.fromId() : link.toId(), ignored -> new ArrayList<>()).add(link);
        }
        Map<ClientCraftingGraph.Link, Port> result = new LinkedHashMap<>();
        for (var entry : groups.entrySet()) {
            var node = graph.nodes().get(entry.getKey());
            var box = boxes.get(entry.getKey());
            var links = entry.getValue();
            if (outgoing && node.key() != null && node.recursion().role() != Role.INPUT) {
                // Delivery leaves above the normal stock connection, so both colors remain visible at a fork.
                allocatePorts(result, boxes, box, links.stream()
                    .filter(link -> graph.nodes().get(link.toId()).recursion().role() == Role.OUTPUT).toList(),
                    true, Side.RIGHT, true, false);
                allocatePorts(result, boxes, box, links.stream()
                    .filter(link -> graph.nodes().get(link.toId()).recursion().role() != Role.OUTPUT).toList(),
                    true, Side.RIGHT, false, false);
            } else if (!outgoing && node.key() == null) {
                allocatePorts(result, boxes, box, links.stream()
                    .filter(link -> graph.nodes().get(link.fromId()).recursion().role() == Role.INPUT).toList(),
                    false, Side.TOP, false, false);
                allocatePorts(result, boxes, box, links.stream()
                    .filter(link -> graph.nodes().get(link.fromId()).recursion().role() != Role.INPUT).toList(),
                    false, Side.LEFT, false, true);
            } else {
                Side side = outgoing ? node.recursion().role() == Role.INPUT ? Side.BOTTOM : Side.RIGHT
                    : node.recursion().role() == Role.OUTPUT ? Side.BOTTOM : Side.LEFT;
                allocatePorts(result, boxes, box, links, outgoing, side, false, node.key() == null);
            }
        }
        return result;
    }

    private static void allocatePorts(Map<ClientCraftingGraph.Link, Port> ports, Map<Integer, Box> boxes, Box box,
            List<ClientCraftingGraph.Link> links, boolean outgoing, Side side, boolean upperHalf, boolean fan) {
        boolean horizontal = side == Side.LEFT || side == Side.RIGHT;
        var ordered = links.stream().sorted(Comparator.<ClientCraftingGraph.Link>comparingDouble(link -> {
            var other = boxes.get(outgoing ? link.toId() : link.fromId());
            return horizontal ? other.centerY() : other.centerX();
        }).thenComparingInt(link -> outgoing ? link.toId() : link.fromId())).toList();
        for (int i = 0; i < ordered.size(); i++) {
            float bendFraction = 0.5f;
            if (fan) {
                // Outer branches turn nearest the icon; inner branches get their own, shorter vertical tracks.
                bendFraction = (Math.min(i, ordered.size() - 1 - i) + 1f) / ((ordered.size() + 1) / 2 + 1f);
                if (!outgoing) bendFraction = 1 - bendFraction;
            }
            // Within one input row, the outer drops turn lower than the inner drops they pass around.
            float inputBendOffset = side == Side.TOP
                ? Math.max(0, (ordered.size() - 1) / 2 - Math.min(i, ordered.size() - 1 - i)) * ROUTE_GAP : 0;
            ports.put(ordered.get(i), new Port(GraphPortAllocator.evenlySpacedPoint(box, side, i,
                upperHalf ? ordered.size() * 2 + 1 : ordered.size()), bendFraction, inputBendOffset));
        }
    }

    private static void addRegion(List<RecursionRegion> regions, Map<Integer, Box> boxes,
            List<ClientCraftingGraph.Node> group, Role role, int level, float header) {
        if (group.isEmpty()) return;
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (var node : group) {
            var box = boxes.get(node.id());
            left = Math.min(left, box.x()); top = Math.min(top, box.y());
            right = Math.max(right, box.x() + box.width()); bottom = Math.max(bottom, box.y() + box.height());
        }
        float padding = regionPadding(right - left);
        regions.add(new RecursionRegion(role, level,
            new Bounds(left - padding, top - header, right + padding, bottom + REGION_BOTTOM_PADDING)));
    }

    private static float width(int count) { return Math.max(0, count * (WIDTH + GAP) - GAP); }
    private static float height(int count) { return Math.max(HEIGHT, count * (HEIGHT + LANE_GAP) - LANE_GAP); }
    private static float regionPadding(float width) { return Math.max(6, (60 - width) / 2); }
}
