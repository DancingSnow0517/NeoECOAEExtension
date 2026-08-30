package cn.dancingsnow.neoecoae.client.craftinggraph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
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
    public enum Kind { MATERIAL, PATTERN, CYCLE_GROUP, FOLDER, REFERENCE }
    public enum View { MAIN, CYCLE_FOCUS }

    public record Node(int id, Kind kind, String label, @Nullable AEKey key,
            @Nullable CraftingGraphSnapshot.MaterialNode material,
            @Nullable CraftingGraphSnapshot.PatternNode pattern,
            @Nullable CraftingGraphSnapshot.CycleGroup cycle) {}

    public record Link(int fromId, int toId, long amount, CraftingGraphSnapshot.EdgeKind kind, boolean selected) {}

    private final CraftingGraphSnapshot source;
    private final View view;
    private final int rootId;
    private final int focusedCycleId;
    private final @Nullable Integer focusedMaterialId;
    private final Map<Integer, Node> nodes;
    private final List<Link> links;
    private final Map<Integer, CompactTreeNode> compactTreeNodes;
    private final boolean compactTree;
    private final Set<Integer> boundaryMaterialIds;
    private final Set<Integer> externalInputIds;
    private final Set<Integer> boundaryOutputIds;
    private final Map<Integer, Set<Integer>> upstream;
    private final Map<Integer, Set<Integer>> downstream;

    private ClientCraftingGraph(CraftingGraphSnapshot source, View view, int rootId, int focusedCycleId,
            Map<Integer, Node> nodes, List<Link> links) {
        this(source, view, rootId, focusedCycleId, null, nodes, links, Map.of(), false, Set.of(), Set.of(), Set.of());
    }

    private ClientCraftingGraph(CraftingGraphSnapshot source, View view, int rootId, int focusedCycleId,
            Map<Integer, Node> nodes, List<Link> links, Map<Integer, CompactTreeNode> compactTreeNodes,
            boolean compactTree) {
        this(source, view, rootId, focusedCycleId, null, nodes, links, compactTreeNodes, compactTree, Set.of(),
            Set.of(), Set.of());
    }

    private ClientCraftingGraph(CraftingGraphSnapshot source, View view, int rootId, int focusedCycleId,
            @Nullable Integer focusedMaterialId, Map<Integer, Node> nodes, List<Link> links,
            Map<Integer, CompactTreeNode> compactTreeNodes, boolean compactTree, Set<Integer> boundaryMaterialIds,
            Set<Integer> externalInputIds, Set<Integer> boundaryOutputIds) {
        this.source = source;
        this.view = view;
        this.rootId = rootId;
        this.focusedCycleId = focusedCycleId;
        this.focusedMaterialId = focusedMaterialId;
        this.nodes = Map.copyOf(nodes);
        this.links = List.copyOf(links);
        this.compactTreeNodes = Map.copyOf(compactTreeNodes);
        this.compactTree = compactTree;
        this.boundaryMaterialIds = Set.copyOf(boundaryMaterialIds);
        this.externalInputIds = Set.copyOf(externalInputIds);
        this.boundaryOutputIds = Set.copyOf(boundaryOutputIds);
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
            source.focusedMaterialId, nodes, links, compactTreeNodes, true, source.boundaryMaterialIds,
            source.externalInputIds, source.boundaryOutputIds);
    }

    public static ClientCraftingGraph main(CraftingGraphSnapshot snapshot, boolean advanced) {
        Map<Integer, CraftingGraphSnapshot.CycleGroup> cycleByMember = new HashMap<>();
        for (var cycle : snapshot.cycleGroups()) {
            for (int member : cycle.memberNodeIds()) cycleByMember.put(member, cycle);
        }
        Map<Integer, Node> nodes = new LinkedHashMap<>();
        for (var material : snapshot.nodes()) {
            if (!cycleByMember.containsKey(material.nodeId())) {
                nodes.put(material.nodeId(), materialNode(material));
            }
        }
        for (var cycle : snapshot.cycleGroups()) {
            int id = cycleVisualId(cycle.componentId());
            nodes.put(id, new Node(id, Kind.CYCLE_GROUP, "Cycle #" + cycle.componentId(), null, null, null, cycle));
        }
        for (var pattern : snapshot.patterns()) {
            if (!advanced && pattern.status() != CraftingGraphSnapshot.CandidateStatus.SELECTED) continue;
            boolean internalCyclePattern = pattern.componentId() >= 0 && snapshot.cycleGroups().stream()
                .anyMatch(cycle -> cycle.componentId() == pattern.componentId());
            if (internalCyclePattern) continue;
            int id = pattern.patternNodeId();
            nodes.put(id, patternNode(id, pattern));
        }

        List<Link> links = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        for (var edge : snapshot.edges()) {
            int from = collapse(edge.fromId(), cycleByMember);
            int to = collapse(edge.toId(), cycleByMember);
            if (from == to || !nodes.containsKey(from) || !nodes.containsKey(to)) continue;
            String identity = from + ":" + to + ":" + edge.kind();
            if (dedupe.add(identity)) links.add(new Link(from, to, edge.amount(), edge.kind(), edge.selected()));
        }
        int root = collapse(snapshot.rootNodeId(), cycleByMember);
        return new ClientCraftingGraph(snapshot, View.MAIN, root, -1, nodes, links);
    }

    public static ClientCraftingGraph cycle(CraftingGraphSnapshot snapshot, int componentId, boolean advanced) {
        return cycle(snapshot, componentId, advanced, null);
    }

    /** Builds a material-flow view without consulting any planner or solver on the client. */
    public static ClientCraftingGraph cycle(CraftingGraphSnapshot snapshot, int componentId, boolean advanced,
            @Nullable AEKey focusedMaterial) {
        var cycle = snapshot.cycleGroups().stream().filter(value -> value.componentId() == componentId).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown cycle component " + componentId));
        Set<Integer> members = Set.copyOf(cycle.memberNodeIds());
        Map<Integer, CraftingGraphSnapshot.PatternNode> cyclePatterns = new LinkedHashMap<>();
        Set<Integer> externalInputIds = new LinkedHashSet<>();
        Set<Integer> boundaryOutputIds = new LinkedHashSet<>();
        for (var pattern : snapshot.patterns()) {
            if (pattern.componentId() != componentId) continue;
            cyclePatterns.put(pattern.patternNodeId(), pattern);
            for (var input : pattern.inputs()) {
                if (!members.contains(input.materialNodeId())) externalInputIds.add(input.materialNodeId());
            }
            for (var output : pattern.outputs()) {
                if (!members.contains(output.materialNodeId())) boundaryOutputIds.add(output.materialNodeId());
            }
        }
        for (var external : cycle.externalInputs()) {
            snapshot.nodes().stream().filter(node -> node.key().equals(external.key()))
                .filter(node -> !members.contains(node.nodeId()))
                .findFirst().ifPresent(node -> externalInputIds.add(node.nodeId()));
        }
        Set<Integer> boundaryMaterialIds = new LinkedHashSet<>(externalInputIds);
        boundaryMaterialIds.addAll(boundaryOutputIds);
        Map<Integer, Node> nodes = new LinkedHashMap<>();
        for (var material : snapshot.nodes()) if (members.contains(material.nodeId())
                || boundaryMaterialIds.contains(material.nodeId())) {
            nodes.put(material.nodeId(), materialNode(material));
        }
        for (var pattern : cyclePatterns.values()) {
            int id = pattern.patternNodeId();
            nodes.put(id, patternNode(id, pattern));
        }
        List<Link> links = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        for (var pattern : cyclePatterns.values()) {
            int patternId = pattern.patternNodeId();
            boolean selected = pattern.status() == CraftingGraphSnapshot.CandidateStatus.SELECTED;
            for (var input : pattern.inputs()) {
                if (nodes.containsKey(input.materialNodeId())
                        && dedupe.add(input.materialNodeId() + ":" + patternId + ":input:" + input.amount())) {
                    // Focus view deliberately reverses the dependency edge: material/input -> pattern.
                    links.add(new Link(input.materialNodeId(), patternId, input.amount(),
                        CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, selected));
                }
            }
            for (var output : pattern.outputs()) {
                if (nodes.containsKey(output.materialNodeId())
                        && dedupe.add(patternId + ":" + output.materialNodeId() + ":output:" + output.amount())) {
                    CraftingGraphSnapshot.EdgeKind kind = members.contains(output.materialNodeId())
                        ? CraftingGraphSnapshot.EdgeKind.PATTERN_OUTPUT : CraftingGraphSnapshot.EdgeKind.BYPRODUCT;
                    links.add(new Link(patternId, output.materialNodeId(), output.amount(), kind, selected));
                }
            }
        }
        if (cyclePatterns.isEmpty()) {
            // Compatibility for snapshots produced before PatternNode was part of the cycle contract. New snapshots
            // always take the material -> pattern -> material path above and never use this direct-edge fallback.
            for (var edge : snapshot.edges()) {
                if (members.contains(edge.fromId()) && members.contains(edge.toId())
                        && dedupe.add(edge.fromId() + ":" + edge.toId() + ":legacy:" + edge.amount())) {
                    links.add(new Link(edge.fromId(), edge.toId(), edge.amount(), edge.kind(), edge.selected()));
                }
            }
        }
        int root = cycle.requiredOutputs().stream().map(CraftingGraphSnapshot.KeyAmount::key)
            .map(key -> snapshot.nodes().stream().filter(node -> node.key().equals(key)).findFirst().orElse(null))
            .filter(java.util.Objects::nonNull).map(CraftingGraphSnapshot.MaterialNode::nodeId).findFirst()
            .orElse(cycle.memberNodeIds().isEmpty() ? -1 : cycle.memberNodeIds().getFirst());
        Integer focusedId = focusedMaterial == null ? null : snapshot.nodes().stream()
            .filter(node -> members.contains(node.nodeId()) && node.key().equals(focusedMaterial))
            .map(CraftingGraphSnapshot.MaterialNode::nodeId).findFirst().orElse(null);
        return new ClientCraftingGraph(snapshot, View.CYCLE_FOCUS, root, componentId, focusedId, nodes, links, Map.of(),
            false, boundaryMaterialIds, externalInputIds, boundaryOutputIds);
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
        return new ClientCraftingGraph(source, view, focusId, focusedCycleId, focusedMaterialId, subset, subsetLinks,
            Map.of(), compactTree, boundaryMaterialIds, externalInputIds, boundaryOutputIds);
    }

    public CraftingGraphSnapshot source() { return source; }
    public View view() { return view; }
    public int rootId() { return rootId; }
    public int focusedCycleId() { return focusedCycleId; }
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

    private static int collapse(int id, Map<Integer, CraftingGraphSnapshot.CycleGroup> cycleByMember) {
        var cycle = cycleByMember.get(id);
        return cycle == null ? id : cycleVisualId(cycle.componentId());
    }

    private static Node materialNode(CraftingGraphSnapshot.MaterialNode material) {
        return new Node(material.nodeId(), Kind.MATERIAL, material.key().getDisplayName().getString(), material.key(),
            material, null, null);
    }

    private static Node patternNode(int id, CraftingGraphSnapshot.PatternNode pattern) {
        return new Node(id, Kind.PATTERN, pattern.displayIdentity(), null, null, pattern, null);
    }

    private static Map<Integer, Set<Integer>> freeze(Map<Integer, Set<Integer>> values) {
        Map<Integer, Set<Integer>> result = new HashMap<>();
        values.forEach((key, value) -> result.put(key, Set.copyOf(value)));
        return Map.copyOf(result);
    }
}
