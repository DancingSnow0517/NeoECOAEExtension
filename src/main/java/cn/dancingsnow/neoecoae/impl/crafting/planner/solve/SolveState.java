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
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveResult;

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

    /** Atomically merges a verified cycle answer into this state. */
    public boolean applyCycleSolution(CycleSolveResult result, KeyCounter inventory) {
        if (result == null || result.status() != cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus.SUCCESS
                || !result.seedShortfall().isEmpty()) return false;
        try {
            // Validate first, then mutate, so a rejected answer cannot partially commit.
            for (var e : result.deliverableOutputs().entrySet())
                if (e.getValue() < 0 || e.getValue() < 0L) return false;
            for (var e : result.requiredSeed().entrySet()) {
                long already = used.get(e.getKey());
                long available = Math.max(0L, inventory.get(e.getKey()) - already);
                if (e.getValue() < 0 || e.getValue() > available) return false;
            }
            for (var e : result.positiveExternalDemand().entrySet()) {
                long already = used.get(e.getKey());
                long available = Math.max(0L, inventory.get(e.getKey()) - already);
                if (e.getValue() > available) return false;
            }
            for (var e : result.patternTimes().entrySet()) if (e.getValue() < 0) return false;
            Map<IPatternDetails, Long> merged = new LinkedHashMap<>(patternTimes);
            for (var e : result.patternTimes().entrySet()) merged.merge(e.getKey(), e.getValue(), Math::addExact);
            for (var e : result.requiredSeed().entrySet()) if (e.getValue() > 0)
                used.add(e.getKey(), e.getValue());
            for (var e : result.positiveExternalDemand().entrySet()) if (e.getValue() > 0)
                used.add(e.getKey(), e.getValue());
            for (var e : result.patternTimes().entrySet()) {
                // applied below from the prevalidated merged map
            }
            patternTimes.clear();
            patternTimes.putAll(merged);
            return true;
        } catch (ArithmeticException ex) {
            return false;
        }
    }
}
