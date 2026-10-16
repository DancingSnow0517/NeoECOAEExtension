package cn.dancingsnow.neoecoae.core.planner;

import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import java.util.*;

/** Presence overapproximation: only a negative reachability answer proves impossibility. */
public final class RouteAvailability {
    private RouteAvailability() {}
    public static <K, P extends PatternView<K>> boolean goalUnreachable(K goal,
            Collection<P> patterns, Set<K> emittable, Iterable<K> stockKeys,
            java.util.function.ToLongFunction<K> stock, KeyOps<K> keyOps, Cancellation cancellation) throws InterruptedException {
        Set<K> available = new HashSet<>(emittable);
        for (K key : stockKeys) {
            cancellation.checkpoint();
            if (stock.applyAsLong(key) > 0L) available.add(key);
        }
        if (available.contains(goal)) return false;
        List<P> candidates = new ArrayList<>(new LinkedHashSet<>(patterns));
        int[] missing = new int[candidates.size()];
        Map<K, List<Integer>> waiting = new HashMap<>();
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int index = 0; index < candidates.size(); index++) {
            cancellation.checkpoint();
            P pattern = candidates.get(index);
            if (!pattern.fastSupported() || !pattern.completeForStaticPlanning() || pattern.special()) return false;
            Set<K> needed = new HashSet<>();
            for (int i = 0; i < pattern.inputCount(); i++) {
                if (!pattern.inputFastSupported(i) || pattern.inputIgnoresComponents(i)) return false;
                K key = pattern.inputKey(i);
                if (!available.contains(key)) needed.add(key);
            }
            missing[index] = needed.size();
            for (K key : needed) waiting.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
            if (needed.isEmpty()) ready.add(index);
        }
        while (!ready.isEmpty()) {
            cancellation.checkpoint();
            P pattern = candidates.get(ready.removeFirst());
            for (int i = 0; i < pattern.grossOutputCount(); i++) {
                K output = pattern.grossOutputKey(i);
                if (pattern.grossOutputAmount(i) <= 0 || !available.add(output)) continue;
                if (keyOps.matches(goal, output)) return false;
                for (int dependent : waiting.getOrDefault(output, List.of())) {
                    if (--missing[dependent] == 0) ready.addLast(dependent);
                }
            }
        }
        return true;
    }
}
