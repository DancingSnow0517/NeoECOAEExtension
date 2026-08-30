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
import java.util.LinkedHashMap;
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
                if (addExecutionRepresentabilityDiagnostics(state, trace)) {
                    return new Outcome(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, state, trace);
                }
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.FAST_DAG, "Acyclic aggregated solve"));
                return new Outcome(PlanningStatus.SUCCESS, state, trace);
            }
            if (!candidates.advanceAfterFailure(network, state, choices)) {
                addTrace(network, state, amount, trace);
                if (addExecutionRepresentabilityDiagnostics(state, trace)) {
                    return new Outcome(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, state, trace);
                }
                trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.MISSING, "Required inputs are unavailable"));
                return new Outcome(PlanningStatus.MISSING_ITEMS, state, trace);
            }
            for (var rejected : state.selected.entrySet()) {
                trace.addNode(new PlanTraceNode(PlanTraceNode.Kind.PATTERN, rejected.getKey(),
                    rejected.getValue().details(), 0, 0, 0, 0,
                    traceLong(state.patternTimes.getOrDefault(rejected.getValue().details(), PlannerAmount.ZERO)),
                    PlanTraceNode.Selection.REJECTED, "DOWNSTREAM_MISSING_ROLLBACK"));
            }
            trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CANDIDATE_REJECTED,
                "Candidate failed downstream; rolled back attempt " + (attempt + 1)));
        }
        trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.NATIVE_FALLBACK, "Candidate retry budget exhausted"));
        return new Outcome(PlanningStatus.PARTIAL_UNSUPPORTED, state, trace);
    }

    private static SolveState runOnce(CompiledNetwork network, AcyclicRoutePlan route, SolveWorkspace workspace,
            long amount, Set<IPatternDetails> deferredPatterns, ECOCancellation cancellation)
            throws InterruptedException {
        SolveState state = new SolveState(workspace.inventory());
        state.stored.set(network.goal(), PlannerAmount.ZERO); // AE2 ignores stored final output during planning.
        state.demand.put(network.goal(), PlannerAmount.of(amount));
        state.bytes = PlannerAmount.of(route.keys().size()).multiply(8L);
        for (AEKey key : route.keys()) {
            cancellation.checkpoint();
            PlannerAmount requested = state.demand.getOrDefault(key, PlannerAmount.ZERO);
            if (requested.signum() <= 0) continue;
            state.bytes = state.bytes.add(PlannerAmount.stackBytes(requested, key.getAmountPerByte()));
            PlannerAmount stored = requested.min(state.stored.get(key));
            if (stored.signum() > 0) {
                state.stored.remove(key, stored);
                addCounter(state.used, key, stored);
                requested = requested.subtract(stored);
            }
            PlannerAmount crafted = requested.min(state.crafted.get(key));
            if (crafted.signum() > 0) {
                state.crafted.remove(key, crafted);
                requested = requested.subtract(crafted);
            }
            if (requested.isZero()) continue;
            if (network.emittable().contains(key)) {
                addCounter(state.emitted, key, requested);
                continue;
            }
            List<CompiledPattern> fast = network.producersOf(key).stream().filter(CompiledPattern::fastSupported).toList();
            if (fast.isEmpty()) {
                if (network.producersOf(key).isEmpty()) addCounter(state.missing, key, requested);
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
            PlannerAmount times = requested.ceilDiv(pattern.outputPerPattern());
            PlannerAmount oldTimes = state.patternTimes.getOrDefault(pattern.details(), PlannerAmount.ZERO);
            state.patternTimes.put(pattern.details(), oldTimes.add(times));
            state.bytes = state.bytes.add(times);
            Map<AEKey, PlannerAmount> produced = new LinkedHashMap<>();
            for (var output : pattern.outputs()) {
                PlannerAmount total = PlannerAmount.of(output.amount()).multiply(times);
                produced.put(output.what(), produced.getOrDefault(output.what(), PlannerAmount.ZERO).add(total));
            }
            for (var output : produced.entrySet()) {
                PlannerAmount available = output.getValue();
                if (output.getKey().equals(key)) available = available.subtract(requested);
                if (available.signum() > 0) addCounter(state.crafted, output.getKey(), available);
            }
            for (CompiledInput input : pattern.inputs()) {
                PlannerAmount required = input.amountPerPattern().multiply(times);
                PlannerAmount old = state.demand.getOrDefault(input.key(), PlannerAmount.ZERO);
                state.demand.put(input.key(), old.add(required));
                state.demandProducers.put(input.key(), pattern.details());
                state.parents.computeIfAbsent(input.key(), ignored -> new java.util.LinkedHashSet<>()).add(key);
            }
        }
        return state;
    }

    private static void addCounter(PlannerCounter counter, AEKey key, PlannerAmount amount) {
        counter.add(key, amount);
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
