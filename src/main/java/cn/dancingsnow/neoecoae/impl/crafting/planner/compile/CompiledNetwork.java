package cn.dancingsnow.neoecoae.impl.crafting.planner.compile;

import appeng.api.stacks.AEKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

public record CompiledNetwork(
    AEKey goal,
    Map<AEKey, List<CompiledPattern>> producers,
    Set<AEKey> emittable,
    int reachablePatternCount,
    int edgeCount,
    Map<AEKey, List<CompiledPattern>> fastProducers,
    boolean multiplePaths
) {
    public CompiledNetwork(AEKey goal, Map<AEKey, List<CompiledPattern>> producers,
            Set<AEKey> emittable, int reachablePatternCount, int edgeCount) {
        this(goal, producers, emittable, reachablePatternCount, edgeCount, Map.of(), false);
    }

    public CompiledNetwork {
        Map<AEKey, List<CompiledPattern>> all = new LinkedHashMap<>();
        Map<AEKey, List<CompiledPattern>> fast = new LinkedHashMap<>();
        multiplePaths = false;
        for (var entry : producers.entrySet()) {
            List<CompiledPattern> patterns = List.copyOf(entry.getValue());
            all.put(entry.getKey(), patterns);
            fast.put(entry.getKey(), patterns.stream().filter(CompiledPattern::fastSupported).toList());
            multiplePaths |= patterns.size() > 1;
        }
        producers = Map.copyOf(all);
        fastProducers = Map.copyOf(fast);
        emittable = Set.copyOf(emittable);
    }

    public Set<AEKey> keys() { return producers.keySet(); }
    public List<CompiledPattern> producersOf(AEKey key) { return producers.getOrDefault(key, List.of()); }
    public List<CompiledPattern> fastProducersOf(AEKey key) { return fastProducers.getOrDefault(key, List.of()); }
}
