package cn.dancingsnow.neoecoae.core.planner;

import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Candidate-only startup DAG: an earliest supply route cannot require a later return from the same loop. */
public final class StartupRouteSearch {
    private StartupRouteSearch() {}

    public static <K, P extends PatternView<K>> Map<K, Integer> ranks(
            Collection<? extends List<P>> producers, Iterable<K> initialKeys, Predicate<P> safe,
            Cancellation cancellation) throws InterruptedException {
        Map<K, Integer> ranks = new LinkedHashMap<>();
        for (K key : initialKeys) ranks.put(key, 0);
        boolean changed;
        do {
            changed = false;
            for (var views : producers) for (P pattern : views) {
                cancellation.checkpoint();
                if (!safe.test(pattern)) continue;
                int inputRank = 0;
                boolean available = true;
                for (int i = 0; i < pattern.inputCount(); i++) {
                    Integer rank = ranks.get(pattern.inputKey(i));
                    if (rank == null) { available = false; break; }
                    inputRank = Math.max(inputRank, rank);
                }
                if (!available) continue;
                int rank = inputRank + 1;
                for (int i = 0; i < pattern.outputCount(); i++) {
                    K output = pattern.outputKey(i);
                    if (!ranks.containsKey(output) || ranks.get(output) > rank) {
                        ranks.put(output, rank);
                        changed = true;
                    }
                }
            }
        } while (changed);
        return ranks;
    }

    /** Retains input dependencies with strictly lower ranks; output advertisement remains the caller's policy. */
    public static <K, P extends PatternView<K>> Map<K, List<P>> earlierRoutes(Collection<K> goals,
            Function<K, List<P>> producers, Map<K, Integer> ranks, Predicate<P> safe,
            Cancellation cancellation) throws InterruptedException {
        Map<K, List<P>> selected = new LinkedHashMap<>();
        var pending = new ArrayDeque<>(goals);
        Set<K> visited = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            cancellation.checkpoint();
            K key = pending.removeFirst();
            if (!visited.add(key)) continue;
            List<P> views = producers.apply(key).stream()
                .filter(pattern -> safe.test(pattern) && earlierInputs(pattern, ranks, ranks.getOrDefault(key, 0)))
                .toList();
            selected.put(key, views);
            for (P pattern : views) {
                // Queue each distinct input once per view, matching aggregation of duplicate ingredient slots.
                Set<K> inputs = new LinkedHashSet<>();
                for (int i = 0; i < pattern.inputCount(); i++) inputs.add(pattern.inputKey(i));
                pending.addAll(inputs);
            }
        }
        return selected;
    }

    private static <K> boolean earlierInputs(PatternView<K> pattern, Map<K, Integer> ranks, int outputRank) {
        for (int i = 0; i < pattern.inputCount(); i++) {
            Integer rank = ranks.get(pattern.inputKey(i));
            if (rank == null || rank >= outputRank) return false;
        }
        return true;
    }
}
