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

/** Fixed material columns, horizontal external-input rows and compact execution icons between stock rows. */
final class RecursiveCycleLayout implements GraphLayoutEngine {
    private static final float WIDTH = GraphLayoutSnapshot.COMPACT_MATERIAL_WIDTH;
    private static final float HEIGHT = GraphLayoutSnapshot.COMPACT_MATERIAL_HEIGHT;
    private static final float GAP = CompactTreeLayout.SIBLING_GAP;
    private static final float STACK_GAP = 12;
    private static final float ROW_GAP = 60;
    private static final float STEP_HEIGHT = 20;

    @Override
    public GraphLayoutSnapshot layout(ClientCraftingGraph graph, long version) {
        long started = System.nanoTime();
        Map<Integer, List<ClientCraftingGraph.Node>> rows = new TreeMap<>();
        Map<AEKey, Integer> columns = new LinkedHashMap<>();
        var ordered = graph.nodes().values().stream().sorted(Comparator.comparingInt(ClientCraftingGraph.Node::id)).toList();
        for (var node : ordered) {
            rows.computeIfAbsent(node.recursion().level(), ignored -> new ArrayList<>()).add(node);
            if (node.key() != null) columns.computeIfAbsent(node.key(), ignored -> columns.size());
        }
        float stockWidth = Math.max(GraphLayoutSnapshot.COMPACT_CYCLE_WIDTH, width(columns.size()));
        float left = -stockWidth / 2;
        float right = left + stockWidth;
        float materialLeft = -width(columns.size()) / 2;
        int maxInputs = rows.values().stream().mapToInt(nodes -> (int) nodes.stream()
            .filter(node -> node.recursion().role() == Role.INPUT).count()).max().orElse(0);
        float inputRight = left - 42;
        float inputLeft = inputRight - width(maxInputs);
        float outputX = right + 36;
        int lastLevel = rows.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        Map<Integer, Float> rowY = new LinkedHashMap<>();
        for (int level = 0; level <= lastLevel; level += 2) {
            var nodes = rows.getOrDefault(level, List.of());
            Map<AEKey, Integer> occurrences = new LinkedHashMap<>();
            for (var node : nodes) {
                if (node.key() != null && node.recursion().role() != Role.INPUT
                        && node.recursion().role() != Role.OUTPUT) {
                    occurrences.merge(node.key(), 1, Integer::sum);
                }
            }
            int stacked = occurrences.values().stream().mapToInt(Integer::intValue).max().orElse(1);
            float height = Math.max(HEIGHT, stacked * (HEIGHT + STACK_GAP) - STACK_GAP);
            rowY.put(level, level == 0 ? 0 : rowY.get(level - 2) - ROW_GAP - height);
        }
        Map<Integer, Box> boxes = new LinkedHashMap<>();
        for (var row : rows.entrySet()) {
            int level = row.getKey();
            if (level % 2 != 0) continue;
            float y = rowY.get(level);
            int inputIndex = 0, outputIndex = 0;
            int inputCount = (int) row.getValue().stream()
                .filter(node -> node.recursion().role() == Role.INPUT).count();
            Map<AEKey, Integer> occurrences = new LinkedHashMap<>();
            for (var node : row.getValue()) {
                float x, cardY = y;
                if (node.recursion().role() == Role.INPUT) {
                    x = inputRight - width(inputCount) + inputIndex++ * (WIDTH + GAP);
                } else if (node.recursion().role() == Role.OUTPUT) {
                    x = outputX + outputIndex++ * (WIDTH + GAP);
                } else if (node.key() != null) {
                    x = materialLeft + columns.get(node.key()) * (WIDTH + GAP);
                    cardY += (occurrences.merge(node.key(), 1, Integer::sum) - 1) * (HEIGHT + STACK_GAP);
                } else {
                    x = -WIDTH / 2;
                }
                boxes.put(node.id(), new Box(node.id(), x, cardY, WIDTH, HEIGHT));
            }
        }
        float outputRight = boxes.values().stream()
            .filter(box -> graph.nodes().get(box.nodeId()).recursion().role() == Role.OUTPUT)
            .map(box -> box.x() + box.width()).max(Float::compare).orElse(right);
        for (var node : ordered) {
            int level = node.recursion().level();
            if (level % 2 == 0) continue;
            boolean delivery = node.recursion().role() == Role.DELIVERY || node.recursion().role() == Role.UNKNOWN;
            float y = rowY.get(level - 1) - (ROW_GAP + STEP_HEIGHT) / 2;
            boxes.put(node.id(), delivery
                ? new Box(node.id(), left - 4, y, outputRight - left + 8, STEP_HEIGHT)
                : new Box(node.id(), (left + right - STEP_HEIGHT) / 2, y, STEP_HEIGHT, STEP_HEIGHT));
        }

        Map<ClientCraftingGraph.Link, List<Point>> routes = new LinkedHashMap<>();
        for (var link : graph.links()) {
            Box from = boxes.get(link.fromId()), to = boxes.get(link.toId());
            boolean external = graph.nodes().get(link.fromId()).recursion().role() == Role.INPUT;
            if (external) {
                routes.put(link, List.of(new Point(from.centerX(), from.y()),
                    new Point(from.centerX(), to.centerY()), new Point(to.x(), to.centerY())));
            } else {
                boolean fromStep = graph.nodes().get(link.fromId()).key() == null && from.height() == STEP_HEIGHT;
                boolean toStep = graph.nodes().get(link.toId()).key() == null && to.height() == STEP_HEIGHT;
                float startX = fromStep ? clamp(to.centerX(), from.x() + 4, from.x() + from.width() - 4) : from.centerX();
                float endX = toStep ? clamp(startX, to.x() + 4, to.x() + to.width() - 4) : to.centerX();
                var start = new Point(startX, from.y());
                var end = new Point(endX, to.y() + to.height());
                float middle = (start.y() + end.y()) / 2;
                int fromLevel = graph.nodes().get(link.fromId()).recursion().level();
                if (!fromStep && fromLevel % 2 == 0 && from.y() > rowY.get(fromLevel)) {
                    float side = from.x() + from.width() + GAP / 2;
                    routes.put(link, List.of(start, new Point(start.x(), start.y() - 6),
                        new Point(side, start.y() - 6), new Point(side, end.y() + 6),
                        new Point(end.x(), end.y() + 6), end));
                } else {
                    routes.put(link, List.of(start, new Point(start.x(), middle), new Point(end.x(), middle), end));
                }
            }
        }

        List<RecursionRegion> regions = new ArrayList<>();
        for (int level = 0; level <= lastLevel; level += 2) {
            var material = rows.getOrDefault(level, List.of()).stream()
                .filter(node -> node.recursion().role() != Role.INPUT && node.recursion().role() != Role.OUTPUT).toList();
            if (material.isEmpty()) continue;
            Role role = level == 0 ? Role.SEED
                : material.stream().anyMatch(node -> node.recursion().role() == Role.RETAINED) ? Role.RETAINED : Role.STOCK;
            float materialBottom = material.stream().map(node -> boxes.get(node.id()))
                .map(box -> box.y() + box.height()).max(Float::compare).orElse(rowY.get(level) + HEIGHT);
            regions.add(new RecursionRegion(role, level,
                new Bounds(left - 8, rowY.get(level) - 14, right + 8, materialBottom + 8)));
        }
        addRegion(regions, graph, boxes, Role.INPUT);
        addRegion(regions, graph, boxes, Role.OUTPUT);
        float minX = inputLeft - 82, top = 0, maxX = right, bottom = HEIGHT;
        for (Box box : boxes.values()) {
            minX = Math.min(minX, box.x()); top = Math.min(top, box.y());
            maxX = Math.max(maxX, box.x() + box.width()); bottom = Math.max(bottom, box.y() + box.height());
        }
        for (var region : regions) {
            minX = Math.min(minX, region.bounds().left()); top = Math.min(top, region.bounds().top());
            maxX = Math.max(maxX, region.bounds().right()); bottom = Math.max(bottom, region.bounds().bottom());
        }
        return new GraphLayoutSnapshot(boxes, graph.links(), routes,
            new Bounds(minX - 12, top - 20, maxX + 20, bottom + 16),
            System.nanoTime() - started, version, regions);
    }

    private static void addRegion(List<RecursionRegion> regions, ClientCraftingGraph graph,
            Map<Integer, Box> boxes, Role role) {
        var group = boxes.values().stream()
            .filter(box -> graph.nodes().get(box.nodeId()).recursion().role() == role).toList();
        if (group.isEmpty()) return;
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (var box : group) {
            left = Math.min(left, box.x()); top = Math.min(top, box.y());
            right = Math.max(right, box.x() + box.width()); bottom = Math.max(bottom, box.y() + box.height());
        }
        float padding = Math.max(8, (56 - (right - left)) / 2);
        regions.add(new RecursionRegion(role, 0, new Bounds(left - padding, top - 14, right + padding, bottom + 8)));
    }

    private static float width(int count) { return Math.max(0, count * (WIDTH + GAP) - GAP); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
