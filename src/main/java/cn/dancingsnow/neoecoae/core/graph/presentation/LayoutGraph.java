package cn.dancingsnow.neoecoae.core.graph.presentation;

import cn.dancingsnow.neoecoae.core.graph.GraphAdjacency;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Geometry-only view of a graph. Existing presentation payloads implement it without copying nodes or edges. */
public interface LayoutGraph {
    enum Kind { MATERIAL, PATTERN, CYCLE_GROUP, CYCLE_CLUSTER, FOLDER, REFERENCE }
    enum EdgeKind { PATTERN_OUTPUT, PATTERN_INPUT, BYPRODUCT, CYCLE_INTERNAL }
    interface Node {
        int id();
        default String label() { return ""; }
        default boolean missing() { return false; }
        default boolean unsupported() { return false; }
        Kind layoutKind();
        default int componentId() { return -1; }
    }
    interface Link {
        int fromId();
        int toId();
        int materialNodeId();
        long amount();
        boolean selected();
        EdgeKind layoutKind();
    }
    int rootId();
    Map<Integer, ? extends Node> nodes();
    List<? extends Link> links();
    GraphAdjacency adjacency();
    default CycleTopology.Cluster focusedCluster() { return null; }
    default List<? extends CycleTopology.Cycle> cycleGroups() { return List.of(); }
    static LayoutGraph of(int root, Map<Integer, ? extends Node> nodes, List<? extends Link> links) {
        GraphAdjacency adjacency = GraphAdjacency.of(links, Link::fromId, Link::toId);
        return new LayoutGraph() {
            public int rootId() { return root; }
            public Map<Integer, ? extends Node> nodes() { return nodes; }
            public List<? extends Link> links() { return links; }
            public GraphAdjacency adjacency() { return adjacency; }
        };
    }
    default Set<Integer> upstream(int id) { return adjacency().upstream(id); }
    default Set<Integer> downstream(int id) { return adjacency().downstream(id); }
    default boolean isExternalInput(int id) { return false; }
    default boolean isBoundaryOutput(int id) { return false; }
}
