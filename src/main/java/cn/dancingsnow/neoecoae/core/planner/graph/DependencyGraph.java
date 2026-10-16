package cn.dancingsnow.neoecoae.core.planner.graph;

import java.util.*;

/** One structural graph shared by platform adapters, route selection and SCC analysis. */
public class DependencyGraph<K, N, E extends DependencyGraph.Edge<K>> {
    public interface Edge<K> { K producer(); K requiredInput(); }
    private final K goal;
    private final Map<K, N> nodes;
    private final List<E> edges;
    private final Map<K, List<E>> outgoing;
    public DependencyGraph(K goal, Map<K, N> nodes, List<E> edges) {
        this.goal = goal; this.nodes = nodes; this.edges = edges;
        Map<K, List<E>> byProducer = new LinkedHashMap<>();
        for (K key : nodes.keySet()) byProducer.put(key, new ArrayList<>());
        for (E edge : edges) byProducer.computeIfAbsent(edge.producer(), ignored -> new ArrayList<>()).add(edge);
        this.outgoing = byProducer;
    }
    public K goal() { return goal; }
    public Map<K, N> nodes() { return nodes; }
    public List<E> edges() { return edges; }
    public List<E> outgoing(K key) { return outgoing.getOrDefault(key, List.of()); }
}
