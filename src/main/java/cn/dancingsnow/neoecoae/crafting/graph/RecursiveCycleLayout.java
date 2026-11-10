package cn.dancingsnow.neoecoae.crafting.graph;

import appeng.api.stacks.AEKey;
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

/** Left-to-right checkpoints with stable material lanes and a single input row above each execution icon. */
final class RecursiveCycleLayout implements GraphLayoutEngine {
    private static final float WIDTH = GraphLayoutSnapshot.COMPACT_MATERIAL_WIDTH;
    private static final float HEIGHT = GraphLayoutSnapshot.COMPACT_MATERIAL_HEIGHT;
    private static final float GAP = CompactTreeLayout.SIBLING_GAP;
    private static final float LANE_GAP = 6;
    private static final float EDGE_GAP = 12;
    private static final float RESULT_GAP = 44;
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
        float coreHeight = Math.max(HEIGHT, lanes.size() * (HEIGHT + LANE_GAP) - LANE_GAP);
        // Depleted external materials only need lanes in the final remainder, not gaps in every checkpoint.
        for (var node : ordered) if (node.key() != null && node.recursion().role() == Role.RETAINED) {
            lanes.computeIfAbsent(node.key(), ignored -> lanes.size());
        }
        int lastLevel = levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        Map<Integer, Float> columnX = new LinkedHashMap<>();
        Map<Integer, Float> columnWidth = new LinkedHashMap<>();
        Map<Integer, Float> stepX = new LinkedHashMap<>();
        float x = 0;
        for (int level = 0; level <= lastLevel; level += 2) {
            var nodes = levels.getOrDefault(level, List.of());
            Map<AEKey, Integer> occurrences = new LinkedHashMap<>();
            int inputs = 0, outputs = 0;
            float width = WIDTH;
            for (var node : nodes) {
                if (node.recursion().role() == Role.INPUT) inputs++;
                else if (node.recursion().role() == Role.OUTPUT) outputs++;
                else if (node.key() == null) width = Math.max(width, 60);
                else {
                    width = Math.max(width, width(occurrences.merge(node.key(), 1, Integer::sum)));
                }
            }
            width = Math.max(width, width(outputs));
            columnX.put(level, x);
            columnWidth.put(level, width);
            float gap = levels.containsKey(level + 1)
                ? Math.max(STEP_SIZE + EDGE_GAP * 2, width(inputs) + EDGE_GAP) : RESULT_GAP;
            stepX.put(level + 1, x + width + (gap - STEP_SIZE) / 2);
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
            Map<AEKey, Integer> occurrences = new LinkedHashMap<>();
            for (var node : entry.getValue()) {
                float cardX = columnX.get(level), cardY, cardWidth = WIDTH;
                if (node.recursion().role() == Role.INPUT) {
                    cardX = inputLeft + inputIndex++ * (WIDTH + GAP);
                    cardY = INPUT_Y;
                } else if (node.recursion().role() == Role.OUTPUT) {
                    cardX += outputIndex++ * (WIDTH + GAP);
                    cardY = INPUT_Y;
                } else if (node.key() != null) {
                    cardX += (occurrences.merge(node.key(), 1, Integer::sum) - 1) * (WIDTH + GAP);
                    cardY = lanes.get(node.key()) * (HEIGHT + LANE_GAP);
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

        Map<ClientCraftingGraph.Link, List<Point>> routes = new LinkedHashMap<>();
        for (var link : graph.links()) {
            Box from = boxes.get(link.fromId()), to = boxes.get(link.toId());
            boolean external = graph.nodes().get(link.fromId()).recursion().role() == Role.INPUT;
            if (external) {
                float bendY = from.y() + from.height() + 8;
                routes.put(link, List.of(new Point(from.centerX(), from.y() + from.height()),
                    new Point(from.centerX(), bendY), new Point(to.centerX(), bendY), new Point(to.centerX(), to.y())));
            } else {
                boolean fromStep = graph.nodes().get(link.fromId()).key() == null;
                boolean toStep = graph.nodes().get(link.toId()).key() == null;
                boolean output = graph.nodes().get(link.toId()).recursion().role() == Role.OUTPUT;
                float startY = fromStep ? clamp(to.centerY(), from.y() + 4, from.y() + from.height() - 4) : from.centerY();
                float endY = toStep ? clamp(startY, to.y() + 4, to.y() + to.height() - 4) : to.centerY();
                var start = new Point(from.x() + from.width(), startY);
                var end = output ? new Point(to.centerX(), to.y() + to.height()) : new Point(to.x(), endY);
                int fromLevel = graph.nodes().get(link.fromId()).recursion().level();
                float sourceRight = fromLevel % 2 == 0 ? columnX.get(fromLevel) + columnWidth.get(fromLevel) : start.x();
                float targetLeft = output ? columnX.get(graph.nodes().get(link.toId()).recursion().level()) : end.x();
                float middle = (sourceRight + targetLeft) / 2;
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
                    float bendY = to.y() + to.height() + 8;
                    route.add(new Point(middle, bendY));
                    route.add(new Point(end.x(), bendY));
                } else {
                    route.add(new Point(middle, end.y()));
                }
                route.add(end);
                routes.put(link, route);
            }
        }

        List<RecursionRegion> regions = new ArrayList<>();
        for (int level = 0; level <= lastLevel; level += 2) {
            var nodes = levels.getOrDefault(level, List.of());
            var material = nodes.stream()
                .filter(node -> node.recursion().role() != Role.INPUT && node.recursion().role() != Role.OUTPUT).toList();
            Role role = level == 0 ? Role.SEED
                : material.stream().anyMatch(node -> node.recursion().role() == Role.RETAINED) ? Role.RETAINED : Role.STOCK;
            addRegion(regions, boxes, material, role, level, role == Role.STOCK ? 18 : 28);
            addRegion(regions, boxes, nodes.stream().filter(node -> node.recursion().role() == Role.INPUT).toList(), Role.INPUT, level, 8);
            addRegion(regions, boxes, nodes.stream().filter(node -> node.recursion().role() == Role.OUTPUT).toList(), Role.OUTPUT, level, 8);
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
        return new GraphLayoutSnapshot(boxes, graph.links(), routes,
            new Bounds(minX - 8, top - 6, maxX + 8, bottom + 8),
            System.nanoTime() - started, version, regions);
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
        float padding = Math.max(6, (60 - (right - left)) / 2);
        regions.add(new RecursionRegion(role, level, new Bounds(left - padding, top - header, right + padding, bottom + 6)));
    }

    private static float width(int count) { return Math.max(0, count * (WIDTH + GAP) - GAP); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
