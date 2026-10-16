package cn.dancingsnow.neoecoae.core.planner.graph;

import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import java.util.*;
import java.util.function.*;

/** Compiles the producer closure without knowing a platform's keys, recipe objects, or inventory API. */
public final class DependencyClosure {
    private DependencyClosure() {}
    public static <K, P, N, E extends DependencyGraph.Edge<K>> DependencyGraph<K, N, E> build(K goal,
            int expectedEdges, Function<K, List<P>> producers, BiFunction<K, List<P>, N> nodeFactory,
            BiFunction<K, P, List<E>> dependencyEdges, Cancellation cancellation) throws InterruptedException {
        Map<K, N> nodes = new LinkedHashMap<>();
        List<E> edges = new ArrayList<>(expectedEdges);
        ArrayDeque<K> work = new ArrayDeque<>();
        Set<K> reachable = new LinkedHashSet<>();
        work.add(goal); reachable.add(goal);
        while (!work.isEmpty()) {
            cancellation.checkpoint();
            K key = work.removeFirst();
            List<P> patterns = producers.apply(key);
            nodes.put(key, nodeFactory.apply(key, patterns));
            for (P pattern : patterns) {
                cancellation.checkpoint();
                for (E edge : dependencyEdges.apply(key, pattern)) {
                    edges.add(edge);
                    if (reachable.add(edge.producer())) work.addLast(edge.producer());
                    if (reachable.add(edge.requiredInput())) work.addLast(edge.requiredInput());
                    nodes.computeIfAbsent(edge.producer(), k -> nodeFactory.apply(k, producers.apply(k)));
                    nodes.computeIfAbsent(edge.requiredInput(), k -> nodeFactory.apply(k, producers.apply(k)));
                }
            }
        }
        return new DependencyGraph<>(goal, nodes, edges);
    }
}
