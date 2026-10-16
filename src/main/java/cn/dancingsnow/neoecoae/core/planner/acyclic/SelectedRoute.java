package cn.dancingsnow.neoecoae.core.planner.acyclic;

import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import cn.dancingsnow.neoecoae.core.graph.TopoSort;
import cn.dancingsnow.neoecoae.core.planner.PatternView;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/** Rebuilds goal-first dependencies after a candidate change; selected feedback is reported as a cycle. */
public final class SelectedRoute {
    private SelectedRoute() {}

    public static <K, P extends PatternView<K>> List<K> order(Set<K> allowed, Set<K> goals, Set<K> stockLeaves,
            Function<K, P> selection, Predicate<P> deferred, BiPredicate<P, Integer> excludedInput,
            Cancellation cancellation) throws InterruptedException {
        Map<K, Set<K>> outgoing = new LinkedHashMap<>();
        ArrayDeque<K> discover = new ArrayDeque<>();
        Set<K> reachable = new LinkedHashSet<>();
        for (K goal : goals) { discover.add(goal); reachable.add(goal); }
        while (!discover.isEmpty()) {
            cancellation.checkpoint();
            K key = discover.removeFirst();
            if (stockLeaves.contains(key)) continue;
            P selected = selection.apply(key);
            if (selected == null || deferred.test(selected)) continue;
            Set<K> dependencies = outgoing.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            for (int slot = 0; slot < selected.inputCount(); slot++) {
                if (excludedInput.test(selected, slot)) continue;
                K dependency = selected.inputKey(slot);
                if (!allowed.contains(dependency) || !dependencies.add(dependency)) continue;
                if (reachable.add(dependency)) discover.addLast(dependency);
            }
        }
        var sorted = TopoSort.kahnFifo(reachable, key -> outgoing.getOrDefault(key, Set.of()), cancellation::checkpoint);
        return sorted.acyclic() ? sorted.order() : null;
    }
}
