package cn.dancingsnow.neoecoae.core.graph.presentation;

import cn.dancingsnow.neoecoae.core.graph.GraphAdjacency;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

/** Iterative graph clipping shared by client projections; it never changes recipe quantities. */
public final class GraphVisibility {
    private GraphVisibility() {}

    public static Set<Integer> withinDepth(LayoutGraph graph, int focus, int depth, Set<Integer> collapsed) {
        Set<Integer> visible = new LinkedHashSet<>();
        Set<Integer> frontier = Set.of(focus);
        for (int level = 0; level <= depth && !frontier.isEmpty(); level++) {
            visible.addAll(frontier);
            Set<Integer> next = new LinkedHashSet<>();
            for (int id : frontier) {
                if (collapsed.contains(id)) continue;
                next.addAll(graph.upstream(id));
                next.addAll(graph.downstream(id));
            }
            next.removeAll(visible);
            frontier = next;
        }
        return visible;
    }

    /** All missing nodes reachable by active dependency edges, together with their paths back to the root. */
    public static Set<Integer> missingPaths(LayoutGraph graph, GraphAdjacency active) {
        Set<Integer> reachable = new LinkedHashSet<>();
        var frontier = new ArrayDeque<Integer>();
        frontier.add(graph.rootId());
        while (!frontier.isEmpty()) {
            int id = frontier.removeFirst();
            if (!reachable.add(id)) continue;
            for (int child : active.downstream(id)) frontier.addLast(child);
        }
        Set<Integer> visible = new LinkedHashSet<>();
        var missing = new ArrayDeque<Integer>();
        for (int id : reachable) {
            var node = graph.nodes().get(id);
            if (node != null && node.missing()) {
                visible.add(id);
                missing.addLast(id);
            }
        }
        while (!missing.isEmpty()) {
            for (int parent : active.upstream(missing.removeFirst()))
                if (reachable.contains(parent) && visible.add(parent)) missing.addLast(parent);
        }
        visible.add(graph.rootId());
        return visible;
    }
}
