package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import java.util.Collections;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SolveWorkspace {
    // Bound retained amounts, including recipes that are tried only once. This is local to one solveGoals.
    private static final int MAX_CACHED_AMOUNTS = 65_536;
    private final PlannerInventorySnapshot inventory;
    private final Map<AEKey, Integer> candidateChoice;
    private Map<CompiledPattern, Expansion> expansions;
    private int cachedAmounts;
    // At most one full checkpoint is created, after a real failure, and only for substantial prefixes.
    static final int MIN_CHECKPOINT_EXPANSIONS = 64;
    static final int MAX_CHECKPOINT_STEPS = 8_192;
    static final int MAX_CHECKPOINT_INPUTS = 65_536;
    private AEKey checkpointBefore;
    private boolean checkpointConsidered;
    private boolean checkpointRetryPossible;
    RetryCheckpoint checkpoint;

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

    void prepareCheckpoint(AEKey changedKey, boolean retryPossible) {
        if (!checkpointConsidered) {
            checkpointBefore = changedKey;
            checkpointRetryPossible = retryPossible;
        }
    }

    boolean needsCheckpointTarget() { return !checkpointConsidered; }

    boolean checkpointRetryPossible() { return checkpointRetryPossible; }

    AEKey takeCheckpointTarget() {
        AEKey target = checkpointBefore;
        if (target != null) checkpointConsidered = true;
        checkpointBefore = null;
        return target;
    }

    /**
     * A queue prefix is its compact restore state: initial goals, identical polls, and actual input adds
     * reconstruct queued/ready and dynamically assigned ranks under the new route. The material snapshot
     * retains UUIDs, supplier order, unused credits and pending allocations via SolveState.copy().
     * Snapshots are taken between complete material steps. Special resolution has unwound; its reusable
     * reservations are the only working state outside SolveState. Deferred cycle work remains a boundary.
     */
    static final class RetryCheckpoint {
        final SolveState state;
        final List<AEKey> polled;
        final BitSet expanded;
        final Map<AEKey, CompiledPattern> selected;
        final Set<AEKey> unsettled;
        final int routeSize;
        final long materialVisits;
        final long inputExpansions;
        final Map<AEKey, PlannerAmount> reusableStock;

        RetryCheckpoint(SolveState state, List<AEKey> polled, BitSet expanded,
                Map<AEKey, CompiledPattern> selected, Set<AEKey> unsettled, int routeSize,
                long materialVisits, long inputExpansions, SpecialPatternResolver resolver) {
            this.state = state.copy();
            this.polled = List.copyOf(polled);
            this.expanded = expanded;
            this.selected = selected;
            this.unsettled = new LinkedHashSet<>(unsettled);
            this.routeSize = routeSize;
            this.materialVisits = materialVisits;
            this.inputExpansions = inputExpansions;
            this.reusableStock = resolver.copyReservations();
        }
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
