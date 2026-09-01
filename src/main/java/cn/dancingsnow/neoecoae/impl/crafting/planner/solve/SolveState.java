package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveResult;

public final class SolveState {
    final PlannerCounter stored = new PlannerCounter();
    final PlannerCounter crafted = new PlannerCounter();
    final PlannerCounter used = new PlannerCounter();
    final PlannerCounter emitted = new PlannerCounter();
    final PlannerCounter missing = new PlannerCounter();
    final Map<AEKey, PlannerAmount> demand = new LinkedHashMap<>();
    final Map<IPatternDetails, PlannerAmount> patternTimes = new LinkedHashMap<>();
    final Map<AEKey, IPatternDetails> demandProducers = new HashMap<>();
    final Map<AEKey, CompiledPattern> selected = new HashMap<>();
    final Map<AEKey, Set<AEKey>> parents = new HashMap<>();
    final Set<AEKey> unsupported = new LinkedHashSet<>();
    PlannerAmount bytes = PlannerAmount.ZERO;

    SolveState(KeyCounter inventory) {
        for (var entry : inventory) if (entry.getLongValue() > 0) stored.add(entry.getKey(), entry.getLongValue());
    }

    /** AE2-facing view. Callers must only use this after execution representability was checked. */
    public KeyCounter usedItems() { return used.toKeyCounterExact("used items"); }
    /** AE2-facing view. Callers must only use this after execution representability was checked. */
    public KeyCounter emittedItems() { return emitted.toKeyCounterExact("emitted items"); }
    /** AE2-facing view. Callers must only use this after execution representability was checked. */
    public KeyCounter missingItems() { return missing.toKeyCounterExact("missing items"); }
    /** Exact planner view of the used inventory. */
    public Map<AEKey, PlannerAmount> usedAmounts() { return used.asMap(); }
    /** Exact planner view of emitted material. */
    public Map<AEKey, PlannerAmount> emittedAmounts() { return emitted.asMap(); }
    /** Exact planner view of missing material. */
    public Map<AEKey, PlannerAmount> missingAmounts() { return missing.asMap(); }
    /** Exact planner view of pattern firing counts. */
    public Map<IPatternDetails, PlannerAmount> plannerPatternTimes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(patternTimes));
    }
    /** Exact planner view of material demand. */
    public Map<AEKey, PlannerAmount> plannerDemands() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(demand));
    }
    /** AE2-facing view retained for existing consumers. */
    public Map<IPatternDetails, Long> patternTimes() { return exactLongMap(patternTimes, "pattern times"); }
    /** AE2-facing view retained for existing consumers. */
    public Map<AEKey, Long> demands() { return exactLongMap(demand, "demand"); }
    /** AE2-facing view retained for existing consumers. */
    public long demandFor(AEKey key) { return demandAmountFor(key).longValueExact(); }
    public PlannerAmount demandAmountFor(AEKey key) { return demand.getOrDefault(key, PlannerAmount.ZERO); }
    public boolean hasPlannedCrafting() { return !patternTimes.isEmpty(); }
    /** AE2-facing storage size; exact only after representability has been established. */
    public long bytes() { return bytes.longValueExact(); }
    public PlannerAmount plannerBytes() { return bytes; }

    /**
     * Finds quantities that are eventually passed through AE2's long-valued CraftingPlan fields.
     * Intermediate demand is intentionally not included: it may exceed long and still collapse to a
     * representable firing count/output plan.
     */
    public List<ExecutionAmountIssue> executionAmountIssues() {
        List<ExecutionAmountIssue> issues = new ArrayList<>();
        collectExecutionIssues(issues, used, "used inventory");
        collectExecutionIssues(issues, emitted, "emitted items");
        collectExecutionIssues(issues, missing, "missing items");
        for (var entry : patternTimes.entrySet()) {
            if (!entry.getValue().fitsLong()) {
                issues.add(new ExecutionAmountIssue(null, entry.getKey(), entry.getValue(), "pattern firing count"));
            }
        }
        if (!bytes.fitsLong()) issues.add(new ExecutionAmountIssue(null, null, bytes, "plan bytes"));
        return List.copyOf(issues);
    }

    public record ExecutionAmountIssue(AEKey key, IPatternDetails producer, PlannerAmount amount, String stage) {}

    /** Exposes a disabled cycle as a normal AE2 missing entry without inventing missing internal SCC members. */
    void markCycleMissing(Map<AEKey, Long> requiredOutputs) {
        for (var entry : requiredOutputs.entrySet()) {
            long amount = entry.getValue();
            if (amount > 0) missing.set(entry.getKey(), missing.get(entry.getKey()).max(PlannerAmount.of(amount)));
        }
    }

    /**
     * Records material deficits discovered while solving a cycle boundary. These are leaf inputs from the
     * external DAG, so they must be exposed through AE2's normal missing-items counter even though the cycle
     * transaction itself is not committed.
     */
    void markMissing(Map<AEKey, Long> deficits) {
        for (var entry : deficits.entrySet()) {
            long amount = entry.getValue() == null ? 0L : entry.getValue();
            if (amount > 0L) {
                // External-demand entries are independent deficits; accumulate them when multiple cycle
                // boundaries require the same leaf material.
                missing.add(entry.getKey(), PlannerAmount.of(amount));
            }
        }
    }

    /** Commits external DAG work and its cycle as one copy-and-replace transaction. */
    boolean applyCycleTransaction(CycleSolveResult cycle, Map<AEKey, Long> ownedCycleReservations,
            Map<AEKey, Long> additionalCycleReservations, KeyCounter inventory,
            KeyCounter directExternalReservations, List<SolveState> externalStates) {
        if (cycle == null || cycle.status() != cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus.SUCCESS
                || !cycle.seedShortfall().isEmpty()) return false;
        SolveState candidate = copy();
        try {
            for (var entry : cycle.requiredSeed().entrySet()) {
                if (entry.getValue() < 0
                        || ownedCycleReservations.getOrDefault(entry.getKey(), 0L) < entry.getValue()) return false;
            }
            for (var entry : additionalCycleReservations.entrySet()) {
                if (entry.getValue() < 0) return false;
                candidate.used.add(entry.getKey(), entry.getValue());
            }
            for (var entry : directExternalReservations) candidate.used.add(entry.getKey(), entry.getLongValue());
            for (SolveState external : externalStates) candidate.mergeExternal(external);
            for (var entry : cycle.patternTimes().entrySet()) {
                if (entry.getValue() < 0) return false;
                candidate.patternTimes.merge(entry.getKey(), PlannerAmount.of(entry.getValue()), PlannerAmount::add);
            }
            for (var entry : candidate.used) {
                if (entry.getValue().compareTo(PlannerAmount.of(inventory.get(entry.getKey()))) > 0) return false;
            }
            replaceWith(candidate);
            return true;
        } catch (ArithmeticException | IllegalArgumentException ignored) {
            return false;
        }
    }

    private SolveState copy() {
        SolveState copy = new SolveState(new KeyCounter());
        copy.stored.replaceFrom(stored); copy.crafted.replaceFrom(crafted);
        copy.used.replaceFrom(used); copy.emitted.replaceFrom(emitted);
        copy.missing.replaceFrom(missing);
        copy.demand.putAll(demand); copy.patternTimes.putAll(patternTimes); copy.demandProducers.putAll(demandProducers);
        copy.selected.putAll(selected);
        parents.forEach((key, value) -> copy.parents.put(key, new LinkedHashSet<>(value)));
        copy.unsupported.addAll(unsupported); copy.bytes = bytes;
        return copy;
    }

    private void mergeExternal(SolveState external) {
        if (!external.missing.isEmpty() || !external.unsupported.isEmpty()) throw new IllegalArgumentException();
        for (var entry : external.used) used.add(entry.getKey(), entry.getValue());
        for (var entry : external.emitted) emitted.add(entry.getKey(), entry.getValue());
        external.demand.forEach((key, value) -> demand.merge(key, value, PlannerAmount::add));
        external.patternTimes.forEach((key, value) -> patternTimes.merge(key, value, PlannerAmount::add));
        demandProducers.putAll(external.demandProducers);
        selected.putAll(external.selected);
        external.parents.forEach((key, value) -> parents.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).addAll(value));
        bytes = bytes.add(external.bytes);
    }

    private void replaceWith(SolveState source) {
        replaceCounter(stored, source.stored); replaceCounter(crafted, source.crafted);
        replaceCounter(used, source.used); replaceCounter(emitted, source.emitted); replaceCounter(missing, source.missing);
        demand.clear(); demand.putAll(source.demand); patternTimes.clear(); patternTimes.putAll(source.patternTimes);
        demandProducers.clear(); demandProducers.putAll(source.demandProducers);
        selected.clear(); selected.putAll(source.selected); parents.clear();
        source.parents.forEach((key, value) -> parents.put(key, new LinkedHashSet<>(value)));
        unsupported.clear(); unsupported.addAll(source.unsupported); bytes = source.bytes;
    }

    private void collectExecutionIssues(List<ExecutionAmountIssue> issues, PlannerCounter counter, String stage) {
        for (var entry : counter) {
            if (!entry.getValue().fitsLong()) issues.add(new ExecutionAmountIssue(entry.getKey(),
                demandProducers.get(entry.getKey()), entry.getValue(), stage));
        }
    }

    private static <K> Map<K, Long> exactLongMap(Map<K, PlannerAmount> source, String stage) {
        Map<K, Long> result = new LinkedHashMap<>();
        for (var entry : source.entrySet()) {
            if (!entry.getValue().fitsLong()) {
                throw new ArithmeticException(stage + " exceeds AE2 long range: " + entry.getValue());
            }
            result.put(entry.getKey(), entry.getValue().longValueExact());
        }
        return Map.copyOf(result);
    }

    private static void replaceCounter(PlannerCounter target, PlannerCounter source) {
        target.replaceFrom(source);
    }
}
