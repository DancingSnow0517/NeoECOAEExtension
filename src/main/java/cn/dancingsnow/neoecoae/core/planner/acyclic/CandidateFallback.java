package cn.dancingsnow.neoecoae.core.planner.acyclic;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/** Advances the nearest producer that can explain a failed leaf, without global route enumeration. */
public final class CandidateFallback {
    private CandidateFallback() {}

    public static <K> boolean advance(Iterable<K> missing, Iterable<K> unsupported,
            Map<K, Set<K>> parents, ToIntFunction<K> candidateCount, Map<K, Integer> choices) {
        ArrayDeque<K> queue = new ArrayDeque<>();
        Set<K> seen = new HashSet<>();
        for (K key : missing) { queue.add(key); seen.add(key); }
        for (K key : unsupported) if (seen.add(key)) queue.add(key);
        while (!queue.isEmpty()) {
            K failedKey = queue.removeFirst();
            for (K parent : parents.getOrDefault(failedKey, Set.of())) {
                int current = choices.getOrDefault(parent, 0);
                if (current + 1 < candidateCount.applyAsInt(parent)) {
                    choices.put(parent, current + 1);
                    return true;
                }
                if (seen.add(parent)) queue.addLast(parent);
            }
        }
        return false;
    }
}
