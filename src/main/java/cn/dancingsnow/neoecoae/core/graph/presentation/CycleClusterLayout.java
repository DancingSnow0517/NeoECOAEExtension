package cn.dancingsnow.neoecoae.core.graph.presentation;

import cn.dancingsnow.neoecoae.core.graph.GraphAdjacency;
import cn.dancingsnow.neoecoae.core.graph.TopoSort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Places independent SCC circles in condensation-DAG layers and routes explicit bridge materials between them. */
public final class CycleClusterLayout implements GraphLayoutEngine {
    private static final float PADDING = 28;
    private static final float LAYER_GAP = 110;
    private static final float COMPONENT_GAP = 54;

    @Override
    public GraphLayoutSnapshot layout(LayoutGraph graph, long version) {
        long started = System.nanoTime();
        CycleTopology.Cluster cluster = graph.focusedCluster();
        if (cluster == null) return new LayeredGraphLayout().layout(graph, version);

        Map<Integer, Integer> ownerByNode = owners(graph, cluster);
        Map<Integer, LocalLayout> locals = new LinkedHashMap<>();
        CircularCycleLayout circular = new CircularCycleLayout();
        for (int componentId : cluster.componentIds()) {
            Map<Integer, LayoutGraph.Node> localNodes = new LinkedHashMap<>();
            for (var entry : graph.nodes().entrySet()) if (ownerByNode.get(entry.getKey()) != null
                    && ownerByNode.get(entry.getKey()) == componentId) localNodes.put(entry.getKey(), entry.getValue());
            List<? extends LayoutGraph.Link> localLinks = graph.links().stream()
                .filter(link -> localNodes.containsKey(link.fromId()) && localNodes.containsKey(link.toId())).toList();
            if (localNodes.isEmpty()) continue;
            int root = localNodes.keySet().iterator().next();
            LayoutGraph localGraph = LayoutGraph.of(root, localNodes, localLinks);
            GraphLayoutSnapshot snapshot = circular.layout(localGraph, version);
            locals.put(componentId, new LocalLayout(snapshot, 0, 0));
        }

        Map<Integer, Integer> depths = condensationDepths(cluster);
        Map<Integer, List<Integer>> layers = new LinkedHashMap<>();
        for (int componentId : cluster.componentIds()) {
            layers.computeIfAbsent(depths.getOrDefault(componentId, 0), ignored -> new ArrayList<>()).add(componentId);
        }
        Map<Integer, GraphLayoutSnapshot.Box> boxes = new LinkedHashMap<>();
        Map<LayoutGraph.Link, List<GraphLayoutSnapshot.Point>> routes = new LinkedHashMap<>();
        Map<Integer, GraphLayoutSnapshot.Point> centers = new HashMap<>();
        float x = PADDING;
        for (int depth : layers.keySet().stream().sorted().toList()) {
            List<Integer> components = layers.get(depth).stream().sorted().toList();
            float layerWidth = 1;
            for (int componentId : components) {
                LocalLayout local = locals.get(componentId);
                if (local != null) layerWidth = Math.max(layerWidth, local.snapshot().bounds().width());
            }
            float y = PADDING;
            for (int componentId : components) {
                LocalLayout local = locals.get(componentId);
                if (local == null) continue;
                float offsetX = x + (layerWidth - local.snapshot().bounds().width()) / 2;
                float offsetY = y;
                LocalLayout positioned = new LocalLayout(local.snapshot(), offsetX, offsetY);
                locals.put(componentId, positioned);
                copyLocal(positioned, boxes, routes);
                centers.put(componentId, new GraphLayoutSnapshot.Point(
                    offsetX + local.snapshot().bounds().width() / 2,
                    offsetY + local.snapshot().bounds().height() / 2));
                y += local.snapshot().bounds().height() + COMPONENT_GAP;
            }
            x += layerWidth + LAYER_GAP;
        }

        placeBridgeMaterials(graph, cluster, ownerByNode, centers, boxes);
        GraphPortAllocator ports = new GraphPortAllocator();
        for (LayoutGraph.Link link : graph.links()) {
            if (routes.containsKey(link)) continue;
            GraphLayoutSnapshot.Box from = boxes.get(link.fromId());
            GraphLayoutSnapshot.Box to = boxes.get(link.toId());
            if (from != null && to != null) routes.put(link, route(from, to, link.materialNodeId(), ports));
        }
        GraphLayoutSnapshot.Bounds bounds = bounds(boxes, routes);
        return new GraphLayoutSnapshot(boxes, graph.links(), routes, bounds, System.nanoTime() - started, version);
    }

    private static Map<Integer, Integer> owners(LayoutGraph graph, CycleTopology.Cluster cluster) {
        Map<Integer, Integer> result = new HashMap<>();
        for (var cycle : graph.cycleGroups()) if (cluster.componentIds().contains(cycle.componentId())) {
            for (int memberId : cycle.memberNodeIds()) result.put(memberId, cycle.componentId());
        }
        for (var node : graph.nodes().values()) if (node.componentId() >= 0
                && cluster.componentIds().contains(node.componentId())) {
            result.put(node.id(), node.componentId());
        }
        return result;
    }

    private static Map<Integer, Integer> condensationDepths(CycleTopology.Cluster cluster) {
        GraphAdjacency adjacency = GraphAdjacency.of(cluster.flows(), CycleTopology.Flow::fromComponentId,
            CycleTopology.Flow::toComponentId);
        List<Integer> components = cluster.componentIds().stream().sorted().toList();
        // Snapshot SCCs should make this a DAG. If malformed client data contains a cycle, keep SCC identities
        // separate and place the unprocessed components in layer zero instead of attempting a client-side merge.
        TopoSort.Result<Integer> sorted = TopoSort.kahnFifo(components, adjacency::downstream);
        Map<Integer, Integer> depth = new HashMap<>();
        for (int current : sorted.order()) {
            int nextDepth = depth.getOrDefault(current, 0) + 1;
            for (int next : adjacency.downstream(current)) depth.merge(next, nextDepth, Math::max);
        }
        for (int componentId : cluster.componentIds()) depth.putIfAbsent(componentId, 0);
        return depth;
    }

    private static void copyLocal(LocalLayout local, Map<Integer, GraphLayoutSnapshot.Box> boxes,
            Map<LayoutGraph.Link, List<GraphLayoutSnapshot.Point>> routes) {
        for (var box : local.snapshot().boxes().values()) boxes.put(box.nodeId(), new GraphLayoutSnapshot.Box(
            box.nodeId(), box.x() + local.offsetX(), box.y() + local.offsetY(), box.width(), box.height()));
        for (LayoutGraph.Link link : local.snapshot().links()) {
            List<GraphLayoutSnapshot.Point> points = local.snapshot().edgePoints(link).stream()
                .map(point -> new GraphLayoutSnapshot.Point(point.x() + local.offsetX(), point.y() + local.offsetY()))
                .toList();
            routes.put(link, points);
        }
    }

    private static void placeBridgeMaterials(LayoutGraph graph, CycleTopology.Cluster cluster,
            Map<Integer, Integer> owners, Map<Integer, GraphLayoutSnapshot.Point> centers,
            Map<Integer, GraphLayoutSnapshot.Box> boxes) {
        Map<Integer, List<CycleTopology.Flow>> byMaterial = new LinkedHashMap<>();
        for (CycleTopology.Flow flow : cluster.flows()) byMaterial.computeIfAbsent(flow.materialNodeId(),
            ignored -> new ArrayList<>()).add(flow);
        for (var entry : byMaterial.entrySet()) {
            int materialId = entry.getKey();
            if (boxes.containsKey(materialId) || owners.containsKey(materialId)) continue;
            float x = 0;
            float y = 0;
            int count = 0;
            for (CycleTopology.Flow flow : entry.getValue()) {
                var from = centers.get(flow.fromComponentId());
                var to = centers.get(flow.toComponentId());
                if (from == null || to == null) continue;
                x += (from.x() + to.x()) / 2;
                y += (from.y() + to.y()) / 2;
                count++;
            }
            if (count == 0) continue;
            float width = GraphLayoutSnapshot.COMPACT_MATERIAL_WIDTH;
            float height = GraphLayoutSnapshot.COMPACT_MATERIAL_HEIGHT;
            boxes.put(materialId, new GraphLayoutSnapshot.Box(materialId, x / count - width / 2,
                y / count - height / 2, width, height));
        }
    }

    private static List<GraphLayoutSnapshot.Point> route(GraphLayoutSnapshot.Box from,
            GraphLayoutSnapshot.Box to, int identity, GraphPortAllocator ports) {
        float lane = ((identity & 3) - 1.5f) * 7;
        GraphLayoutSnapshot.Point start = ports.attach(from, to.centerX(), to.centerY(), true, null);
        GraphLayoutSnapshot.Point end = ports.attach(to, from.centerX(), from.centerY(), false, null);
        float startX = start.x();
        float startY = start.y();
        float endX = end.x();
        float endY = end.y();
        // Cross-cluster links use an outer horizontal lane. Routing through the midpoint of two rings causes the
        // bridge line and its amount label to cut across nodes when the rings are vertically aligned.
        // Use a lower bridge lane so the inter-cycle connection reads as a dependency between rings and never
        // collides with the cycle titles or the top-most material nodes.
        float outerY = Math.max(from.y() + from.height(), to.y() + to.height()) + 34f + Math.abs(lane);
        return List.of(new GraphLayoutSnapshot.Point(startX, startY),
            new GraphLayoutSnapshot.Point(startX, outerY), new GraphLayoutSnapshot.Point(endX, outerY),
            new GraphLayoutSnapshot.Point(endX, endY));
    }

    private static GraphLayoutSnapshot.Bounds bounds(Map<Integer, GraphLayoutSnapshot.Box> boxes,
            Map<LayoutGraph.Link, List<GraphLayoutSnapshot.Point>> routes) {
        float right = 1;
        float bottom = 1;
        for (var box : boxes.values()) {
            right = Math.max(right, box.x() + box.width() + PADDING);
            bottom = Math.max(bottom, box.y() + box.height() + PADDING);
        }
        for (var points : routes.values()) for (var point : points) {
            right = Math.max(right, point.x() + PADDING);
            bottom = Math.max(bottom, point.y() + PADDING);
        }
        return new GraphLayoutSnapshot.Bounds(0, 0, right, bottom);
    }

    private record LocalLayout(GraphLayoutSnapshot snapshot, float offsetX, float offsetY) {}
}
