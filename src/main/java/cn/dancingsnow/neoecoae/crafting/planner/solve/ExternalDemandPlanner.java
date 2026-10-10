package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plans a solved cycle's boundary inputs, delegating selected cyclic suppliers to the component planner. */
final class ExternalDemandPlanner {
    record Outcome(CycleExternalDemandStatus status, KeyCounter directReservations,
            List<SolveState> states, Map<AEKey, Long> missingLeaves, Set<IPatternDetails> selectedPatterns,
            Map<AEKey, Long> delegatedCycleDemands, String diagnostic) {
        boolean solved() { return status == CycleExternalDemandStatus.SOLVED; }

        Outcome copy() {
            KeyCounter reservations = new KeyCounter();
            for (var entry : directReservations) reservations.add(entry.getKey(), entry.getLongValue());
            List<SolveState> copiedStates = new ArrayList<>(states.size());
            for (SolveState state : states) copiedStates.add(state.copy());
            return new Outcome(status, reservations, copiedStates, new LinkedHashMap<>(missingLeaves),
                new LinkedHashSet<>(selectedPatterns), new LinkedHashMap<>(delegatedCycleDemands), diagnostic);
        }
    }

    private final AcyclicCraftingSolver acyclicSolver;
    private final ActiveRouteSelector routeSelector = new ActiveRouteSelector();
    private final CraftingGraphBuilder graphBuilder = new CraftingGraphBuilder();
    /**
     * A single component plan can ask for the same external boundary several times while it checks startup
     * seeds, recovery witnesses and alternate routes. Keep this cache invocation-local: stock and pattern
     * structure are part of the key, so a later network calculation can never inherit an old proof.
     */
    private static final int MAX_DEMAND_CACHE_ENTRIES = 256;
    private CompiledNetwork cachedNetwork;
    private final Map<CacheKey, Outcome> demandCache = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, Outcome> eldest) {
            return size() > MAX_DEMAND_CACHE_ENTRIES;
        }
    };

    ExternalDemandPlanner(AcyclicCraftingSolver acyclicSolver) { this.acyclicSolver = acyclicSolver; }

    Outcome solve(CompiledNetwork network, CycleComponent cycle, CycleSolveResult cycleResult,
            KeyCounter inventory, SolveState base, Map<AEKey, Long> additionalCycleReservations,
            Set<AEKey> delegatedCycleInputs, ECOCancellation cancellation) throws InterruptedException {
        return solve(network, cycle, cycleResult, inventory, base, additionalCycleReservations,
            delegatedCycleInputs, false, cancellation);
    }

    Outcome solve(CompiledNetwork network, CycleComponent cycle, CycleSolveResult cycleResult,
            KeyCounter inventory, SolveState base, Map<AEKey, Long> additionalCycleReservations,
            Set<AEKey> delegatedCycleInputs, boolean ignorePatternSubstitutions,
            ECOCancellation cancellation) throws InterruptedException {
        return solveDemands(network, cycle, cycleResult.positiveExternalDemand(), inventory, base,
            additionalCycleReservations, delegatedCycleInputs, ignorePatternSubstitutions, cancellation);
    }

    Outcome solveDemands(CompiledNetwork network, CycleComponent cycle, Map<AEKey, Long> demands,
            KeyCounter inventory, SolveState base, Map<AEKey, Long> additionalCycleReservations,
            Set<AEKey> delegatedCycleInputs, ECOCancellation cancellation) throws InterruptedException {
        return solveDemands(network, cycle, demands, inventory, base, additionalCycleReservations,
            delegatedCycleInputs, false, cancellation);
    }

    Outcome solveDemands(CompiledNetwork network, CycleComponent cycle, Map<AEKey, Long> demands,
            KeyCounter inventory, SolveState base, Map<AEKey, Long> additionalCycleReservations,
            Set<AEKey> delegatedCycleInputs, boolean ignorePatternSubstitutions,
            ECOCancellation cancellation) throws InterruptedException {
        cancellation.checkpoint();
        // CompiledNetwork is immutable and shared by all route attempts in this invocation. Comparing its
        // identity both invalidates changed recipes and avoids hashing the complete recipe graph on each hit.
        if (cachedNetwork != network) {
            demandCache.clear();
            cachedNetwork = network;
        }
        KeyCounter available = remainingInventory(inventory, base);
        CacheKey cacheKey = new CacheKey(Set.copyOf(cycle.members()), cycle.patterns().stream()
            .map(pattern -> pattern.details()).collect(java.util.stream.Collectors.toSet()),
            demands.entrySet().stream().map(entry -> new BoundaryDemand(entry.getKey(), entry.getValue())).toList(),
            copyLongMap(available),
            Set.copyOf(base.stored.unboundedKeys()), copyLongMap(additionalCycleReservations),
            Set.copyOf(delegatedCycleInputs), ignorePatternSubstitutions);
        Outcome cached = demandCache.get(cacheKey);
        if (cached != null) return cached.copy();

        Outcome result = solveDemandsUncached(network, cycle, demands, available, base,
            additionalCycleReservations, delegatedCycleInputs, ignorePatternSubstitutions, cancellation);
        if (cacheable(result.status())) demandCache.put(cacheKey, result.copy());
        return result;
    }

    private static boolean cacheable(CycleExternalDemandStatus status) {
        // UNSUPPORTED also represents an exhausted/unknown lower-level search. Never turn that transient result
        // into a proof for a later route attempt; the explicit statuses below are deterministic for this key.
        return status != CycleExternalDemandStatus.UNSUPPORTED;
    }

    private Outcome solveDemandsUncached(CompiledNetwork network, CycleComponent cycle, Map<AEKey, Long> demands,
            KeyCounter available, SolveState base, Map<AEKey, Long> additionalCycleReservations,
            Set<AEKey> delegatedCycleInputs, boolean ignorePatternSubstitutions,
            ECOCancellation cancellation) throws InterruptedException {
        for (var reservation : additionalCycleReservations.entrySet()) {
            long amount = reservation.getValue();
            if (amount < 0L || available.get(reservation.getKey()) < amount) {
                return failure(CycleExternalDemandStatus.MISSING,
                    Map.of(reservation.getKey(), amount < 0L ? 0L : amount - available.get(reservation.getKey())),
                    "Cycle-owned stock is unavailable before external-demand planning");
            }
            if (amount > 0L && !base.stored.isUnbounded(reservation.getKey())) available.remove(reservation.getKey(), amount);
        }

        KeyCounter direct = new KeyCounter();
        List<SolveState> states = new ArrayList<>();
        Set<IPatternDetails> selected = new LinkedHashSet<>();
        Map<AEKey, Long> delegated = new LinkedHashMap<>();
        Map<AEKey, Long> deficits = new LinkedHashMap<>();
        for (var demand : demands.entrySet()) {
            cancellation.checkpoint();
            // A cyclic supplier owns both the requested output and the stock that can bootstrap its feedback
            // loop. Delegate the complete demand before consuming inventory here; otherwise the downstream
            // component reserves the seed as an ordinary boundary input and the supplier later observes no
            // remaining stock with which to start the cycle.
            if (delegatedCycleInputs.contains(demand.getKey()) && !base.stored.isUnbounded(demand.getKey())) {
                if (demand.getValue() > 0L) {
                    delegated.merge(demand.getKey(), demand.getValue(), Math::addExact);
                }
                continue;
            }
            long fromStock = Math.min(demand.getValue(), available.get(demand.getKey()));
            if (fromStock > 0) {
                if (!base.stored.isUnbounded(demand.getKey())) available.remove(demand.getKey(), fromStock);
                direct.add(demand.getKey(), fromStock);
            }
            long deficit = demand.getValue() - fromStock;
            if (deficit > 0) deficits.put(demand.getKey(), deficit);
        }
        if (!deficits.isEmpty()) {
            // All roots are in the same solve: full outputs and finite raw stock are credited exactly once.
            Outcome one = solveDeficits(network, cycle, deficits, available,
                base.stored.unboundedKeys(), ignorePatternSubstitutions, cancellation);
            if (!one.solved()) return one;
            SolveState state = one.states().getFirst();
            states.add(state);
            selected.addAll(one.selectedPatterns());
            for (var used : state.used) {
                if (!used.getValue().fitsLong()) {
                    return failure(CycleExternalDemandStatus.UNREPRESENTABLE, Map.of(),
                        "External DAG used amount exceeds AE2 long range: key=" + used.getKey()
                            + " amount=" + used.getValue() + " max=" + Long.MAX_VALUE);
                }
                if (available.get(used.getKey()) < used.getValue().longValueExact()) {
                    return failure(CycleExternalDemandStatus.MISSING,
                        Map.of(used.getKey(), used.getValue().longValueExact() - available.get(used.getKey())),
                        "External demands compete for the same remaining inventory");
                }
                if (!base.stored.isUnbounded(used.getKey())) available.remove(used.getKey(), used.getValue().longValueExact());
            }
        }
        return new Outcome(CycleExternalDemandStatus.SOLVED, direct, states, Map.of(),
            selected, delegated, delegated.isEmpty()
                ? "External demand solved through inventory and acyclic routes"
                : "External demand solved through inventory, acyclic routes, and delegated cycle components");
    }

    private static Map<AEKey, Long> copyLongMap(Map<AEKey, Long> source) {
        if (source.isEmpty()) return Map.of();
        return Map.copyOf(source);
    }

    private static Map<AEKey, Long> copyLongMap(KeyCounter source) {
        if (!source.iterator().hasNext()) return Map.of();
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (var entry : source) result.put(entry.getKey(), entry.getLongValue());
        return Map.copyOf(result);
    }

    private record BoundaryDemand(AEKey key, long amount) {}

    /** Component numbering/dependency metadata is irrelevant to the filtered external DAG; root order is not. */
    private record CacheKey(Set<AEKey> members, Set<IPatternDetails> forbiddenPatterns,
            List<BoundaryDemand> demands, Map<AEKey, Long> available,
            Set<AEKey> unbounded, Map<AEKey, Long> additionalReservations, Set<AEKey> delegatedInputs,
            boolean ignorePatternSubstitutions) {
    }

    private Outcome solveDeficits(CompiledNetwork network, CycleComponent cycle, Map<AEKey, Long> demands,
            KeyCounter inventory, Set<AEKey> unboundedKeys, boolean ignorePatternSubstitutions,
            ECOCancellation cancellation) throws InterruptedException {
        Set<IPatternDetails> forbiddenPatterns = cycle.patterns().stream()
            .map(pattern -> pattern.details()).collect(java.util.stream.Collectors.toSet());
        List<AEKey> forbiddenMembers = cycle.members();
        Map<AEKey, List<cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern>> producers =
            new LinkedHashMap<>();
        // A cycle member may have an independent alternate producer. Exclude only this component's physical
        // patterns; blanking every producer for the member would incorrectly reject a craftable startup seed.
        // Re-entry is still detected below when the filtered route ultimately depends on a forbidden member.
        network.producers().forEach((key, candidates) -> producers.put(key, candidates.stream()
            .filter(pattern -> !forbiddenPatterns.contains(pattern.details())).toList()));
        int patterns = producers.values().stream().mapToInt(List::size).sum();
        int edges = producers.values().stream().flatMap(List::stream).mapToInt(p -> p.inputs().size()).sum();
        CompiledNetwork filtered = new CompiledNetwork(demands.keySet().iterator().next(), producers,
            network.emittable().stream().filter(key -> !forbiddenMembers.contains(key)).collect(java.util.stream.Collectors.toSet()),
            patterns, edges);

        var graph = graphBuilder.build(filtered, cancellation);
        var selection = routeSelector.select(graph, cancellation);
        Set<IPatternDetails> deferredCyclePatterns = selection.cyclicComponents().stream()
            .flatMap(component -> component.patterns().stream())
            .map(pattern -> pattern.details()).collect(java.util.stream.Collectors.toSet());
        var solved = acyclicSolver.solveDemands(filtered,
            PlannerInventorySnapshot.of(inventory, unboundedKeys), demands,
            selection.choices(), deferredCyclePatterns, ignorePatternSubstitutions, cancellation);
        if (solved.status() == PlanningStatus.SUCCESS) {
            return new Outcome(CycleExternalDemandStatus.SOLVED, new KeyCounter(), List.of(solved.state()), Map.of(),
                solved.state().selected.values().stream().map(p -> p.details()).collect(java.util.stream.Collectors.toSet()),
                Map.of(), deferredCyclePatterns.isEmpty() ? "External DAG solved"
                    : "External DAG solved with cyclic suppliers deferred to component planning");
        }
        if (solved.status() == PlanningStatus.MISSING_ITEMS) {
            Map<AEKey, Long> missing = positive(solved.state().missingItems());
            if (missing.keySet().stream().anyMatch(forbiddenMembers::contains)) {
                return failure(CycleExternalDemandStatus.FORBIDDEN_ROUTE, missing,
                    "All usable external routes re-enter the current cycle component");
            }
            return new Outcome(CycleExternalDemandStatus.MISSING, new KeyCounter(), List.of(solved.state()),
                missing, Set.of(), Map.of(), "External DAG leaf material is missing");
        }
        if (solved.status() == PlanningStatus.AMOUNT_OVERFLOW) {
            return failure(CycleExternalDemandStatus.OVERFLOW, Map.of(), "External DAG arithmetic overflow");
        }
        if (solved.status() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE) {
            return failure(CycleExternalDemandStatus.UNREPRESENTABLE, Map.of(),
                solved.trace().diagnostics().stream()
                    .filter(diagnostic -> diagnostic.code() == cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic.Code
                        .EXECUTION_AMOUNT_UNREPRESENTABLE)
                    .map(cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic::message)
                    .findFirst().orElse("External DAG plan exceeds AE2 long range"));
        }
        return failure(CycleExternalDemandStatus.UNSUPPORTED, Map.of(),
            "External demands=" + demands + " status=" + solved.status()
                + ": " + solved.trace().diagnostics().stream()
                    .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
                    .collect(java.util.stream.Collectors.joining("; ")));
    }

    private static Outcome failure(CycleExternalDemandStatus status, Map<AEKey, Long> missing, String diagnostic) {
        return new Outcome(status, new KeyCounter(), List.of(), missing, Set.of(), Map.of(), diagnostic);
    }
    private static KeyCounter remainingInventory(KeyCounter inventory, SolveState base) {
        KeyCounter result = new KeyCounter();
        for (var entry : inventory) {
            if (base.stored.isUnbounded(entry.getKey())) {
                result.set(entry.getKey(), Long.MAX_VALUE);
                continue;
            }
            long remaining = base.used.get(entry.getKey()).compareTo(PlannerAmount.of(entry.getLongValue())) >= 0
                ? 0L : PlannerAmount.of(entry.getLongValue()).subtract(base.used.get(entry.getKey())).longValueExact();
            if (remaining > 0) result.add(entry.getKey(), remaining);
        }
        // Creative supply belongs to the snapshot, even when the finite inventory API has no entry for it.
        base.stored.unboundedKeys().forEach(key -> result.set(key, Long.MAX_VALUE));
        return result;
    }
    private static Map<AEKey, Long> positive(KeyCounter counter) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (var entry : counter) if (entry.getLongValue() > 0) result.put(entry.getKey(), entry.getLongValue());
        return result;
    }
}
