package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable amount-free graph compiled only from the current goal's reachable closure. */
public final class CraftingDependencyGraph {
    private final AEKey goal;
    private final Map<AEKey, CraftingGraphNode> nodes;
    private final List<CraftingGraphEdge> edges;
    private final Map<AEKey, List<CraftingGraphEdge>> outgoing;

    public CraftingDependencyGraph(AEKey goal, Map<AEKey, CraftingGraphNode> nodes,
            List<CraftingGraphEdge> edges) {
        this.goal = goal;
        this.nodes = Map.copyOf(nodes);
        this.edges = List.copyOf(edges);
        Map<AEKey, List<CraftingGraphEdge>> byProducer = new LinkedHashMap<>();
        for (AEKey key : nodes.keySet()) byProducer.put(key, new ArrayList<>());
        for (CraftingGraphEdge edge : edges) {
            byProducer.computeIfAbsent(edge.producer(), ignored -> new ArrayList<>()).add(edge);
        }
        Map<AEKey, List<CraftingGraphEdge>> frozen = new LinkedHashMap<>();
        byProducer.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        this.outgoing = Map.copyOf(frozen);
    }

    public AEKey goal() { return goal; }
    public Map<AEKey, CraftingGraphNode> nodes() { return nodes; }
    public List<CraftingGraphEdge> edges() { return edges; }
    public List<CraftingGraphEdge> outgoing(AEKey key) { return outgoing.getOrDefault(key, List.of()); }
}
