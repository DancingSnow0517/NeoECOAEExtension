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
    record Outcome(CycleExternalDemandStatus status, Map<AEKey, PlannerAmount> directReservations,
            List<SolveState> states, Map<AEKey, PlannerAmount> missingLeaves, Set<IPatternDetails> selectedPatterns,
            Map<AEKey, PlannerAmount> delegatedCycleDemands, String diagnostic) {
        boolean solved() { return status == CycleExternalDemandStatus.SOLVED; }

        Outcome copy() {
            Map<AEKey, PlannerAmount> reservations = new LinkedHashMap<>(directReservations);
            List<SolveState> copiedStates = new ArrayList<>(states.size());
            for (SolveState state : states) copiedStates.add(state.copy());
            return new Outcome(status, reservations, copiedStates, new LinkedHashMap<>(missingLeaves),
                new LinkedHashSet<>(selectedPatterns), new LinkedHashMap<>(delegatedCycleDemands), diagnostic);
        }

        void addMaterialPreview(SolveState base, cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace trace) {
            Map<AEKey, PlannerAmount> demands = new LinkedHashMap<>(directReservations);
            Map<AEKey, PlannerAmount> inventory = new LinkedHashMap<>(directReservations);
            for (SolveState state : states) {
                state.plannerDemands().forEach((key, amount) -> demands.merge(key, amount, PlannerAmount::add));
                state.usedAmounts().forEach((key, amount) -> inventory.merge(key, amount, PlannerAmount::add));
                // Keep the same byte arithmetic as a successful external merge, but no execution/stock effects.
                base.retainPreviewBytes(state.plannerBytes());
            }
            missingLeaves.forEach((key, amount) -> demands.merge(key, amount, PlannerAmount::max));
            AcyclicCraftingSolver.addMaterialTrace(demands, inventory, missingLeaves, trace);
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
    private static final int MAX_FILTERED_ROUTE_ENTRIES = 16;
    private static final int MAX_FILTERED_ROUTE_WEIGHT = 50_000;
    private CompiledNetwork cachedNetwork;
    private record FilteredRouteKey(Set<AEKey> members, Set<IPatternDetails> excluded, AEKey goal) {}
    private record FilteredRoute(CompiledNetwork network, ActiveRouteSelector.Selection selection, int weight) {}
    /** Pure structure only: a different quantity or stock still requires a fresh numeric solve. */
    private final Map<FilteredRouteKey, FilteredRoute> filteredRoutes = new LinkedHashMap<>(16, 0.75F, true);
    private int filteredRouteWeight;
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
        return solveExactDemands(network, cycle, cycleResult.positiveExactExternalDemand(),
            PlannerInventorySnapshot.of(inventory, base.stored.unboundedKeys()), base,
            exact(additionalCycleReservations), delegatedCycleInputs, ignorePatternSubstitutions, cancellation);
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
        return solveExactDemands(network, cycle, exact(demands),
            PlannerInventorySnapshot.of(inventory, base.stored.unboundedKeys()), base,
            exact(additionalCycleReservations), delegatedCycleInputs, ignorePatternSubstitutions, cancellation);
    }

    Outcome solveExactDemands(CompiledNetwork network, CycleComponent cycle, Map<AEKey, PlannerAmount> demands,
            PlannerInventorySnapshot inventory, SolveState base, Map<AEKey, PlannerAmount> additionalCycleReservations,
            Set<AEKey> delegatedCycleInputs, boolean ignorePatternSubstitutions,
            ECOCancellation cancellation) throws InterruptedException {
        cancellation.checkpoint();
        // CompiledNetwork is immutable and shared by all route attempts in this invocation. Comparing its
        // identity both invalidates changed recipes and avoids hashing the complete recipe graph on each hit.
        if (cachedNetwork != network) {
            demandCache.clear();
            filteredRoutes.clear();
            filteredRouteWeight = 0;
            cachedNetwork = network;
        }
        PlannerCounter available = remainingInventory(inventory, base);
        CacheKey cacheKey = new CacheKey(Set.copyOf(cycle.members()), cycle.patterns().stream()
            .map(pattern -> pattern.details()).collect(java.util.stream.Collectors.toSet()),
            demands.entrySet().stream().map(entry -> new BoundaryDemand(entry.getKey(), entry.getValue())).toList(),
            Map.copyOf(available.asMap()),
            Set.copyOf(base.stored.unboundedKeys()), Map.copyOf(additionalCycleReservations),
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

    private Outcome solveDemandsUncached(CompiledNetwork network, CycleComponent cycle, Map<AEKey, PlannerAmount> demands,
            PlannerCounter available, SolveState base, Map<AEKey, PlannerAmount> additionalCycleReservations,
            Set<AEKey> delegatedCycleInputs, boolean ignorePatternSubstitutions,
            ECOCancellation cancellation) throws InterruptedException {
        for (var reservation : additionalCycleReservations.entrySet()) {
            PlannerAmount amount = reservation.getValue();
            if (!available.isUnbounded(reservation.getKey()) && available.get(reservation.getKey()).compareTo(amount) < 0) {
                return failure(CycleExternalDemandStatus.MISSING,
                    Map.of(reservation.getKey(), amount.subtract(available.get(reservation.getKey()))),
                    "Cycle-owned stock is unavailable before external-demand planning");
            }
            if (amount.signum() > 0) available.remove(reservation.getKey(), amount);
        }

        Map<AEKey, PlannerAmount> direct = new LinkedHashMap<>();
        List<SolveState> states = new ArrayList<>();
        Set<IPatternDetails> selected = new LinkedHashSet<>();
        Map<AEKey, PlannerAmount> delegated = new LinkedHashMap<>();
        Map<AEKey, PlannerAmount> deficits = new LinkedHashMap<>();
        for (var demand : demands.entrySet()) {
            cancellation.checkpoint();
            // A cyclic supplier owns both the requested output and the stock that can bootstrap its feedback
            // loop. Delegate the complete demand before consuming inventory here; otherwise the downstream
            // component reserves the seed as an ordinary boundary input and the supplier later observes no
            // remaining stock with which to start the cycle.
            if (delegatedCycleInputs.contains(demand.getKey()) && !base.stored.isUnbounded(demand.getKey())) {
                if (demand.getValue().signum() > 0) {
                    delegated.merge(demand.getKey(), demand.getValue(), PlannerAmount::add);
                }
                continue;
            }
            PlannerAmount fromStock = available.available(demand.getKey(), demand.getValue());
            if (fromStock.signum() > 0) {
                if (!base.stored.isUnbounded(demand.getKey())) available.remove(demand.getKey(), fromStock);
                direct.merge(demand.getKey(), fromStock, PlannerAmount::add);
            }
            PlannerAmount deficit = demand.getValue().subtract(fromStock);
            if (deficit.signum() > 0) deficits.put(demand.getKey(), deficit);
        }
        if (!deficits.isEmpty()) {
            // All roots are in the same solve: full outputs and finite raw stock are credited exactly once.
            Outcome one = solveDeficits(network, cycle, deficits, available,
                base.stored.unboundedKeys(), ignorePatternSubstitutions, cancellation);
            if (!one.solved()) {
                // Keep the stock already credited to these boundary roots. A failed cycle is explanatory,
                // but dropping these reservations makes its report look as though all inventory was ignored.
                return new Outcome(one.status(), direct, one.states(), one.missingLeaves(), one.selectedPatterns(),
                    delegated, one.diagnostic());
            }
            SolveState state = cn.dancingsnow.neoecoae.platform.JdkCompat.first(one.states());
            states.add(state);
            selected.addAll(one.selectedPatterns());
            for (var used : state.used) {
                // Unlimited inputs are charged in the exact ledger; no finite stock window can bound their total.
                if (base.stored.isUnbounded(used.getKey())) continue;
                if (available.get(used.getKey()).compareTo(used.getValue()) < 0) {
                    return failure(CycleExternalDemandStatus.MISSING,
                        Map.of(used.getKey(), used.getValue().subtract(available.get(used.getKey()))),
                        "External demands compete for the same remaining inventory");
                }
                available.remove(used.getKey(), used.getValue());
            }
        }
        return new Outcome(CycleExternalDemandStatus.SOLVED, direct, states, Map.of(),
            selected, delegated, delegated.isEmpty()
                ? "External demand solved through inventory and acyclic routes"
                : "External demand solved through inventory, acyclic routes, and delegated cycle components");
    }

    private static Map<AEKey, PlannerAmount> exact(Map<AEKey, Long> source) {
        Map<AEKey, PlannerAmount> result = new LinkedHashMap<>();
        source.forEach((key, amount) -> result.put(key, PlannerAmount.of(amount)));
        return result;
    }

    private record BoundaryDemand(AEKey key, PlannerAmount amount) {}

    /** Component numbering/dependency metadata is irrelevant to the filtered external DAG; root order is not. */
    private record CacheKey(Set<AEKey> members, Set<IPatternDetails> forbiddenPatterns,
            List<BoundaryDemand> demands, Map<AEKey, PlannerAmount> available,
            Set<AEKey> unbounded, Map<AEKey, PlannerAmount> additionalReservations, Set<AEKey> delegatedInputs,
            boolean ignorePatternSubstitutions) {
    }

    private Outcome solveDeficits(CompiledNetwork network, CycleComponent cycle, Map<AEKey, PlannerAmount> demands,
            PlannerCounter inventory, Set<AEKey> unboundedKeys, boolean ignorePatternSubstitutions,
            ECOCancellation cancellation) throws InterruptedException {
        Set<IPatternDetails> forbiddenPatterns = cycle.patterns().stream()
            .map(pattern -> pattern.details()).collect(java.util.stream.Collectors.toSet());
        List<AEKey> forbiddenMembers = cycle.members();
        FilteredRoute route = filteredRoute(network, Set.copyOf(forbiddenMembers), forbiddenPatterns,
            demands.keySet().iterator().next(), cancellation);
        CompiledNetwork filtered = route.network();
        var selection = route.selection();
        Set<IPatternDetails> deferredCyclePatterns = selection.cyclicComponents().stream()
            .flatMap(component -> component.patterns().stream())
            .map(pattern -> pattern.details()).collect(java.util.stream.Collectors.toSet());
        var solved = acyclicSolver.solveExactDemands(filtered,
            PlannerInventorySnapshot.exact(inventory.asMap(), unboundedKeys), demands,
            selection.choices(), deferredCyclePatterns, ignorePatternSubstitutions, cancellation);
        // A complete exact DAG is usable internally even when its cumulative totals do not fit AE2's fields.
        // Keep genuine shortages/unsupported routes out of the cycle transaction.
        if (solved.status() == PlanningStatus.SUCCESS
                || solved.status() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
                    && solved.state().missingAmounts().isEmpty() && solved.state().unsupported.isEmpty()) {
            return new Outcome(CycleExternalDemandStatus.SOLVED, Map.of(), List.of(solved.state()), Map.of(),
                solved.state().selected.values().stream().map(p -> p.details()).collect(java.util.stream.Collectors.toSet()),
                Map.of(), deferredCyclePatterns.isEmpty() ? "External DAG solved"
                    : "External DAG solved with cyclic suppliers deferred to component planning");
        }
        if (solved.status() == PlanningStatus.MISSING_ITEMS
                || solved.status() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
                    && !solved.state().missingAmounts().isEmpty()) {
            Map<AEKey, PlannerAmount> missing = new LinkedHashMap<>(solved.state().missingAmounts());
            if (missing.keySet().stream().anyMatch(forbiddenMembers::contains)) {
                return new Outcome(CycleExternalDemandStatus.FORBIDDEN_ROUTE, Map.of(), List.of(solved.state()),
                    missing, Set.of(), Map.of(), "All usable external routes re-enter the current cycle component");
            }
            return new Outcome(CycleExternalDemandStatus.MISSING, Map.of(), List.of(solved.state()),
                missing, Set.of(), Map.of(), "External DAG leaf material is missing");
        }
        if (solved.status() == PlanningStatus.AMOUNT_OVERFLOW) {
            return failure(CycleExternalDemandStatus.OVERFLOW, Map.of(), "External DAG arithmetic overflow");
        }
        return failure(CycleExternalDemandStatus.UNSUPPORTED, Map.of(),
            "External demands=" + demands + " status=" + solved.status()
                + ": " + solved.trace().diagnostics().stream()
                    .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
                    .collect(java.util.stream.Collectors.joining("; ")));
    }

    private FilteredRoute filteredRoute(CompiledNetwork network, Set<AEKey> forbiddenMembers,
            Set<IPatternDetails> forbiddenPatterns, AEKey goal, ECOCancellation cancellation)
            throws InterruptedException {
        cancellation.checkpoint();
        FilteredRouteKey key = new FilteredRouteKey(forbiddenMembers, Set.copyOf(forbiddenPatterns), goal);
        FilteredRoute cached = filteredRoutes.get(key);
        if (cached != null) return cached;
        Map<AEKey, List<cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern>> producers =
            new LinkedHashMap<>();
        // Exclude only physical patterns owned by this component, retaining independent seed producers.
        network.producers().forEach((material, candidates) -> producers.put(material, candidates.stream()
            .filter(pattern -> !forbiddenPatterns.contains(pattern.details())).toList()));
        int patterns = producers.values().stream().mapToInt(List::size).sum();
        int edges = producers.values().stream().flatMap(List::stream).mapToInt(p -> p.inputs().size()).sum();
        CompiledNetwork filtered = new CompiledNetwork(goal, producers,
            network.emittable().stream().filter(material -> !forbiddenMembers.contains(material))
                .collect(java.util.stream.Collectors.toSet()), patterns, edges);
        var graph = graphBuilder.build(filtered, cancellation);
        var selection = routeSelector.select(graph, cancellation);
        long weight = (long) patterns + edges + producers.size();
        FilteredRoute result = new FilteredRoute(filtered, selection,
            (int) Math.min(Integer.MAX_VALUE, weight));
        if (!selection.budgetExhausted() && weight <= MAX_FILTERED_ROUTE_WEIGHT) {
            while (!filteredRoutes.isEmpty() && (filteredRoutes.size() >= MAX_FILTERED_ROUTE_ENTRIES
                    || filteredRouteWeight + weight > MAX_FILTERED_ROUTE_WEIGHT)) {
                var iterator = filteredRoutes.entrySet().iterator();
                filteredRouteWeight -= iterator.next().getValue().weight();
                iterator.remove();
            }
            filteredRoutes.put(key, result);
            filteredRouteWeight += result.weight();
        }
        return result;
    }

    private static Outcome failure(CycleExternalDemandStatus status, Map<AEKey, PlannerAmount> missing, String diagnostic) {
        return new Outcome(status, Map.of(), List.of(), missing, Set.of(), Map.of(), diagnostic);
    }
    private static PlannerCounter remainingInventory(PlannerInventorySnapshot inventory, SolveState base) {
        PlannerCounter result = new PlannerCounter();
        inventory.amounts().forEach((key, amount) -> result.set(key,
            amount.subtract(base.used.get(key)).max(PlannerAmount.ZERO)));
        base.stored.unboundedKeys().forEach(result::setUnbounded);
        return result;
    }
}
