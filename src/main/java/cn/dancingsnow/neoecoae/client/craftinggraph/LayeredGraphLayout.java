package cn.dancingsnow.neoecoae.client.craftinggraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable goal-left layered layout. SCCs have already been condensed by {@link ClientCraftingGraph}. */
public final class LayeredGraphLayout {
    private static final float LAYER_GAP = 92;
    private static final float ROW_GAP = 28;

    public GraphLayoutSnapshot layout(ClientCraftingGraph graph, long version) {
        long started = System.nanoTime();
        Map<Integer, Integer> layer = assignLayers(graph);
        Map<Integer, List<Integer>> layers = new LinkedHashMap<>();
        graph.nodes().keySet().stream().sorted().forEach(id ->
            layers.computeIfAbsent(layer.getOrDefault(id, 0), ignored -> new ArrayList<>()).add(id));

        Map<Integer, Float> priorOrder = new HashMap<>();
        for (var entry : layers.entrySet()) {
            entry.getValue().sort(Comparator.<Integer>comparingDouble(id -> barycenter(graph, id, priorOrder))
                .thenComparingInt(Integer::intValue));
            for (int i = 0; i < entry.getValue().size(); i++) priorOrder.put(entry.getValue().get(i), (float) i);
        }

        Map<Integer, GraphLayoutSnapshot.Box> boxes = new LinkedHashMap<>();
        float maxRight = 0;
        float maxBottom = 0;
        float x = 0;
        for (var entry : layers.entrySet()) {
            float layerWidth = (float) entry.getValue().stream().map(graph.nodes()::get)
                .mapToDouble(LayeredGraphLayout::width).max().orElse(GraphLayoutSnapshot.MATERIAL_WIDTH);
            float y = 0;
            for (int id : entry.getValue()) {
                var node = graph.nodes().get(id);
                float width = width(node);
                float height = height(node);
                var box = new GraphLayoutSnapshot.Box(id, x, y, width, height);
                boxes.put(id, box);
                y += height + ROW_GAP;
                maxRight = Math.max(maxRight, x + width);
                maxBottom = Math.max(maxBottom, y - ROW_GAP);
            }
            x += layerWidth + LAYER_GAP;
        }
        long layoutNanos = System.nanoTime() - started;
        return new GraphLayoutSnapshot(boxes, graph.links(), new GraphLayoutSnapshot.Bounds(0, 0, maxRight, maxBottom),
            layoutNanos, version);
    }

    private static Map<Integer, Integer> assignLayers(ClientCraftingGraph graph) {
        Map<Integer, Integer> layer = new HashMap<>();
        if (!graph.nodes().containsKey(graph.rootId())) {
            graph.nodes().keySet().forEach(id -> layer.put(id, 0));
            return layer;
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        layer.put(graph.rootId(), 0);
        queue.add(graph.rootId());
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            int nextLayer = layer.get(current) + 1;
            for (int dependency : graph.downstream(current)) {
                if (!layer.containsKey(dependency)) {
                    layer.put(dependency, nextLayer);
                    queue.addLast(dependency);
                }
            }
            // Pattern-output edges are represented material -> pattern. This also covers reverse-oriented traces.
            for (int neighbor : graph.upstream(current)) if (!layer.containsKey(neighbor)) {
                layer.put(neighbor, nextLayer);
                queue.addLast(neighbor);
            }
        }
        int orphanLayer = layer.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        for (int id : graph.nodes().keySet()) layer.putIfAbsent(id, orphanLayer);
        return layer;
    }

    private static double barycenter(ClientCraftingGraph graph, int id, Map<Integer, Float> order) {
        double total = 0;
        int count = 0;
        for (int neighbor : graph.upstream(id)) if (order.containsKey(neighbor)) {
            total += order.get(neighbor);
            count++;
        }
        return count == 0 ? id : total / count;
    }

    private static float width(ClientCraftingGraph.Node node) {
        return switch (node.kind()) {
            case MATERIAL -> GraphLayoutSnapshot.MATERIAL_WIDTH;
            case PATTERN -> GraphLayoutSnapshot.PATTERN_WIDTH;
            case CYCLE_GROUP -> GraphLayoutSnapshot.CYCLE_WIDTH;
        };
    }

    private static float height(ClientCraftingGraph.Node node) {
        return switch (node.kind()) {
            case MATERIAL -> GraphLayoutSnapshot.MATERIAL_HEIGHT;
            case PATTERN -> GraphLayoutSnapshot.PATTERN_HEIGHT;
            case CYCLE_GROUP -> GraphLayoutSnapshot.CYCLE_HEIGHT;
        };
    }
}
