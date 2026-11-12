package cn.dancingsnow.neoecoae.crafting.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Client-only presentation model. It never references planner or solver classes. */
public final class ClientCraftingGraph {
    public enum Kind { MATERIAL, PATTERN, CYCLE_GROUP, CYCLE_CLUSTER, FOLDER, REFERENCE, RECURSION }
    public enum View { MAIN, CYCLE_FOCUS, CYCLE_CLUSTER }

    public record Node(int id, Kind kind, String label, @Nullable AEKey key,
            @Nullable CraftingGraphSnapshot.MaterialNode material,
            @Nullable CraftingGraphSnapshot.PatternNode pattern,
            @Nullable CraftingGraphSnapshot.CycleGroup cycle, @Nullable CycleCluster cluster,
            @Nullable RecursiveCycleNode recursion) {
        public Node(int id, Kind kind, String label, @Nullable AEKey key,
                @Nullable CraftingGraphSnapshot.MaterialNode material,
                @Nullable CraftingGraphSnapshot.PatternNode pattern,
                @Nullable CraftingGraphSnapshot.CycleGroup cycle, @Nullable CycleCluster cluster) {
            this(id, kind, label, key, material, pattern, cycle, cluster, null);
        }
        public Node(int id, Kind kind, String label, @Nullable AEKey key,
                @Nullable CraftingGraphSnapshot.MaterialNode material,
                @Nullable CraftingGraphSnapshot.PatternNode pattern,
                @Nullable CraftingGraphSnapshot.CycleGroup cycle) {
            this(id, kind, label, key, material, pattern, cycle, null, null);
        }
    }

    public record Link(int fromId, int toId, cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount plannerAmount,
            CraftingGraphSnapshot.EdgeKind kind, boolean selected,
            int materialNodeId) {
        public Link(int fromId, int toId, long amount, CraftingGraphSnapshot.EdgeKind kind, boolean selected,
                int materialNodeId) {
            this(fromId, toId, cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount.of(amount), kind, selected, materialNodeId);
        }
        public Link(int fromId, int toId, long amount, CraftingGraphSnapshot.EdgeKind kind, boolean selected) {
            this(fromId, toId, amount, kind, selected, -1);
        }
        public long amount() { return plannerAmount.longValueSaturated(); }
    }

    private final CraftingGraphSnapshot source;
    private final View view;
    private final int rootId;
    private final int focusedCycleId;
    private final @Nullable CycleCluster focusedCluster;
    private final @Nullable Integer focusedMaterialId;
    private final Map<Integer, Node> nodes;
    private final List<Link> links;
    private final Map<Integer, CompactTreeNode> compactTreeNodes;
    private final boolean compactTree;
    private final boolean recursiveCycle;
    private final Set<Integer> boundaryMaterialIds;
    private final Set<Integer> externalInputIds;
    private final Set<Integer> boundaryOutputIds;
    private final Map<Integer, Set<Integer>> upstream;
    private final Map<Integer, Set<Integer>> downstream;

    private ClientCraftingGraph(CraftingGraphSnapshot source, View view, int rootId, int focusedCycleId,
            Map<Integer, Node> nodes, List<Link> links) {
        this(source, view, rootId, focusedCycleId, null, null, nodes, links, Map.of(), false, Set.of(), Set.of(),
            Set.of());
    }

    private ClientCraftingGraph(CraftingGraphSnapshot source, View view, int rootId, int focusedCycleId,
            Map<Integer, Node> nodes, List<Link> links, Map<Integer, CompactTreeNode> compactTreeNodes,
            boolean compactTree) {
        this(source, view, rootId, focusedCycleId, null, null, nodes, links, compactTreeNodes, compactTree, Set.of(),
            Set.of(), Set.of());
    }

    private ClientCraftingGraph(CraftingGraphSnapshot source, View view, int rootId, int focusedCycleId,
            @Nullable CycleCluster focusedCluster, @Nullable Integer focusedMaterialId,
            Map<Integer, Node> nodes, List<Link> links,
            Map<Integer, CompactTreeNode> compactTreeNodes, boolean compactTree, Set<Integer> boundaryMaterialIds,
            Set<Integer> externalInputIds, Set<Integer> boundaryOutputIds) {
        this.source = source;
        this.view = view;
        this.rootId = rootId;
        this.focusedCycleId = focusedCycleId;
        this.focusedCluster = focusedCluster;
        this.focusedMaterialId = focusedMaterialId;
        this.nodes = nodes;
        this.links = links;
        this.compactTreeNodes = compactTreeNodes;
        this.compactTree = compactTree;
        this.recursiveCycle = view == View.CYCLE_FOCUS && nodes.values().stream().anyMatch(node -> node.recursion() != null);
        this.boundaryMaterialIds = boundaryMaterialIds;
        this.externalInputIds = externalInputIds;
        this.boundaryOutputIds = boundaryOutputIds;
        Map<Integer, Set<Integer>> up = new HashMap<>();
        Map<Integer, Set<Integer>> down = new HashMap<>();
        for (Link link : links) {
            down.computeIfAbsent(link.fromId(), ignored -> new LinkedHashSet<>()).add(link.toId());
            up.computeIfAbsent(link.toId(), ignored -> new LinkedHashSet<>()).add(link.fromId());
        }
        this.upstream = freeze(up);
        this.downstream = freeze(down);
    }

    /** Package-visible benchmark fixture factory; production callers use {@link #main} and {@link #cycle}. */
    static ClientCraftingGraph synthetic(int rootId, Map<Integer, Node> nodes, List<Link> links) {
        return new ClientCraftingGraph(CraftingGraphSnapshot.EMPTY, View.MAIN, rootId, -1, nodes, links);
    }

    static ClientCraftingGraph compact(ClientCraftingGraph source, Map<Integer, Node> nodes, List<Link> links,
            Map<Integer, CompactTreeNode> compactTreeNodes, int rootId) {
        return new ClientCraftingGraph(source.source, source.view, rootId, source.focusedCycleId,
            source.focusedCluster, source.focusedMaterialId, nodes, links, compactTreeNodes, true,
            source.boundaryMaterialIds,
            source.externalInputIds, source.boundaryOutputIds);
    }

    public static ClientCraftingGraph main(CraftingGraphSnapshot snapshot, boolean advanced) {
        return main(snapshot, advanced, true);
    }

    private static ClientCraftingGraph main(CraftingGraphSnapshot snapshot, boolean advanced, boolean groupClusters) {
        Map<Integer, CraftingGraphSnapshot.CycleGroup> cycleByMember = new HashMap<>();
        Map<Integer, CraftingGraphSnapshot.CycleGroup> cycleByComponent = new HashMap<>();
        for (var cycle : snapshot.cycleGroups()) {
            cycleByComponent.put(cycle.componentId(), cycle);
            for (int member : cycle.memberNodeIds()) cycleByMember.put(member, cycle);
        }
        Map<Integer, CraftingGraphSnapshot.CycleGroup> cycleByNode = new HashMap<>(cycleByMember);
        for (var pattern : snapshot.patterns()) {
            var cycle = cycleByComponent.get(pattern.componentId());
            if (cycle != null) cycleByNode.put(pattern.patternNodeId(), cycle);
        }
        List<CycleCluster> clusters = groupClusters ? CycleClusterProjection.derive(snapshot) : List.of();
        Map<Integer, CycleCluster> clusterByComponent = new HashMap<>();
        for (CycleCluster cluster : clusters) for (int componentId : cluster.componentIds()) {
            clusterByComponent.put(componentId, cluster);
        }
        for (CycleCluster cluster : clusters) for (InterCycleFlow flow : cluster.flows()) {
            // A material passed between two modules belongs inside their group. Leaving it outside would
            // redraw a module -> material -> module feedback loop around the supposedly collapsed box.
            cycleByNode.putIfAbsent(flow.materialNodeId(), cycleByComponent.get(flow.fromComponentId()));
        }
        Map<Integer, Node> nodes = new LinkedHashMap<>();
        for (var material : snapshot.nodes()) {
            if (!cycleByNode.containsKey(material.nodeId())) {
                nodes.put(material.nodeId(), materialNode(material));
            }
        }
        for (var cycle : snapshot.cycleGroups()) {
            if (clusterByComponent.containsKey(cycle.componentId())) continue;
            int id = cycleVisualId(cycle.componentId());
            nodes.put(id, new Node(id, Kind.CYCLE_GROUP, "递归模块 #" + cycle.componentId(), null, null, null, cycle));
        }
        for (CycleCluster cluster : clusters) {
            int id = clusterVisualId(cluster.clusterId());
            nodes.put(id, new Node(id, Kind.CYCLE_CLUSTER, "递归模块组 #" + cluster.clusterId(), null, null,
                null, null, cluster));
        }
        for (var pattern : snapshot.patterns()) {
            if (!advanced && pattern.status() != CraftingGraphSnapshot.CandidateStatus.SELECTED) continue;
            boolean internalCyclePattern = cycleByComponent.containsKey(pattern.componentId());
            if (internalCyclePattern) continue;
            int id = pattern.patternNodeId();
            nodes.put(id, patternNode(id, pattern));
        }

        List<Link> links = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        for (var edge : snapshot.edges()) {
            int from = collapse(edge.fromId(), cycleByNode, clusterByComponent);
            int to = collapse(edge.toId(), cycleByNode, clusterByComponent);
            if (from == to || !nodes.containsKey(from) || !nodes.containsKey(to)) continue;
            int materialId = edgeMaterialId(edge);
            String identity = from + ":" + to + ":" + edge.kind() + ":" + materialId + ":" + edge.plannerAmount()
                + ":" + edge.fromId() + ":" + edge.toId();
            if (dedupe.add(identity)) links.add(new Link(from, to, edge.plannerAmount(), edge.kind(), edge.selected(),
                materialId));
        }
        int root = collapse(snapshot.rootNodeId(), cycleByNode, clusterByComponent);
        return new ClientCraftingGraph(snapshot, View.MAIN, root, -1, nodes, links);
    }

    public static ClientCraftingGraph cycle(CraftingGraphSnapshot snapshot, int componentId, boolean advanced) {
        return cycle(snapshot, componentId, advanced, null);
    }

    static ClientCraftingGraph recursive(CraftingGraphSnapshot snapshot, int componentId, int rootId,
            @Nullable Integer focusedMaterialId, Map<Integer, Node> nodes, List<Link> links,
            Set<Integer> externalInputs, Set<Integer> outputs) {
        Set<Integer> boundary = new LinkedHashSet<>(externalInputs);
        boundary.addAll(outputs);
        return new ClientCraftingGraph(snapshot, View.CYCLE_FOCUS, rootId, componentId, null, focusedMaterialId,
            nodes, links, Map.of(), false, boundary, externalInputs, outputs);
    }

    /** The server supplies bounded checkpoints of the selected order; the client only projects them. */
    public static ClientCraftingGraph cycle(CraftingGraphSnapshot snapshot, int componentId, boolean advanced,
            @Nullable AEKey focusedMaterial) {
        return RecursiveCycleProjection.project(snapshot, componentId, focusedMaterial);
    }

    /** Related cycles remain separate modules inside their shared boundary. */
    public static ClientCraftingGraph cluster(CraftingGraphSnapshot snapshot, CycleCluster cluster,
            boolean advanced) {
        ClientCraftingGraph flat = main(snapshot, advanced, false);
        Set<Integer> moduleIds = cluster.componentIds().stream().map(ClientCraftingGraph::cycleVisualId)
            .collect(java.util.stream.Collectors.toSet());
        Set<Integer> visible = new LinkedHashSet<>(moduleIds);
        for (Link link : flat.links()) {
            if ((moduleIds.contains(link.fromId()) || moduleIds.contains(link.toId()))
                    && flat.nodes().get(link.fromId()).kind() != Kind.PATTERN
                    && flat.nodes().get(link.toId()).kind() != Kind.PATTERN) {
                visible.add(link.fromId());
                visible.add(link.toId());
            }
        }
        Map<Integer, Node> nodes = new LinkedHashMap<>();
        flat.nodes().forEach((id, node) -> { if (visible.contains(id)) nodes.put(id, node); });
        List<Link> links = flat.links().stream()
            .filter(link -> nodes.containsKey(link.fromId()) && nodes.containsKey(link.toId())).toList();
        int root = nodes.containsKey(flat.rootId()) ? flat.rootId()
            : cluster.componentIds().isEmpty() ? -1 : cycleVisualId(cluster.componentIds().get(0));
        return new ClientCraftingGraph(snapshot, View.CYCLE_CLUSTER, root, -1, cluster, null, nodes, links,
            Map.of(), false, Set.of(), Set.of(), Set.of());
    }

    public ClientCraftingGraph limited(int focusId, int depth, Set<Integer> collapsed) {
        if (!nodes.containsKey(focusId) || depth < 0) return this;
        Set<Integer> visible = new LinkedHashSet<>();
        Set<Integer> frontier = Set.of(focusId);
        for (int level = 0; level <= depth && !frontier.isEmpty(); level++) {
            visible.addAll(frontier);
            Set<Integer> next = new LinkedHashSet<>();
            for (int id : frontier) {
                if (collapsed.contains(id)) continue;
                next.addAll(upstream(id));
                next.addAll(downstream(id));
            }
            next.removeAll(visible);
            frontier = next;
        }
        Map<Integer, Node> subset = new LinkedHashMap<>();
        for (int id : visible) subset.put(id, nodes.get(id));
        List<Link> subsetLinks = links.stream()
            .filter(link -> visible.contains(link.fromId()) && visible.contains(link.toId())).toList();
        return new ClientCraftingGraph(source, view, focusId, focusedCycleId, focusedCluster, focusedMaterialId,
            subset, subsetLinks,
            Map.of(), compactTree, boundaryMaterialIds, externalInputIds, boundaryOutputIds);
    }

    /** Keeps missing materials and every dependency node needed to connect them to the plan root. */
    public ClientCraftingGraph missingPaths() {
        if (view != View.MAIN || !nodes.containsKey(rootId)) return this;

        boolean hasSelectedLinks = links.stream().anyMatch(Link::selected);
        Map<Integer, Set<Integer>> activeUpstream = new HashMap<>();
        Map<Integer, Set<Integer>> activeDownstream = new HashMap<>();
        for (Link link : links) {
            if (hasSelectedLinks && !link.selected()) continue;
            activeDownstream.computeIfAbsent(link.fromId(), ignored -> new LinkedHashSet<>()).add(link.toId());
            activeUpstream.computeIfAbsent(link.toId(), ignored -> new LinkedHashSet<>()).add(link.fromId());
        }
        Set<Integer> reachable = new LinkedHashSet<>();
        var frontier = new ArrayDeque<Integer>();
        frontier.add(rootId);
        while (!frontier.isEmpty()) {
            int id = frontier.removeFirst();
            if (!reachable.add(id)) continue;
            for (int child : activeDownstream.getOrDefault(id, Set.of())) frontier.addLast(child);
        }

        Set<Integer> visible = new LinkedHashSet<>();
        var missingFrontier = new ArrayDeque<Integer>();
        for (int id : reachable) {
            Node node = nodes.get(id);
            if (node != null && node.material() != null
                    && node.material().status() == CraftingGraphSnapshot.MaterialStatus.MISSING) {
                visible.add(id);
                missingFrontier.addLast(id);
            }
        }
        while (!missingFrontier.isEmpty()) {
            int id = missingFrontier.removeFirst();
            for (int parent : activeUpstream.getOrDefault(id, Set.of())) {
                if (reachable.contains(parent) && visible.add(parent)) missingFrontier.addLast(parent);
            }
        }

        // Preserve a stable anchor when a valid plan contains no missing material.
        visible.add(rootId);
        Map<Integer, Node> subset = new LinkedHashMap<>();
        for (int id : visible) subset.put(id, nodes.get(id));
        List<Link> subsetLinks = links.stream()
            .filter(link -> !hasSelectedLinks || link.selected())
            .filter(link -> visible.contains(link.fromId()) && visible.contains(link.toId())).toList();
        return new ClientCraftingGraph(source, view, rootId, focusedCycleId, focusedCluster, focusedMaterialId,
            subset, subsetLinks, Map.of(), false, boundaryMaterialIds, externalInputIds, boundaryOutputIds);
    }

    public CraftingGraphSnapshot source() { return source; }
    public View view() { return view; }
    public boolean isRecursiveCycle() { return recursiveCycle; }
    public int rootId() { return rootId; }
    public int focusedCycleId() { return focusedCycleId; }
    public @Nullable CycleCluster focusedCluster() { return focusedCluster; }
    public @Nullable Integer focusedMaterialId() { return focusedMaterialId; }
    public Map<Integer, Node> nodes() { return nodes; }
    public List<Link> links() { return links; }
    public boolean isCompactTree() { return compactTree; }
    public boolean isBoundaryMaterial(int id) { return boundaryMaterialIds.contains(id); }
    public boolean isExternalInput(int id) { return externalInputIds.contains(id); }
    public boolean isBoundaryOutput(int id) { return boundaryOutputIds.contains(id); }
    public Map<Integer, CompactTreeNode> compactTreeNodes() { return compactTreeNodes; }
    public CompactTreeNode compactTreeNode(int id) { return compactTreeNodes.get(id); }
    public Set<Integer> upstream(int id) { return upstream.getOrDefault(id, Set.of()); }
    public Set<Integer> downstream(int id) { return downstream.getOrDefault(id, Set.of()); }

    public Set<Integer> neighborhood(int id) {
        Set<Integer> result = new LinkedHashSet<>(upstream(id));
        result.addAll(downstream(id));
        return result;
    }

    public static int cycleVisualId(int componentId) { return Integer.MIN_VALUE + componentId; }
    public static int clusterVisualId(int clusterId) { return Integer.MIN_VALUE / 2 + clusterId; }

    private static int collapse(int id, Map<Integer, CraftingGraphSnapshot.CycleGroup> cycleByMember,
            Map<Integer, CycleCluster> clusterByComponent) {
        var cycle = cycleByMember.get(id);
        if (cycle == null) return id;
        CycleCluster cluster = clusterByComponent.get(cycle.componentId());
        return cluster == null ? cycleVisualId(cycle.componentId()) : clusterVisualId(cluster.clusterId());
    }

    private static int edgeMaterialId(CraftingGraphSnapshot.Edge edge) {
        return switch (edge.kind()) {
            case PATTERN_OUTPUT, BYPRODUCT -> edge.fromId() >= 0 ? edge.fromId() : edge.toId();
            case PATTERN_INPUT, CYCLE_INTERNAL -> edge.toId() >= 0 ? edge.toId() : edge.fromId();
        };
    }

    private static Node materialNode(CraftingGraphSnapshot.MaterialNode material) {
        String label = material.key() == null ? "Material #" + material.nodeId()
            : material.key().getDisplayName().getString();
        return new Node(material.nodeId(), Kind.MATERIAL, label, material.key(),
            material, null, null);
    }

    private static Node patternNode(int id, CraftingGraphSnapshot.PatternNode pattern) {
        return new Node(id, Kind.PATTERN, pattern.displayIdentity(), null, null, pattern, null);
    }

    private static Map<Integer, Set<Integer>> freeze(Map<Integer, Set<Integer>> values) {
        Map<Integer, Set<Integer>> result = new HashMap<>();
        values.forEach((key, value) -> result.put(key, value));
        return result;
    }
}
