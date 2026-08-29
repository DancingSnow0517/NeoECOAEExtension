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
import java.util.List;

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

    /** Exposes a disabled cycle as a normal AE2 missing entry without inventing missing internal SCC members. */
    void markCycleMissing(Map<AEKey, Long> requiredOutputs) {
        for (var entry : requiredOutputs.entrySet()) {
            long amount = entry.getValue();
            if (amount > 0) missing.set(entry.getKey(), Math.max(missing.get(entry.getKey()), amount));
        }
    }

    /** Commits external DAG work and its cycle as one copy-and-replace transaction. */
    boolean applyCycleTransaction(CycleSolveResult cycle, KeyCounter inventory,
            KeyCounter directExternalReservations, List<SolveState> externalStates) {
        if (cycle == null || cycle.status() != cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus.SUCCESS
                || !cycle.seedShortfall().isEmpty()) return false;
        SolveState candidate = copy();
        try {
            for (var entry : cycle.requiredSeed().entrySet()) {
                if (entry.getValue() < 0) return false;
                addCounter(candidate.used, entry.getKey(), entry.getValue());
            }
            for (var entry : directExternalReservations) addCounter(candidate.used, entry.getKey(), entry.getLongValue());
            for (SolveState external : externalStates) candidate.mergeExternal(external);
            for (var entry : cycle.patternTimes().entrySet()) {
                if (entry.getValue() < 0) return false;
                candidate.patternTimes.merge(entry.getKey(), entry.getValue(), Math::addExact);
            }
            for (var entry : candidate.used) if (entry.getLongValue() > inventory.get(entry.getKey())) return false;
            replaceWith(candidate);
            return true;
        } catch (ArithmeticException | IllegalArgumentException ignored) {
            return false;
        }
    }

    private SolveState copy() {
        SolveState copy = new SolveState(new KeyCounter());
        copyCounter(stored, copy.stored); copyCounter(crafted, copy.crafted); copyCounter(used, copy.used);
        copyCounter(emitted, copy.emitted); copyCounter(missing, copy.missing);
        copy.demand.putAll(demand); copy.patternTimes.putAll(patternTimes); copy.selected.putAll(selected);
        parents.forEach((key, value) -> copy.parents.put(key, new LinkedHashSet<>(value)));
        copy.unsupported.addAll(unsupported); copy.bytes = bytes;
        return copy;
    }

    private void mergeExternal(SolveState external) {
        if (!external.missing.isEmpty() || !external.unsupported.isEmpty()) throw new IllegalArgumentException();
        for (var entry : external.used) addCounter(used, entry.getKey(), entry.getLongValue());
        for (var entry : external.emitted) addCounter(emitted, entry.getKey(), entry.getLongValue());
        external.demand.forEach((key, value) -> demand.merge(key, value, Math::addExact));
        external.patternTimes.forEach((key, value) -> patternTimes.merge(key, value, Math::addExact));
        selected.putAll(external.selected);
        external.parents.forEach((key, value) -> parents.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(value));
        bytes = Math.addExact(bytes, external.bytes);
    }

    private void replaceWith(SolveState source) {
        replaceCounter(stored, source.stored); replaceCounter(crafted, source.crafted);
        replaceCounter(used, source.used); replaceCounter(emitted, source.emitted); replaceCounter(missing, source.missing);
        demand.clear(); demand.putAll(source.demand); patternTimes.clear(); patternTimes.putAll(source.patternTimes);
        selected.clear(); selected.putAll(source.selected); parents.clear();
        source.parents.forEach((key, value) -> parents.put(key, new LinkedHashSet<>(value)));
        unsupported.clear(); unsupported.addAll(source.unsupported); bytes = source.bytes;
    }

    private static void addCounter(KeyCounter counter, AEKey key, long amount) {
        counter.set(key, Math.addExact(counter.get(key), amount));
    }
    private static void copyCounter(KeyCounter source, KeyCounter target) {
        for (var entry : source) target.set(entry.getKey(), entry.getLongValue());
    }
    private static void replaceCounter(KeyCounter target, KeyCounter source) {
        var keys = new java.util.ArrayList<AEKey>();
        for (var entry : target) keys.add(entry.getKey());
        for (AEKey key : keys) target.set(key, 0);
        copyCounter(source, target);
    }
}
