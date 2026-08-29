package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SolveState {
    final KeyCounter stored = new KeyCounter();
    final KeyCounter crafted = new KeyCounter();
    final KeyCounter used = new KeyCounter();
    final KeyCounter emitted = new KeyCounter();
    final KeyCounter missing = new KeyCounter();
    final Map<AEKey, Long> demand = new LinkedHashMap<>();
    final Map<IPatternDetails, Long> patternTimes = new LinkedHashMap<>();
    final Map<AEKey, CompiledPattern> selected = new HashMap<>();
    final Map<AEKey, Set<AEKey>> parents = new HashMap<>();
    final Set<AEKey> unsupported = new LinkedHashSet<>();
    long bytes;

    SolveState(KeyCounter inventory) {
        for (var entry : inventory) if (entry.getLongValue() > 0) stored.add(entry.getKey(), entry.getLongValue());
    }

    public KeyCounter usedItems() { return used; }
    public KeyCounter emittedItems() { return emitted; }
    public KeyCounter missingItems() { return missing; }
    public Map<IPatternDetails, Long> patternTimes() { return Map.copyOf(patternTimes); }
    public Map<AEKey, Long> demands() { return Map.copyOf(demand); }
    public long demandFor(AEKey key) { return demand.getOrDefault(key, 0L); }
    public boolean hasPlannedCrafting() { return !patternTimes.isEmpty(); }
    public long bytes() { return bytes; }
}
