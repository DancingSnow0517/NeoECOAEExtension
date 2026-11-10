package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SolveWorkspace {
    // Bound retained amounts, including recipes that are tried only once. This is local to one solveGoals.
    private static final int MAX_CACHED_AMOUNTS = 65_536;
    private final PlannerInventorySnapshot inventory;
    private final Map<AEKey, Integer> candidateChoice;
    private Map<CompiledPattern, Expansion> expansions;
    private int cachedAmounts;

    public SolveWorkspace(KeyCounter inventory, Map<AEKey, Integer> candidateChoice) {
        this(PlannerInventorySnapshot.of(inventory), candidateChoice);
    }

    public SolveWorkspace(PlannerInventorySnapshot inventory, Map<AEKey, Integer> candidateChoice) {
        this.inventory = inventory;
        this.candidateChoice = candidateChoice;
    }
    PlannerInventorySnapshot inventory() { return inventory; }
    Map<AEKey, Integer> candidateChoice() { return candidateChoice; }

    void enableExpansionCache() {
        if (expansions == null) expansions = new IdentityHashMap<>();
    }

    /** Only pure recipe arithmetic is retained; inventory, credit and provenance are never cached. */
    Expansion cachedExpansion(CompiledPattern pattern, PlannerAmount times, AcyclicSolveMetrics metrics) {
        if (expansions == null) return null;
        Expansion previous = expansions.get(pattern);
        if (previous != null && previous.times.equals(times)) {
            if (metrics != null) metrics.expansionCacheHits++;
            return previous;
        }
        if (metrics != null) {
            metrics.expansionCacheMisses++;
            if (previous != null) metrics.expansionInvalidations++;
        }
        long size = 1L + pattern.inputs().size() + pattern.outputs().size();
        if (previous == null && size > MAX_CACHED_AMOUNTS - cachedAmounts) {
            if (metrics != null) metrics.expansionCacheBypasses++;
            return null;
        }
        PlannerAmount[] inputs = new PlannerAmount[pattern.inputs().size()];
        for (int slot = 0; slot < inputs.length; slot++) {
            CompiledInput input = pattern.inputs().get(slot);
            if (!pattern.specialAnalysis().excludesFromCycleGraph(input)) inputs[slot] = inputAmount(input, times);
        }
        Expansion expansion = new Expansion(times, Collections.unmodifiableMap(outputAmounts(pattern, times)), inputs);
        if (previous == null) cachedAmounts += (int) size;
        expansions.put(pattern, expansion);
        return expansion;
    }

    static Map<AEKey, PlannerAmount> outputAmounts(CompiledPattern pattern, PlannerAmount times) {
        Map<AEKey, PlannerAmount> produced = new LinkedHashMap<>();
        for (var output : pattern.outputs()) {
            PlannerAmount total = PlannerAmount.of(output.amount()).multiply(times);
            produced.put(output.what(), produced.getOrDefault(output.what(), PlannerAmount.ZERO).add(total));
        }
        return produced;
    }

    static PlannerAmount inputAmount(CompiledInput input, PlannerAmount times) {
        return input.reusable() ? input.amountPerPattern() : input.amountPerPattern().multiply(times);
    }

    static final class Expansion {
        private final PlannerAmount times;
        private final Map<AEKey, PlannerAmount> outputs;
        private final PlannerAmount[] inputs;

        private Expansion(PlannerAmount times, Map<AEKey, PlannerAmount> outputs, PlannerAmount[] inputs) {
            this.times = times;
            this.outputs = outputs;
            this.inputs = inputs;
        }

        Map<AEKey, PlannerAmount> outputs() { return outputs; }
        PlannerAmount inputAmount(int slot) { return inputs[slot]; }
    }
}
