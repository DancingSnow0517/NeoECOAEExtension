package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.core.planner.acyclic.SelectedRoute;
import cn.dancingsnow.neoecoae.core.planner.acyclic.StockConsumption;
import cn.dancingsnow.neoecoae.core.planner.acyclic.AcyclicPass;
import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.AEItemKey;
import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceEdge;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AcyclicCraftingSolver {
    public record Outcome(PlanningStatus status, SolveState state, ECOPlanTrace trace,
            Map<AEKey, Integer> cyclicRouteChoices) {
        public Outcome(PlanningStatus status, SolveState state, ECOPlanTrace trace) {
            this(status, state, trace, Map.of());
        }
        public Outcome {

        }
    }
    private final CandidateResolver candidates = new CandidateResolver();

    public Outcome solve(CompiledNetwork network, AcyclicRoutePlan route, KeyCounter inventory, long amount,
            ECOCancellation cancellation) throws InterruptedException {
        return solve(network, route, inventory, amount, Map.of(), cancellation);
    }

    public Outcome solve(CompiledNetwork network, AcyclicRoutePlan route, KeyCounter inventory, long amount,
            Map<AEKey, Integer> initialChoices, ECOCancellation cancellation) throws InterruptedException {
        return solve(network, route, inventory, amount, initialChoices, Set.of(), cancellation);
    }

    /**
     * Patterns owned by a cyclic component are selected to preserve route identity, but their arithmetic is
     * deferred wholesale to that component. This prevents one self-growing transition from being planned once
     * for an acyclic byproduct and a second time for its feedback member.
     */
    public Outcome solve(CompiledNetwork network, AcyclicRoutePlan route, KeyCounter inventory, long amount,
            Map<AEKey, Integer> initialChoices, Set<IPatternDetails> deferredPatterns,
            ECOCancellation cancellation) throws InterruptedException {
        return solve(network, route, inventory, amount, initialChoices, deferredPatterns,
            false, cancellation);
    }

    public Outcome solve(CompiledNetwork network, AcyclicRoutePlan route, KeyCounter inventory, long amount,
            Map<AEKey, Integer> initialChoices, Set<IPatternDetails> deferredPatterns,
            boolean ignorePatternSubstitutions, ECOCancellation cancellation) throws InterruptedException {
        return solve(network, route, PlannerInventorySnapshot.of(inventory), amount, initialChoices,
            deferredPatterns, ignorePatternSubstitutions, cancellation);
    }

    public Outcome solve(CompiledNetwork network, AcyclicRoutePlan route, PlannerInventorySnapshot inventory,
            long amount, Map<AEKey, Integer> initialChoices, Set<IPatternDetails> deferredPatterns,
            boolean ignorePatternSubstitutions, ECOCancellation cancellation) throws InterruptedException {
        return solveGoals(network, inventory, Map.of(network.goal(), PlannerAmount.of(amount)), true,
            initialChoices, deferredPatterns, ignorePatternSubstitutions, cancellation);
    }

    /** Boundary goals share one inventory and output-credit ledger; none is a player's newly crafted goal. */
    Outcome solveDemands(CompiledNetwork network, PlannerInventorySnapshot inventory, Map<AEKey, Long> demands,
            Map<AEKey, Integer> initialChoices, Set<IPatternDetails> deferredPatterns,
            boolean ignorePatternSubstitutions, ECOCancellation cancellation) throws InterruptedException {
        Map<AEKey, PlannerAmount> goals = new LinkedHashMap<>();
        demands.forEach((key, amount) -> { if (amount > 0) goals.put(key, PlannerAmount.of(amount)); });
        return solveGoals(network, inventory, goals, false, initialChoices, deferredPatterns,
            ignorePatternSubstitutions, cancellation);
    }

    private Outcome solveGoals(CompiledNetwork network, PlannerInventorySnapshot inventory,
            Map<AEKey, PlannerAmount> goals, boolean additionalGoal, Map<AEKey, Integer> initialChoices,
            Set<IPatternDetails> deferredPatterns, boolean ignorePatternSubstitutions,
            ECOCancellation cancellation) throws InterruptedException {
        long amount = goals.getOrDefault(network.goal(), PlannerAmount.ZERO).longValueExact();
        ECOPlanTrace trace = new ECOPlanTrace();
        if (goals.values().stream().anyMatch(value -> value.signum() <= 0)) {
            trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.AMOUNT_OVERFLOW, "Goal amount must be positive"));
            return new Outcome(PlanningStatus.AMOUNT_OVERFLOW, new SolveState(inventory), trace);
        }
        Map<AEKey, Integer> choices = new HashMap<>(initialChoices);
        int retryBudget = Math.max(1, network.reachablePatternCount() + 1);
        SolveState state = null;
        SolveState missingRoute = null;
        cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingDependencyGraph universe = null;
        for (int attempt = 0; attempt < retryBudget; attempt++) {
            cancellation.checkpoint();
            if (attempt > 0) {
                // The caller classified the initial route. A numeric retry can add feedback through
                // secondary outputs even when its ordinary input edges still look like a DAG.
                if (universe == null) {
                    universe = new cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder()
                        .build(network, cancellation);
                }
                var selected = new ActiveRouteSelector().selectWithChoices(universe, choices, cancellation);
                if (selected.cyclicComponents().stream()
                        .filter(cycle -> cycle.patterns().stream().anyMatch(pattern -> pattern.outputs().stream()
                            .anyMatch(output -> !output.what().equals(pattern.producedKey()))))
                        .flatMap(cycle -> cycle.patterns().stream())
                        .anyMatch(pattern -> !deferredPatterns.contains(pattern.details()))) {
                    return cyclicRouteOutcome(network, inventory, choices, trace);
                }
            }
            List<AEKey> currentRoute = selectedRoute(
                network, choices, deferredPatterns, Set.of(), goals.keySet(), cancellation);
            state = currentRoute == null
                ? solveStockBackedRoute(network, inventory, choices, goals, additionalGoal, deferredPatterns,
                    ignorePatternSubstitutions, cancellation)
                : runOnce(network, new AcyclicRoutePlan(currentRoute),
                    new SolveWorkspace(inventory, choices), goals, additionalGoal,
                    deferredPatterns, Set.of(), ignorePatternSubstitutions, cancellation);
            if (state == null) {
                return cyclicRouteOutcome(network, inventory, choices, trace);
            }
            if (state.missing.isEmpty() && state.unsupported.isEmpty()) {
                // Joint credit proves quantities, not startup. An actual supplier-to-consumer
                // cycle must be reclassified for seed solving before publishing DAG success.
                if (state.executionProvenance().hasPatternCycle(state.patternTimes.keySet(), cancellation)) {
                    return cyclicRouteOutcome(network, inventory, choices, trace);
                }
                addTrace(network, state, amount, trace);
                if (addExecutionRepresentabilityDiagnostics(state, trace)) {
                    return new Outcome(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, state, trace);
                }
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.FAST_DAG, "Acyclic aggregated solve"));
                return new Outcome(PlanningStatus.SUCCESS, state, trace);
            }
            if (!candidates.advanceAfterFailure(network, state, choices)) {
                // A later unsupported alternative must not erase a completely calculated material list.
                if (!state.unsupported.isEmpty() && missingRoute != null) state = missingRoute;
                addTrace(network, state, amount, trace);
                if (!state.unsupported.isEmpty()) {
                    trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.NATIVE_FALLBACK,
                        "No batch-safe candidate remains for " + state.unsupported));
                    return new Outcome(PlanningStatus.PARTIAL_UNSUPPORTED, state, trace);
                }
                if (addExecutionRepresentabilityDiagnostics(state, trace)) {
                    return new Outcome(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, state, trace);
                }
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.MISSING, "Required inputs are unavailable"));
                return new Outcome(PlanningStatus.MISSING_ITEMS, state, trace);
            }
            if (state.unsupported.isEmpty()) missingRoute = state;
            for (var rejected : state.selected.entrySet()) {
                trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, rejected.getKey(),
                    rejected.getValue().details(), 0, 0, 0, 0,
                    traceLong(state.patternTimes.getOrDefault(rejected.getValue().details(), PlannerAmount.ZERO)),
                    PlanTraceNode.Selection.REJECTED, state.unsupported.isEmpty()
                        ? "DOWNSTREAM_MISSING_ROLLBACK" : "DOWNSTREAM_UNSUPPORTED_ROLLBACK"));
            }
            trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CANDIDATE_REJECTED,
                "Candidate failed downstream; rolled back attempt " + (attempt + 1)));
        }
        trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.NATIVE_FALLBACK, "Candidate retry budget exhausted"));
        return new Outcome(PlanningStatus.PARTIAL_UNSUPPORTED, state, trace);
    }

    private static Outcome cyclicRouteOutcome(CompiledNetwork network, PlannerInventorySnapshot inventory,
            Map<AEKey, Integer> choices, ECOPlanTrace trace) {
        SolveState state = new SolveState(inventory);
        state.unsupported.add(network.goal());
        trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CANDIDATE_DEFERRED_CYCLE,
            "Selected alternate producer route requires cycle component planning"));
        return new Outcome(PlanningStatus.PARTIAL_UNSUPPORTED, state, trace, choices);
    }

    /**
     * A reversible recipe need not execute its reverse when stock already covers that input.
     * Try stocked keys as leaves, then reopen any leaf whose aggregated demand exceeds supply.
     * Each retry uses fresh inventory and removes at least one leaf; a remaining cycle still falls back.
     */
    private static SolveState solveStockBackedRoute(CompiledNetwork network, PlannerInventorySnapshot inventory,
            Map<AEKey, Integer> choices, Map<AEKey, PlannerAmount> goals, boolean additionalGoal,
            Set<IPatternDetails> deferredPatterns,
            boolean ignorePatternSubstitutions, ECOCancellation cancellation) throws InterruptedException {
        PlannerCounter stock = new PlannerCounter();
        inventory.initialize(stock);
        Set<AEKey> stockLeaves = new LinkedHashSet<>(stock.asMap().keySet());
        if (additionalGoal) stockLeaves.remove(network.goal());
        while (!stockLeaves.isEmpty()) {
            cancellation.checkpoint();
            List<AEKey> route = selectedRoute(network, choices, deferredPatterns, stockLeaves, goals.keySet(), cancellation);
            if (route == null) return null;
            SolveState state = runOnce(network, new AcyclicRoutePlan(route),
                new SolveWorkspace(inventory, choices), goals, additionalGoal, deferredPatterns, stockLeaves,
                ignorePatternSubstitutions, cancellation);
            if (!stockLeaves.removeAll(state.unsupported)) return state;
        }
        return null;
    }

    /**
     * Candidate fallback can change a pattern's inputs after the caller built its structural route. Recompute
     * the goal-first topological order for the current choices so a newly introduced dependency is never
     * visited before the consumer that creates its demand. Keys owned by cycle components remain outside this
     * pass and are left untouched for the cycle transaction.
     *
     * @return the selected route, or {@code null} when the selected acyclic subset now contains a cycle
     */
    private static List<AEKey> selectedRoute(CompiledNetwork network,
            Map<AEKey, Integer> choices, Set<IPatternDetails> deferredPatterns,
            Set<AEKey> stockLeaves, Set<AEKey> goals, ECOCancellation cancellation) throws InterruptedException {
        Set<AEKey> allowed = network.keys();
        if (!allowed.contains(network.goal())) return List.of();

        return SelectedRoute.order(allowed, goals, stockLeaves,
            key -> selectedPattern(network, key, choices), pattern -> deferredPatterns.contains(pattern.details()),
            (pattern, slot) -> pattern.specialAnalysis().excludesFromCycleGraph(pattern.inputs().get(slot)), cancellation);
    }

    private static CompiledPattern selectedPattern(CompiledNetwork network, AEKey key,
            Map<AEKey, Integer> choices) {
        List<CompiledPattern> candidates = network.fastProducersOf(key);
        if (candidates.isEmpty()) return null;
        int choice = choices.getOrDefault(key, 0);
        if (choice < 0) choice = 0;
        if (choice >= candidates.size()) choice = candidates.size() - 1;
        return candidates.get(choice);
    }

    private static SolveState runOnce(CompiledNetwork network, AcyclicRoutePlan route, SolveWorkspace workspace,
            Map<AEKey, PlannerAmount> goals, boolean additionalGoal,
            Set<IPatternDetails> deferredPatterns, Set<AEKey> stockLeaves,
            boolean ignorePatternSubstitutions,
            ECOCancellation cancellation)
            throws InterruptedException {
        SolveState state = new SolveState(workspace.inventory());
        SpecialPatternResolver specialResolver = new SpecialPatternResolver(
            network, state, workspace.candidateChoice(), cancellation, ignorePatternSubstitutions);
        AcyclicPass.run(state,
            new AcyclicPass.Domain<AEKey, CompiledPattern>() {
                @Override public AEKey goal() { return network.goal(); }
                @Override public CompiledPattern selected(AEKey key) {
                    return selectedPattern(network, key, workspace.candidateChoice());
                }
                @Override public boolean hasProducer(AEKey key) { return !network.producersOf(key).isEmpty(); }
                @Override public boolean emittable(AEKey key) { return network.emittable().contains(key); }
                @Override public boolean deferred(CompiledPattern pattern) { return deferredPatterns.contains(pattern.details()); }
                @Override public long amountPerByte(AEKey key) { return key.getAmountPerByte(); }
                @Override public PlannerAmount outputPerPattern(CompiledPattern pattern) { return pattern.outputPerPattern(); }
                @Override public int outputCount(CompiledPattern pattern) { return pattern.outputs().size(); }
                @Override public AEKey outputKey(CompiledPattern pattern, int i) { return pattern.outputs().get(i).what(); }
                @Override public long outputAmount(CompiledPattern pattern, int i) { return pattern.outputs().get(i).amount(); }
                @Override public boolean excludedInput(CompiledPattern pattern, int slot) {
                    return pattern.specialAnalysis().excludesFromCycleGraph(pattern.inputs().get(slot));
                }
                @Override public boolean reusableInput(CompiledPattern pattern, int slot) { return pattern.inputs().get(slot).reusable(); }
                @Override public AcyclicPass.Queue<AEKey> queue(
                        List<AEKey> order, Map<AEKey, CompiledPattern> selected, Set<AEKey> leaves) {
                    var queue = new JointOutputDemandQueue(order, selected, deferredPatterns, leaves);
                    return new AcyclicPass.Queue<>() {
                        @Override public void add(AEKey key) { queue.add(key); }
                        @Override public boolean isEmpty() { return queue.isEmpty(); }
                        @Override public AEKey poll() throws InterruptedException { return queue.poll(cancellation); }
                    };
                }
                @Override public void registerGoal(AEKey key, PlannerAmount amount) {
                    state.provenance.register(MaterialDemand.goal(key, amount));
                }
                @Override public PlannerAmount pending(AEKey key) { return state.provenance.pendingAmount(key); }
                @Override public PlannerAmount consumeRequestedStock(AEKey key, PlannerAmount requested) {
                    boolean ignoreComponents = false;
                    IPatternDetails.IInput matchingInput = null;
                    IPatternDetails consumer = state.demandProducers.get(key);
                    if (consumer != null) {
                        for (CompiledPattern candidate : network.producersOf(consumer.getOutputs().isEmpty()
                                ? key : consumer.getOutputs().getFirst().what())) {
                            if (candidate.details() != consumer) continue;
                            for (CompiledInput input : candidate.inputs()) {
                                if (input.key().equals(key) && input.ignoresComponents()) {
                                    ignoreComponents = true;
                                    matchingInput = input.source();
                                    break;
                                }
                            }
                            break;
                        }
                    }
                    return consumeStoredForInput(state, key, requested, ignoreComponents, null, consumer, matchingInput);
                }
                @Override public void emitted(AEKey key, PlannerAmount amount) {
                    state.provenance.allocatePending(key, key, MaterialSource.Emitted.INSTANCE, amount);
                }
                @Override public void supplied(AEKey key, CompiledPattern pattern, PlannerAmount amount, boolean deferred) {
                    if (deferred) state.deferredSuppliers.put(key, pattern.details());
                    state.provenance.allocatePending(key, key, new MaterialSource.PatternOutput(pattern.details(), true), amount);
                }
                @Override public void resolveSpecial(CompiledPattern pattern, PlannerAmount times) throws InterruptedException {
                    specialResolver.resolve(pattern, times);
                }
                @Override public PlannerAmount registerInput(CompiledPattern pattern, int slot, PlannerAmount required) {
                    CompiledInput input = pattern.inputs().get(slot);
                    MaterialDemand demand = MaterialDemand.input(pattern.details(), slot, input.key(), required);
                    state.provenance.register(demand);
                    return input.ignoresComponents() && input.key() instanceof AEItemKey
                        ? consumeStoredForInput(state, input.key(), required, true, demand, pattern.details(), input.source())
                        : PlannerAmount.ZERO;
                }
                @Override public void inputProducer(AEKey key, CompiledPattern pattern) { state.demandProducers.put(key, pattern.details()); }
            }, route.keys(), goals, additionalGoal, stockLeaves, cancellation);
        return state;
    }

    private static PlannerAmount consumeStoredForInput(SolveState state, AEKey key, PlannerAmount requested,
            boolean ignoreComponents, MaterialDemand demand, IPatternDetails consumer,
            IPatternDetails.IInput matchingInput) {
        return StockConsumption.consume(state.stored, state.used,
            key, requested, ignoreComponents && key instanceof AEItemKey,
            storedKey -> storedKey instanceof AEItemKey candidate && key instanceof AEItemKey wanted
                && candidate.getItem() == wanted.getItem()
                && cn.dancingsnow.neoecoae.crafting.ECOProcessingComponentMatching
                    .isValid(consumer, matchingInput, key, storedKey, null),
            (storedKey, amount) -> {
                if (demand == null) state.provenance.allocatePending(key, storedKey, MaterialSource.Stock.INSTANCE, amount);
                else state.provenance.allocate(demand, storedKey, MaterialSource.Stock.INSTANCE, amount);
            });
    }

    private static void addTrace(CompiledNetwork network, SolveState state, long goalAmount, ECOPlanTrace trace) {
        PlannerAmount goal = PlannerAmount.of(goalAmount);
        trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.GOAL, network.goal(), null, goalAmount, 0, goalAmount,
            traceLong(state.missing.get(network.goal())), 0, PlanTraceNode.Selection.NOT_APPLICABLE, null)
            .withExact(goal.toBigInteger(), PlannerAmount.ZERO.toBigInteger(), goal.toBigInteger(),
                state.missing.get(network.goal()).toBigInteger(), PlannerAmount.ZERO.toBigInteger()));
        for (var demand : state.demand.entrySet()) {
            PlannerAmount used = state.used.get(demand.getKey());
            PlannerAmount missing = state.missing.get(demand.getKey());
            PlannerAmount requested = demand.getValue();
            PlannerAmount toCraft = demand.getValue().subtract(used).subtract(missing).max(PlannerAmount.ZERO);
            trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.MATERIAL, demand.getKey(), null, traceLong(requested),
                traceLong(used), traceLong(toCraft), traceLong(missing), 0,
                PlanTraceNode.Selection.NOT_APPLICABLE, null)
                .withExact(requested.toBigInteger(), used.toBigInteger(), toCraft.toBigInteger(),
                    missing.toBigInteger(), java.math.BigInteger.ZERO));
        }
        for (var selected : state.selected.entrySet()) {
            CompiledPattern pattern = selected.getValue();
            long times = traceLong(state.patternTimes.getOrDefault(pattern.details(), PlannerAmount.ZERO));
            trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, selected.getKey(), pattern.details(), 0, 0, 0,
                0, times, PlanTraceNode.Selection.SELECTED, null));
            for (CompiledInput input : pattern.inputs()) {
                PlannerAmount exact = input.amountPerPattern().multiply(state.patternTimes
                    .getOrDefault(pattern.details(), PlannerAmount.ZERO));
                trace.addEdge(new PlanTraceEdge(selected.getKey(), input.key(), traceLong(exact), exact.toBigInteger()));
            }
        }
        for (var entry : network.producers().entrySet()) {
            for (CompiledPattern pattern : entry.getValue()) if (!pattern.fastSupported()) {
                trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, entry.getKey(), pattern.details(), 0, 0, 0,
                    0, 0, PlanTraceNode.Selection.UNSUPPORTED, pattern.unsupportedReason()));
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.UNSUPPORTED_INPUT,
                    pattern.unsupportedReason()));
            }
        }
    }

    private static boolean addExecutionRepresentabilityDiagnostics(SolveState state, ECOPlanTrace trace) {
        List<SolveState.ExecutionAmountIssue> issues = state.executionAmountIssues();
        for (var issue : issues) {
            String key = issue.key() == null ? "<pattern/plan>" : issue.key().toString();
            String producer = issue.producer() == null ? "<counter>" : issue.producer().toString();
            trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.EXECUTION_AMOUNT_UNREPRESENTABLE,
                "Execution amount exceeds AE2 long range: key=" + key + " producer=" + producer
                    + " pattern=" + producer + " amount=" + issue.amount() + " max=" + Long.MAX_VALUE
                    + " stage=" + issue.stage()));
        }
        return !issues.isEmpty();
    }

    private static long traceLong(PlannerAmount amount) {
        return amount.fitsLong() ? amount.longValueExact() : 0L;
    }
}
