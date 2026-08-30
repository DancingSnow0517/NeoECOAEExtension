package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlanTraceEdge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlanTraceNode;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlannerDiagnostic;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AcyclicCraftingSolver {
    public record Outcome(PlanningStatus status, SolveState state, ECOPlanTrace trace) {}
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
        ECOPlanTrace trace = new ECOPlanTrace();
        if (amount <= 0) {
            trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.AMOUNT_OVERFLOW, "Goal amount must be positive"));
            return new Outcome(PlanningStatus.AMOUNT_OVERFLOW, new SolveState(inventory), trace);
        }
        Map<AEKey, Integer> choices = new HashMap<>(initialChoices);
        int retryBudget = Math.max(1, network.reachablePatternCount() + 1);
        SolveState state = null;
        try {
            for (int attempt = 0; attempt < retryBudget; attempt++) {
                cancellation.checkpoint();
                state = runOnce(network, route, new SolveWorkspace(inventory, choices), amount,
                    deferredPatterns, cancellation);
                if (!state.unsupported.isEmpty()) {
                    addTrace(network, state, amount, trace);
                    trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.NATIVE_FALLBACK,
                        "No batch-safe candidate remains for " + state.unsupported));
                    return new Outcome(PlanningStatus.PARTIAL_UNSUPPORTED, state, trace);
                }
                if (state.missing.isEmpty()) {
                    addTrace(network, state, amount, trace);
                    trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.FAST_DAG, "Acyclic aggregated solve"));
                    return new Outcome(PlanningStatus.SUCCESS, state, trace);
                }
                if (!candidates.advanceAfterFailure(network, state, choices)) {
                    addTrace(network, state, amount, trace);
                    trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.MISSING, "Required inputs are unavailable"));
                    return new Outcome(PlanningStatus.MISSING_ITEMS, state, trace);
                }
                for (var rejected : state.selected.entrySet()) {
                    trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, rejected.getKey(),
                        rejected.getValue().details(), 0, 0, 0, 0,
                        state.patternTimes.getOrDefault(rejected.getValue().details(), 0L),
                        PlanTraceNode.Selection.REJECTED, "DOWNSTREAM_MISSING_ROLLBACK"));
                }
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CANDIDATE_REJECTED,
                    "Candidate failed downstream; rolled back attempt " + (attempt + 1)));
            }
            trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.NATIVE_FALLBACK, "Candidate retry budget exhausted"));
            return new Outcome(PlanningStatus.PARTIAL_UNSUPPORTED, state, trace);
        } catch (AmountOverflowException e) {
            trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.AMOUNT_OVERFLOW, e.getMessage()));
            return new Outcome(PlanningStatus.AMOUNT_OVERFLOW, state == null ? new SolveState(inventory) : state, trace);
        }
    }

    private static SolveState runOnce(CompiledNetwork network, AcyclicRoutePlan route, SolveWorkspace workspace,
            long amount, Set<IPatternDetails> deferredPatterns, ECOCancellation cancellation)
            throws InterruptedException, AmountOverflowException {
        SolveState state = new SolveState(workspace.inventory());
        state.stored.set(network.goal(), 0); // AE2 ignores stored final output during planning.
        state.demand.put(network.goal(), amount);
        state.bytes = CheckedAmounts.multiply(route.keys().size(), 8L, "route node bytes");
        for (AEKey key : route.keys()) {
            cancellation.checkpoint();
            long requested = state.demand.getOrDefault(key, 0L);
            if (requested <= 0) continue;
            state.bytes = CheckedAmounts.add(state.bytes,
                CheckedAmounts.stackBytes(requested, key.getAmountPerByte()), "stack bytes");
            long stored = Math.min(requested, state.stored.get(key));
            if (stored > 0) {
                state.stored.remove(key, stored);
                addCounter(state.used, key, stored, "used items");
                requested -= stored;
            }
            long crafted = Math.min(requested, state.crafted.get(key));
            if (crafted > 0) {
                state.crafted.remove(key, crafted);
                requested -= crafted;
            }
            if (requested == 0) continue;
            if (network.emittable().contains(key)) {
                addCounter(state.emitted, key, requested, "emitted items");
                continue;
            }
            List<CompiledPattern> fast = network.producersOf(key).stream().filter(CompiledPattern::fastSupported).toList();
            if (fast.isEmpty()) {
                if (network.producersOf(key).isEmpty()) addCounter(state.missing, key, requested, "missing items");
                else state.unsupported.add(key);
                continue;
            }
            int choice = workspace.candidateChoice().getOrDefault(key, 0);
            if (choice >= fast.size()) choice = fast.size() - 1;
            CompiledPattern pattern = fast.get(choice);
            state.selected.put(key, pattern);
            if (deferredPatterns.contains(pattern.details())) {
                // The untouched demand becomes a required output of the owning cycle component.
                continue;
            }
            long times = CheckedAmounts.ceilDiv(requested, pattern.outputPerPattern());
            long oldTimes = state.patternTimes.getOrDefault(pattern.details(), 0L);
            state.patternTimes.put(pattern.details(), CheckedAmounts.add(oldTimes, times, "patternTimes"));
            state.bytes = CheckedAmounts.add(state.bytes, times, "bytes");
            Map<AEKey, Long> produced = new java.util.LinkedHashMap<>();
            for (var output : pattern.outputs()) {
                long total = CheckedAmounts.multiply(output.amount(), times, "pattern output");
                produced.put(output.what(), CheckedAmounts.add(produced.getOrDefault(output.what(), 0L), total,
                    "combined pattern output"));
            }
            for (var output : produced.entrySet()) {
                long available = output.getValue();
                if (output.getKey().equals(key)) available -= requested;
                if (available > 0) addCounter(state.crafted, output.getKey(), available, "crafted inventory");
            }
            for (CompiledInput input : pattern.inputs()) {
                long required = CheckedAmounts.multiply(input.amountPerPattern(), times, "input demand");
                long old = state.demand.getOrDefault(input.key(), 0L);
                state.demand.put(input.key(), CheckedAmounts.add(old, required, "demand aggregation"));
                state.parents.computeIfAbsent(input.key(), ignored -> new java.util.LinkedHashSet<>()).add(key);
            }
        }
        return state;
    }

    private static void addCounter(KeyCounter counter, AEKey key, long amount, String operation)
            throws AmountOverflowException {
        counter.set(key, CheckedAmounts.add(counter.get(key), amount, operation));
    }

    private static void addTrace(CompiledNetwork network, SolveState state, long goalAmount, ECOPlanTrace trace) {
        trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.GOAL, network.goal(), null, goalAmount, 0, goalAmount,
            state.missing.get(network.goal()), 0, PlanTraceNode.Selection.NOT_APPLICABLE, null));
        for (var demand : state.demand.entrySet()) {
            long used = state.used.get(demand.getKey());
            long missing = state.missing.get(demand.getKey());
            trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.MATERIAL, demand.getKey(), null, demand.getValue(), used,
                Math.max(0, demand.getValue() - used - missing), missing, 0,
                PlanTraceNode.Selection.NOT_APPLICABLE, null));
        }
        for (var selected : state.selected.entrySet()) {
            CompiledPattern pattern = selected.getValue();
            long times = state.patternTimes.getOrDefault(pattern.details(), 0L);
            trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, selected.getKey(), pattern.details(), 0, 0, 0,
                0, times, PlanTraceNode.Selection.SELECTED, null));
            for (CompiledInput input : pattern.inputs()) {
                try {
                    trace.addEdge(new PlanTraceEdge(selected.getKey(), input.key(),
                        CheckedAmounts.multiply(input.amountPerPattern(), times, "trace edge")));
                } catch (AmountOverflowException ignored) {
                    // The solver already rejects this; explanation data must never break calculation.
                }
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
}
