package cn.dancingsnow.neoecoae.impl.crafting.planner.compile;

import appeng.api.stacks.AEKey;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CompiledNetwork(
    AEKey goal,
    Map<AEKey, List<CompiledPattern>> producers,
    Set<AEKey> emittable,
    int reachablePatternCount,
    int edgeCount
) {
    public CompiledNetwork {
        producers = Map.copyOf(producers);
        emittable = Set.copyOf(emittable);
    }

    public Set<AEKey> keys() { return producers.keySet(); }
    public List<CompiledPattern> producersOf(AEKey key) { return producers.getOrDefault(key, List.of()); }
}
