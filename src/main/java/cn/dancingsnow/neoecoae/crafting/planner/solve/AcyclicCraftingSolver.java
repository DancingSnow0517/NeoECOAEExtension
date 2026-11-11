package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.AEItemKey;
import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlanningStageLogger;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand;
import cn.dancingsnow.neoecoae.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceEdge;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlanTraceNode;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import java.util.ArrayDeque;
import java.util.ArrayList;
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

    /**
     * GTNH's lite traversal on the existing compiled graph and exact material ledger. Each key is visited once;
     * stock is shared across branches, and cyclic leftovers receive one final exact-stock draw. There is no
     * candidate retry, returned-container/tool analysis, byproduct credit, or cycle-growth solving.
     */
    public Outcome solveLite(CompiledNetwork network,
            cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph condensation,
            PlannerInventorySnapshot inventory, long amount, ECOCancellation cancellation) throws InterruptedException {
        SolveState state = new SolveState(inventory);
        ECOPlanTrace trace = new ECOPlanTrace();
        if (amount <= 0L) return new Outcome(PlanningStatus.AMOUNT_OVERFLOW, state, trace);
        PlannerAmount goalAmount = PlannerAmount.of(amount);
        state.stored.set(network.goal(), PlannerAmount.ZERO);
        state.demand.put(network.goal(), goalAmount);
        state.provenance.register(MaterialDemand.goal(network.goal(), goalAmount));
        state.bytes = PlannerAmount.of(network.keys().size()).multiply(8L);

        Map<AEKey, Integer> inDegree = new HashMap<>();
        for (var patterns : network.producers().values()) {
            cancellation.checkpoint();
            if (!patterns.isEmpty()) for (CompiledInput input : patterns.get(0).inputs()) {
                inDegree.merge(input.key(), 1, Integer::sum);
            }
        }
        Set<AEKey> looping = new LinkedHashSet<>();
        for (var cycle : condensation.cycles()) {
            if (cycle.members().size() > 1) looping.addAll(cycle.members());
        }
        ArrayDeque<AEKey> ready = new ArrayDeque<>();
        ArrayDeque<AEKey> loopCandidates = new ArrayDeque<>();
        Set<AEKey> visited = new LinkedHashSet<>();
        ready.add(network.goal());
        while (!ready.isEmpty() || !loopCandidates.isEmpty()) {
            cancellation.checkpoint();
            AEKey key = ready.isEmpty() ? loopCandidates.removeFirst() : ready.removeFirst();
            if (!visited.add(key)) continue;
            var patterns = network.producersOf(key);
            CompiledPattern pattern = patterns.isEmpty() ? null : patterns.get(0);
            // Even a stock-satisfied branch releases its dependency edges, just as in GTNH.
            if (pattern != null) for (CompiledInput input : pattern.inputs()) {
                if (inDegree.merge(input.key(), -1, Integer::sum) == 0) ready.addLast(input.key());
                if (looping.contains(input.key())) loopCandidates.addLast(input.key());
            }
            PlannerAmount requested = state.provenance.pendingAmount(key);
            state.bytes = state.bytes.add(PlannerAmount.stackBytes(requested, key.getAmountPerByte()));
            requested = requested.subtract(consumeStoredForInput(state, key, requested, false, null, null, null));
            if (requested.isZero()) continue;
            if (pattern == null) continue;
            if (!pattern.fastSupported()) {
                state.unsupported.add(key);
                continue;
            }
            state.selected.put(key, pattern);
            PlannerAmount times = requested.ceilDiv(pattern.outputPerPattern());
            state.patternTimes.merge(pattern.details(), times, PlannerAmount::add);
            state.bytes = state.bytes.add(times);
            state.provenance.allocatePending(key, key, new MaterialSource.PatternOutput(pattern.details(), true), requested);
            for (int slot = 0; slot < pattern.inputs().size(); slot++) {
                cancellation.checkpoint();
                CompiledInput input = pattern.inputs().get(slot);
                PlannerAmount required = input.amountPerPattern().multiply(times);
                state.provenance.register(MaterialDemand.input(pattern.details(), slot, input.key(), required));
                state.demand.merge(input.key(), required, PlannerAmount::add);
            }
        }
        // A loop may add a demand after that material's visit. Do not expand it a second time or borrow outputs
        // from an unexecuted cycle; only the remaining captured stock may close it.
        for (AEKey key : state.demand.keySet()) {
            cancellation.checkpoint();
            PlannerAmount pending = state.provenance.pendingAmount(key);
            pending = pending.subtract(consumeStoredForInput(state, key, pending, false, null, null, null));
            if (pending.signum() > 0) state.missing.add(key, pending);
        }
        addTrace(network, state, goalAmount, trace);
        if (!state.unsupported.isEmpty()) return new Outcome(PlanningStatus.PARTIAL_UNSUPPORTED, state, trace);
        if (addExecutionRepresentabilityDiagnostics(state, trace)) {
            return new Outcome(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, state, trace);
        }
        trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.FAST_DAG,
            "Lite crafting: one exact producer per material, one topological pass"));
        return new Outcome(state.missing.isEmpty() ? PlanningStatus.SUCCESS : PlanningStatus.MISSING_ITEMS, state, trace);
    }


    public Outcome solve(CompiledNetwork network, AcyclicRoutePlan route, KeyCounter inventory, long amount,
            ECOCancellation cancellation) throws InterruptedException {
        return solve(network, route, inventory, amount, Map.of(), Set.of(), false, cancellation);
    }

    public Outcome solve(CompiledNetwork network, AcyclicRoutePlan route, KeyCounter inventory, long amount,
            Map<AEKey, Integer> initialChoices, Set<IPatternDetails> deferredPatterns,
            boolean ignorePatternSubstitutions, ECOCancellation cancellation) throws InterruptedException {
        return solve(network, route, PlannerInventorySnapshot.of(inventory), amount, initialChoices,
            deferredPatterns, ignorePatternSubstitutions, cancellation);
    }

    /**
     * Patterns owned by a cyclic component retain route identity, but their arithmetic is deferred to that
     * component so feedback and acyclic byproducts cannot charge the same firing twice.
     */
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
        return solveExactDemands(network, inventory, goals, initialChoices, deferredPatterns,
            ignorePatternSubstitutions, cancellation);
    }

    Outcome solveExactDemands(CompiledNetwork network, PlannerInventorySnapshot inventory,
            Map<AEKey, PlannerAmount> demands, Map<AEKey, Integer> initialChoices,
            Set<IPatternDetails> deferredPatterns, boolean ignorePatternSubstitutions,
            ECOCancellation cancellation) throws InterruptedException {
        return solveGoals(network, inventory, demands, false, initialChoices, deferredPatterns,
            ignorePatternSubstitutions, cancellation);
    }

    private Outcome solveGoals(CompiledNetwork network, PlannerInventorySnapshot inventory,
            Map<AEKey, PlannerAmount> goals, boolean additionalGoal, Map<AEKey, Integer> initialChoices,
            Set<IPatternDetails> deferredPatterns, boolean ignorePatternSubstitutions,
            ECOCancellation cancellation) throws InterruptedException {
        AcyclicSolveMetrics metrics = ECOPlanningStageLogger.isEnabled() ? new AcyclicSolveMetrics() : null;
        try {
            Outcome outcome = solveGoals(network, inventory, goals, additionalGoal, initialChoices,
                deferredPatterns, ignorePatternSubstitutions, cancellation, metrics);
            if (metrics != null) metrics.status = outcome.status().name();
            return outcome;
        } finally {
            if (metrics != null) metrics.finish();
        }
    }

    private Outcome solveGoals(CompiledNetwork network, PlannerInventorySnapshot inventory,
            Map<AEKey, PlannerAmount> goals, boolean additionalGoal, Map<AEKey, Integer> initialChoices,
            Set<IPatternDetails> deferredPatterns, boolean ignorePatternSubstitutions,
            ECOCancellation cancellation, AcyclicSolveMetrics metrics) throws InterruptedException {
        PlannerAmount amount = goals.getOrDefault(network.goal(), PlannerAmount.ZERO);
        ECOPlanTrace trace = new ECOPlanTrace();
        if (goals.values().stream().anyMatch(value -> value.signum() <= 0)) {
            trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.AMOUNT_OVERFLOW, "Goal amount must be positive"));
            return new Outcome(PlanningStatus.AMOUNT_OVERFLOW, new SolveState(inventory), trace);
        }
        Map<AEKey, Integer> choices = new HashMap<>(initialChoices);
        SolveWorkspace workspace = new SolveWorkspace(inventory, choices);
        int retryBudget = Math.max(1, network.reachablePatternCount() + 1);
        SolveState state = null;
        SolveState missingRoute = null;
        cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingDependencyGraph universe = null;
        ActiveRouteSelector.RetryCheck retryCheck = null;
        for (int attempt = 0; attempt < retryBudget; attempt++) {
            cancellation.checkpoint();
            if (metrics != null) metrics.attempts++;
            if (attempt > 0) {
                // The caller classified the initial route. A numeric retry can add feedback through
                // secondary outputs even when its ordinary input edges still look like a DAG.
                if (universe == null) {
                    long started = metrics == null ? 0L : System.nanoTime();
                    try {
                        if (metrics != null) metrics.universeBuilds++;
                        universe = new cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder()
                            .build(network, cancellation);
                    } finally {
                        if (metrics != null) metrics.universeNanos += System.nanoTime() - started;
                    }
                }
                if (retryCheck == null) {
                    retryCheck = new ActiveRouteSelector.RetryCheck(universe, deferredPatterns, metrics, cancellation);
                }
                if (retryCheck.requiresCycle(choices, cancellation)) {
                    return cyclicRouteOutcome(network, inventory, choices, trace);
                }
            }
            List<AEKey> currentRoute = selectedRoute(
                network, choices, deferredPatterns, Set.of(), goals.keySet(), cancellation);
            state = currentRoute == null
                ? solveStockBackedRoute(network, workspace, goals, additionalGoal, deferredPatterns,
                    ignorePatternSubstitutions, cancellation, metrics)
                : runOnce(network, new AcyclicRoutePlan(currentRoute),
                    workspace, goals, additionalGoal,
                    deferredPatterns, Set.of(), ignorePatternSubstitutions, cancellation, metrics);
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
            workspace.enableExpansionCache();
            if (metrics != null) metrics.candidateSwitches++;
            boolean findCheckpoint = workspace.needsCheckpointTarget();
            AEKey checkpointTarget = null;
            boolean checkpointRetryPossible = false;
            for (var rejected : state.selected.entrySet()) {
                if (findCheckpoint) {
                    if (checkpointTarget == null && selectedPattern(network, rejected.getKey(), choices) != rejected.getValue()) {
                        checkpointTarget = rejected.getKey();
                    }
                    if (choices.getOrDefault(rejected.getKey(), 0) + 1 < network.fastProducersOf(rejected.getKey()).size()) {
                        checkpointRetryPossible = true;
                    }
                }
                trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, rejected.getKey(),
                    rejected.getValue().details(), 0, 0, 0, 0,
                    traceLong(state.patternTimes.getOrDefault(rejected.getValue().details(), PlannerAmount.ZERO)),
                    PlanTraceNode.Selection.REJECTED, state.unsupported.isEmpty()
                        ? "DOWNSTREAM_MISSING_ROLLBACK" : "DOWNSTREAM_UNSUPPORTED_ROLLBACK"));
            }
            if (checkpointTarget != null) workspace.prepareCheckpoint(checkpointTarget, checkpointRetryPossible);
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
    private static SolveState solveStockBackedRoute(CompiledNetwork network, SolveWorkspace workspace,
            Map<AEKey, PlannerAmount> goals, boolean additionalGoal,
            Set<IPatternDetails> deferredPatterns,
            boolean ignorePatternSubstitutions, ECOCancellation cancellation, AcyclicSolveMetrics metrics) throws InterruptedException {
        PlannerCounter stock = new PlannerCounter();
        workspace.inventory().initialize(stock);
        Set<AEKey> stockLeaves = new LinkedHashSet<>(stock.asMap().keySet());
        if (additionalGoal) stockLeaves.remove(network.goal());
        while (!stockLeaves.isEmpty()) {
            cancellation.checkpoint();
            List<AEKey> route = selectedRoute(network, workspace.candidateChoice(), deferredPatterns,
                stockLeaves, goals.keySet(), cancellation);
            if (route == null) return null;
            SolveState state = runOnce(network, new AcyclicRoutePlan(route),
                workspace, goals, additionalGoal, deferredPatterns, stockLeaves,
                ignorePatternSubstitutions, cancellation, metrics);
            if (!stockLeaves.removeAll(state.unsupported)) return state;
            workspace.enableExpansionCache();
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

        Map<AEKey, Set<AEKey>> outgoing = new LinkedHashMap<>();
        Map<AEKey, Integer> indegree = new LinkedHashMap<>();
        ArrayDeque<AEKey> discover = new ArrayDeque<>();
        Set<AEKey> reachable = new LinkedHashSet<>();
        for (AEKey goal : goals) {
            discover.add(goal);
            reachable.add(goal);
            indegree.put(goal, 0);
        }

        while (!discover.isEmpty()) {
            cancellation.checkpoint();
            AEKey key = discover.removeFirst();
            if (stockLeaves.contains(key)) continue;
            CompiledPattern selected = selectedPattern(network, key, choices);
            if (selected == null || deferredPatterns.contains(selected.details())) continue;
            Set<AEKey> dependencies = outgoing.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            for (CompiledInput input : selected.inputs()) {
                if (selected.specialAnalysis().excludesFromCycleGraph(input)) continue;
                AEKey dependency = input.key();
                if (!allowed.contains(dependency) || !dependencies.add(dependency)) continue;
                indegree.merge(dependency, 1, Integer::sum);
                if (reachable.add(dependency)) {
                    indegree.putIfAbsent(dependency, 0);
                    discover.addLast(dependency);
                }
            }
        }

        ArrayDeque<AEKey> ready = new ArrayDeque<>();
        for (AEKey key : reachable) if (indegree.getOrDefault(key, 0) == 0) ready.addLast(key);
        List<AEKey> ordered = new ArrayList<>(reachable.size());
        while (!ready.isEmpty()) {
            cancellation.checkpoint();
            AEKey key = ready.removeFirst();
            ordered.add(key);
            for (AEKey dependency : outgoing.getOrDefault(key, Set.of())) {
                int remaining = indegree.merge(dependency, -1, Integer::sum);
                if (remaining == 0) ready.addLast(dependency);
            }
        }
        return ordered.size() == reachable.size() ? ordered : null;
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
            ECOCancellation cancellation, AcyclicSolveMetrics metrics)
            throws InterruptedException {
        long started = metrics == null ? 0L : System.nanoTime();
        try {
            return expandDemands(network, route, workspace, goals, additionalGoal, deferredPatterns,
                stockLeaves, ignorePatternSubstitutions, cancellation, metrics);
        } finally {
            if (metrics != null) metrics.runOnceNanos += System.nanoTime() - started;
        }
    }

    private static SolveState expandDemands(CompiledNetwork network, AcyclicRoutePlan route, SolveWorkspace workspace,
            Map<AEKey, PlannerAmount> goals, boolean additionalGoal, Set<IPatternDetails> deferredPatterns,
            Set<AEKey> stockLeaves, boolean ignorePatternSubstitutions,
            ECOCancellation cancellation, AcyclicSolveMetrics metrics) throws InterruptedException {
        AEKey checkpointBefore = workspace.takeCheckpointTarget();
        boolean retryPossible = workspace.checkpointRetryPossible();
        Map<AEKey, CompiledPattern> selected = new LinkedHashMap<>();
        for (AEKey key : route.keys()) {
            CompiledPattern pattern = selectedPattern(network, key, workspace.candidateChoice());
            if (pattern != null) {
                selected.put(key, pattern);
                if (checkpointBefore != null && workspace.candidateChoice().getOrDefault(key, 0) + 1
                        < network.fastProducersOf(key).size()) retryPossible = true;
            }
        }
        // Preserve AE2's primary-output activation and full-output credit contracts. Unlike the
        // original input-order traversal, postpone a joint-output demand while another requested
        // primary branch can supply it. No additional producer views or speculative firings exist.
        JointOutputDemandQueue pending = new JointOutputDemandQueue(route.keys(), selected,
            deferredPatterns, stockLeaves);
        goals.keySet().forEach(pending::add);
        SolveWorkspace.RetryCheckpoint saved = workspace.checkpoint;
        boolean restored = false;
        if (saved != null) {
            long started = metrics == null ? 0L : System.nanoTime();
            try {
                // This is an induction over the actual processing order, not a comparison of merged amounts.
                // Identical completed steps see identical stock, credits, pending UUIDs and supplier order.
                // The new queue also checks potential byproducts, including sources unused by the old plan.
                // Special inputs can resolve producers outside the ordinary demand queue. Reuse the existing
                // selected ledger to check those decisions as well as the queue's order and joint-output proof.
                restored = stockLeaves.isEmpty();
                if (restored) for (var entry : saved.state.selected.entrySet()) {
                    cancellation.checkpoint();
                    if (selectedPattern(network, entry.getKey(), workspace.candidateChoice()) != entry.getValue()) {
                        restored = false;
                        break;
                    }
                }
                if (restored) restored = pending.restorePrefix(saved.polled, saved.expanded, saved.selected,
                    selected, cancellation);
            } finally {
                if (metrics != null) metrics.prefixCheckNanos += System.nanoTime() - started;
            }
            if (!restored) {
                workspace.checkpoint = null;
                pending.reset(goals.keySet());
                if (metrics != null) metrics.incrementalFallbacks++;
            }
        }
        SolveState state;
        Set<AEKey> unsettled;
        if (restored) {
            long started = metrics == null ? 0L : System.nanoTime();
            state = saved.state.copy();
            state.bytes = state.bytes.add(PlannerAmount.of((long) route.keys().size() - saved.routeSize).multiply(8L));
            unsettled = new LinkedHashSet<>(saved.unsettled);
            if (metrics != null) {
                metrics.incrementalRestores++;
                metrics.restoreNanos += System.nanoTime() - started;
                metrics.retainedMaterialVisits += saved.materialVisits;
                metrics.retainedPatternExpansions += saved.expanded.cardinality();
                metrics.retainedInputExpansions += saved.inputExpansions;
            }
        } else {
            if (metrics != null) metrics.runOnceCalls++;
            state = new SolveState(workspace.inventory());
            if (additionalGoal) state.stored.set(network.goal(), PlannerAmount.ZERO);
            for (var goal : goals.entrySet()) {
                state.demand.put(goal.getKey(), goal.getValue());
                state.provenance.register(MaterialDemand.goal(goal.getKey(), goal.getValue()));
            }
            state.bytes = PlannerAmount.of(route.keys().size()).multiply(8L);
            unsettled = new LinkedHashSet<>();
        }
        SpecialPatternResolver specialResolver = new SpecialPatternResolver(
            network, state, workspace.candidateChoice(), cancellation, ignorePatternSubstitutions);
        if (restored) specialResolver.restoreReservations(saved.reusableStock);
        List<AEKey> prefix = checkpointBefore != null && stockLeaves.isEmpty() && retryPossible
                && route.keys().size() > SolveWorkspace.MIN_CHECKPOINT_EXPANSIONS ? new ArrayList<>() : null;
        if (metrics != null && checkpointBefore != null) {
            metrics.checkpointDecision = !stockLeaves.isEmpty() ? "STOCK_LEAVES" : !retryPossible ? "NO_RETRY"
                : prefix == null ? "SHORT_ROUTE" : "NO_BOUNDARY";
        }
        java.util.BitSet expanded = prefix == null ? null : new java.util.BitSet();
        long prefixVisits = 0;
        long prefixInputs = 0;
        while (!pending.isEmpty()) {
            cancellation.checkpoint();
            AEKey key = pending.poll(cancellation);
            if (prefix != null) {
                CompiledPattern next = selected.get(key);
                boolean deferred = next != null && deferredPatterns.contains(next.details());
                // A compiled substitution slot already chooses one concrete demand. Component consumption,
                // remainders and special-input work are retained in state plus the resolver's reservations.
                boolean resumable = next == null || (next.fastSupported() && next.semantics().supported()
                    && (next.semantics().matchingMode() == PatternSemantics.MatchingMode.EXACT
                        || next.semantics().matchingMode() == PatternSemantics.MatchingMode.SUBSTITUTION)
                    && next.semantics().executionRestriction() == PatternSemantics.ExecutionRestriction.NONE);
                // Every snapshot is between complete material steps, after nested special resolution unwinds.
                boolean boundary = key.equals(checkpointBefore) || deferred || !resumable;
                if (boundary || prefix.size() == SolveWorkspace.MAX_CHECKPOINT_STEPS) {
                    // Do not copy a stock ledger far larger than the input work this prefix can save.
                    long stockKeys = (long) workspace.inventory().amounts().size() + workspace.inventory().unboundedKeys().size();
                    int expansions = expanded.cardinality();
                    if (metrics != null) {
                        if (boundary && next != null) {
                            StringBuilder details = new StringBuilder("key=").append(key)
                                .append(" pattern=").append(next.id())
                                .append(" patternClass=").append(next.details().getClass().getName())
                                .append(" deferred=").append(deferred).append(" resumable=").append(resumable)
                                .append(" prefixExpansions=").append(expansions)
                                .append(" prefixInputs=").append(prefixInputs).append(" stockKeys=").append(stockKeys)
                                .append(" complete=").append(next.semantics().completeForStaticPlanning())
                                .append(" matching=").append(next.semantics().matchingMode())
                                .append(" restriction=").append(next.semantics().executionRestriction())
                                .append(" reason=").append(next.semantics().unsupportedReason())
                                .append(" returned=").append(next.semantics().returnedOutputs())
                                .append(" feedback=").append(next.semantics().feedbackEdges());
                            for (int slot = 0; slot < next.inputs().size(); slot++) {
                                CompiledInput input = next.inputs().get(slot);
                                if (input.ignoresComponents()) details.append(" componentInput[").append(slot)
                                    .append("]=").append(input.key());
                                if (input.remainderKey() != null) details.append(" remainderInput[").append(slot)
                                    .append("]=").append(input.key()).append("->").append(input.remainderKey());
                            }
                            for (var requirement : next.specialAnalysis().requirements()) {
                                details.append(" specialInput=").append(requirement.type()).append(':')
                                    .append(requirement.input().key());
                            }
                            ECOPlanningStageLogger.statistics("checkpoint_boundary", System.nanoTime(), details.toString());
                        }
                        metrics.checkpointBoundary = deferred ? "DEFERRED" : !resumable ? "SPECIAL"
                            : boundary ? "CANDIDATE" : "STEP_LIMIT";
                        metrics.checkpointPrefixSteps = prefix.size();
                        metrics.checkpointPrefixExpansions = expansions;
                        metrics.checkpointPrefixInputs = prefixInputs;
                        metrics.checkpointDecision = !boundary ? "STEP_LIMIT"
                            : expansions < SolveWorkspace.MIN_CHECKPOINT_EXPANSIONS ? "SHORT_PREFIX" : "STOCK_COST";
                    }
                    if (boundary && expansions >= SolveWorkspace.MIN_CHECKPOINT_EXPANSIONS
                            && stockKeys <= prefixInputs * 16L) {
                        long started = metrics == null ? 0L : System.nanoTime();
                        workspace.checkpoint = new SolveWorkspace.RetryCheckpoint(state, prefix, expanded, selected,
                            unsettled, route.keys().size(), prefixVisits, prefixInputs, specialResolver);
                        if (metrics != null) {
                            metrics.checkpointDecision = "COPIED";
                            metrics.checkpointCopies++;
                            metrics.checkpointNanos += System.nanoTime() - started;
                        }
                    }
                    prefix = null;
                }
            }
            if (prefix != null) prefix.add(key);
            PlannerAmount requested = state.provenance.pendingAmount(key);
            if (requested.signum() <= 0) continue;
            if (metrics != null) metrics.materialVisits++;
            if (prefix != null) prefixVisits++;
            state.bytes = state.bytes.add(PlannerAmount.stackBytes(requested, key.getAmountPerByte()));
            boolean ignoreComponents = false;
            IPatternDetails.IInput matchingInput = null;
            IPatternDetails demandProducer = state.demandProducers.get(key);
            if (demandProducer != null) {
                for (CompiledPattern candidate : network.producersOf(demandProducer.getOutputs().isEmpty()
                        ? key : demandProducer.getOutputs().getFirst().what())) {
                    if (candidate.details() != demandProducer) continue;
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
            PlannerAmount stored = consumeStoredForInput(state, key, requested, ignoreComponents,
                null, demandProducer, matchingInput);
            if (stored.signum() > 0) {
                requested = requested.subtract(stored);
            }
            PlannerAmount crafted = requested.min(state.craftedAmount(key));
            if (crafted.signum() > 0) {
                state.consumeCrafted(key, crafted);
                requested = requested.subtract(crafted);
            }
            if (requested.isZero()) continue;
            if (network.emittable().contains(key)) {
                addCounter(state.emitted, key, requested);
                state.provenance.allocatePending(key, key, MaterialSource.Emitted.INSTANCE, requested);
                continue;
            }
            List<CompiledPattern> fast = network.fastProducersOf(key);
            if (stockLeaves.contains(key)) {
                // Reopen this dependency in a fresh pass instead of producing along an omitted edge.
                unsettled.add(key);
                continue;
            }
            if (fast.isEmpty()) {
                unsettled.add(key);
                continue;
            }
            int choice = workspace.candidateChoice().getOrDefault(key, 0);
            if (choice >= fast.size()) choice = fast.size() - 1;
            CompiledPattern pattern = fast.get(choice);
            state.selected.put(key, pattern);
            if (deferredPatterns.contains(pattern.details())) {
                state.deferredSuppliers.put(key, pattern.details());
                // The untouched demand becomes a required output of the owning cycle component.
                state.provenance.allocatePending(key, key,
                    new MaterialSource.PatternOutput(pattern.details(), true), requested);
                continue;
            }
            state.provenance.allocatePending(key, key, new MaterialSource.PatternOutput(pattern.details(), true), requested);
            PlannerAmount times = requested.ceilDiv(pattern.outputPerPattern());
            if (metrics != null) metrics.patternExpansions++;
            if (prefix != null) {
                expanded.set(prefix.size() - 1);
            }
            PlannerAmount oldTimes = state.patternTimes.getOrDefault(pattern.details(), PlannerAmount.ZERO);
            state.patternTimes.put(pattern.details(), oldTimes.add(times));
            state.bytes = state.bytes.add(times);
            SolveWorkspace.Expansion expansion = workspace.cachedExpansion(pattern, times, metrics);
            Map<AEKey, PlannerAmount> produced = expansion == null
                ? SolveWorkspace.outputAmounts(pattern, times) : expansion.outputs();
            for (var output : produced.entrySet()) {
                PlannerAmount available = output.getValue();
                if (output.getKey().equals(key)) available = available.subtract(requested);
                if (available.signum() > 0) state.creditCrafted(output.getKey(), pattern.details(), available);
            }
            specialResolver.resolve(pattern, times);
            for (int slot = 0; slot < pattern.inputs().size(); slot++) {
                CompiledInput input = pattern.inputs().get(slot);
                if (pattern.specialAnalysis().excludesFromCycleGraph(input)) continue;
                if (metrics != null) metrics.inputExpansions++;
                if (prefix != null) prefixInputs++;
                // Legacy semantic adapters may still express reusable stock without the special analyzer.
                PlannerAmount required = expansion == null
                    ? SolveWorkspace.inputAmount(input, times) : expansion.inputAmount(slot);
                MaterialDemand inputDemand = MaterialDemand.input(pattern.details(), slot, input.key(), required);
                state.provenance.register(inputDemand);
                if (input.ignoresComponents() && input.key() instanceof AEItemKey) {
                    PlannerAmount available = consumeStoredForInput(state, input.key(), required, true,
                        inputDemand, pattern.details(), input.source());
                    required = required.subtract(available);
                }
                PlannerAmount old = state.demand.getOrDefault(input.key(), PlannerAmount.ZERO);
                state.demand.put(input.key(), old.add(required));
                state.demandProducers.put(input.key(), pattern.details());
                state.parents.computeIfAbsent(input.key(), ignored -> new java.util.LinkedHashSet<>()).add(key);
                pending.add(input.key());
            }
            if (prefix != null && prefixInputs > SolveWorkspace.MAX_CHECKPOINT_INPUTS) {
                if (metrics != null) {
                    metrics.checkpointBoundary = "INPUT_LIMIT";
                    metrics.checkpointDecision = "INPUT_LIMIT";
                    metrics.checkpointPrefixSteps = prefix.size();
                    metrics.checkpointPrefixExpansions = expanded.cardinality();
                    metrics.checkpointPrefixInputs = prefixInputs;
                }
                prefix = null;
            }
        }
        // A leaf encountered before its source is not a material deficit yet. Allocate its credit
        // through the existing demand ledger so scheduling sees the exact physical supplier.
        for (AEKey key : unsettled) {
            cancellation.checkpoint();
            PlannerAmount requested = state.provenance.pendingAmount(key);
            PlannerAmount credited = requested.min(state.craftedAmount(key));
            if (credited.signum() > 0) state.consumeCrafted(key, credited);
            requested = requested.subtract(credited);
            if (requested.isZero()) continue;
            if (stockLeaves.contains(key) || !network.producersOf(key).isEmpty()) state.unsupported.add(key);
            else addCounter(state.missing, key, requested);
        }
        return state;
    }

    private static PlannerAmount consumeStoredForInput(SolveState state, AEKey key, PlannerAmount requested,
            boolean ignoreComponents, MaterialDemand demand, IPatternDetails consumer,
            IPatternDetails.IInput matchingInput) {
        if (requested.signum() <= 0) return PlannerAmount.ZERO;
        if (!ignoreComponents || !(key instanceof AEItemKey wanted)) {
            PlannerAmount exact = state.stored.available(key, requested);
            if (exact.signum() > 0) {
                state.stored.remove(key, exact);
                addCounter(state.used, key, exact);
                if (demand == null) state.provenance.allocatePending(key, key, MaterialSource.Stock.INSTANCE, exact);
                else state.provenance.allocate(demand, key, MaterialSource.Stock.INSTANCE, exact);
            }
            return exact;
        }
        PlannerAmount remaining = requested;
        PlannerAmount consumed = PlannerAmount.ZERO;
        for (AEKey storedKey : state.stored.keysSnapshot()) {
            if (remaining.isZero() || !(storedKey instanceof AEItemKey candidate)
                    || candidate.getItem() != wanted.getItem()) continue;
            if (!cn.dancingsnow.neoecoae.crafting.ECOProcessingComponentMatching
                    .isValid(consumer, matchingInput, key, storedKey, null)) continue;
            PlannerAmount take = state.stored.available(storedKey, remaining);
            if (take.signum() <= 0) continue;
            state.stored.remove(storedKey, take);
            addCounter(state.used, storedKey, take);
            if (demand == null) state.provenance.allocatePending(key, storedKey, MaterialSource.Stock.INSTANCE, take);
            else state.provenance.allocate(demand, storedKey, MaterialSource.Stock.INSTANCE, take);
            consumed = consumed.add(take);
            remaining = remaining.subtract(take);
        }
        return consumed;
    }

    private static void addCounter(PlannerCounter counter, AEKey key, PlannerAmount amount) {
        counter.add(key, amount);
    }

    private static void addTrace(CompiledNetwork network, SolveState state, PlannerAmount goal, ECOPlanTrace trace) {
        trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.GOAL, network.goal(), null, traceLong(goal), 0, traceLong(goal),
            traceLong(state.missing.get(network.goal())), 0, PlanTraceNode.Selection.NOT_APPLICABLE, null)
            .withExact(goal.toBigInteger(), PlannerAmount.ZERO.toBigInteger(), goal.toBigInteger(),
                state.missing.get(network.goal()).toBigInteger(), PlannerAmount.ZERO.toBigInteger()));
        addMaterialTrace(state.demand, state.usedAmounts(), state.missingAmounts(), trace);
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

    /** The same material arithmetic also describes external DAGs that cannot be committed for execution. */
    static void addMaterialTrace(Map<AEKey, PlannerAmount> demands, Map<AEKey, PlannerAmount> inventory,
            Map<AEKey, PlannerAmount> deficits, ECOPlanTrace trace) {
        for (var demand : demands.entrySet()) {
            PlannerAmount used = inventory.getOrDefault(demand.getKey(), PlannerAmount.ZERO);
            PlannerAmount missing = deficits.getOrDefault(demand.getKey(), PlannerAmount.ZERO);
            PlannerAmount requested = demand.getValue();
            PlannerAmount toCraft = requested.subtract(used).subtract(missing).max(PlannerAmount.ZERO);
            trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.MATERIAL, demand.getKey(), null, traceLong(requested),
                traceLong(used), traceLong(toCraft), traceLong(missing), 0,
                PlanTraceNode.Selection.NOT_APPLICABLE, null)
                .withExact(requested.toBigInteger(), used.toBigInteger(), toCraft.toBigInteger(),
                    missing.toBigInteger(), java.math.BigInteger.ZERO));
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
