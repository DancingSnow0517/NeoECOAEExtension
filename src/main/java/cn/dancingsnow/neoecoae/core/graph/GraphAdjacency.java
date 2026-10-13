package cn.dancingsnow.neoecoae.core.graph;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Immutable directed adjacency over integer node ids, built once from an edge list. Each neighbour set keeps the
 * first-occurrence order of its edges, and repeated (from, to) pairs collapse to a single neighbour.
 */
public final class GraphAdjacency {
    private final Map<Integer, Set<Integer>> upstream;
    private final Map<Integer, Set<Integer>> downstream;

    private GraphAdjacency(Map<Integer, Set<Integer>> upstream, Map<Integer, Set<Integer>> downstream) {
        this.upstream = upstream;
        this.downstream = downstream;
    }

    public static <E> GraphAdjacency of(Iterable<? extends E> edges, ToIntFunction<? super E> from,
            ToIntFunction<? super E> to) {
        Map<Integer, Set<Integer>> up = new HashMap<>();
        Map<Integer, Set<Integer>> down = new HashMap<>();
        for (E edge : edges) {
            int source = from.applyAsInt(edge);
            int target = to.applyAsInt(edge);
            down.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(target);
            up.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(source);
        }
        return new GraphAdjacency(freeze(up), freeze(down));
    }

    public Set<Integer> upstream(int id) {
        return upstream.getOrDefault(id, Set.of());
    }

    public Set<Integer> downstream(int id) {
        return downstream.getOrDefault(id, Set.of());
    }

    /** Upstream neighbours first, then downstream; a node linked in both directions appears once. */
    public Set<Integer> neighborhood(int id) {
        Set<Integer> result = new LinkedHashSet<>(upstream(id));
        result.addAll(downstream(id));
        return result;
    }

    private static Map<Integer, Set<Integer>> freeze(Map<Integer, Set<Integer>> built) {
        Map<Integer, Set<Integer>> frozen = new HashMap<>();
        built.forEach((id, neighbours) -> frozen.put(id, Collections.unmodifiableSet(neighbours)));
        return Collections.unmodifiableMap(frozen);
    }
}
