package cn.dancingsnow.neoecoae.core.planner.graph;

import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import cn.dancingsnow.neoecoae.core.graph.StronglyConnected;
import java.util.*;

/** SCC metadata retains the original edges and deterministic member ordering. */
public record DependencyScc<K, E>(int componentId, List<K> members, List<E> internalEdges, boolean cyclic) {
    public static <K, E extends DependencyGraph.Edge<K>> List<DependencyScc<K, E>> analyze(
            DependencyGraph<K, ?, E> graph, Cancellation cancellation) throws InterruptedException {
        List<List<K>> memberSets = StronglyConnected.components(graph.nodes().keySet(),
            node -> graph.outgoing(node).stream().map(DependencyGraph.Edge::requiredInput).toList(),
            cancellation::checkpoint);
        List<DependencyScc<K, E>> result = new ArrayList<>(memberSets.size());
        int id = 0;
        for (List<K> members : memberSets) {
            cancellation.checkpoint();
            Set<K> lookup = new LinkedHashSet<>(members);
            List<E> internal = new ArrayList<>();
            for (K member : members) for (E edge : graph.outgoing(member)) {
                if (lookup.contains(edge.requiredInput())) internal.add(edge);
            }
            boolean cyclic = members.size() > 1 || internal.stream()
                .anyMatch(edge -> edge.producer().equals(edge.requiredInput()));
            result.add(new DependencyScc<>(id++, members, internal, cyclic));
        }
        return result;
    }
}
