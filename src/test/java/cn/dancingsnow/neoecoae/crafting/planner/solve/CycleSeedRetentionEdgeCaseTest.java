package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveDiagnostic;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveMetrics;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Corner cases of seed retention and supply feedback; the main scenarios live in CycleSeedRetentionPlanningTest. */
class CycleSeedRetentionEdgeCaseTest {
    private final AEKey a = key("A"), b = key("B"), f = key("F"), z = key("Z");
    private final AEKey k = key("K"), t = key("T");

    // P1: A + F -> 2B, P2: B -> A. B is the goal; F is a boundary input.
    private final CompiledPattern p1 = fastPattern(0, b, 2, new GenericStack(a, 1), new GenericStack(f, 1));
    private final CompiledPattern p2 = fastPattern(1, a, 1, new GenericStack(b, 1));
    // Outside the cycle: Z -> F.
    private final CompiledPattern makeF = fastPattern(2, f, 1, new GenericStack(z, 1));

    @Test
    void fullyReturnedCatalystIsRetainedWithoutExtraFirings() throws Exception {
        // K + F -> T + K. T is listed first, so it is the pattern's primary output and the dependency anchor:
        // the sibling output K gets an edge K -> T next to the input edge T -> K, which is a two-key SCC {T, K}.
        var outputs = List.of(new GenericStack(t, 1), new GenericStack(k, 1));
        var details = details(outputs);
        var make = new CompiledPattern(3, details, t, PlannerAmount.ONE, List.of(input(k, 1), input(f, 1)), outputs,
            true, null, false, semantics(details, outputs));
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(t, List.of(make));
        producers.put(k, List.of(make));
        producers.put(f, List.of());
        var outcome = plan(new CompiledNetwork(t, producers, Set.of(), 1, 3), inventory(k, 1, f, 5), 5);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertEquals(Map.of(make.details(), 5L), outcome.state().patternTimes());
        assertEquals(1L, outcome.state().usedItems().get(k), describe(outcome));
        assertEquals(5L, outcome.state().usedItems().get(f));
        assertHasDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);
        assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SEED_NOT_RETAINED);
        var end = replay(cycle(outcome).cycleResult(), counts(k, 1, f, 5));
        assertTrue(end.get(k) >= 1L && end.get(t) >= 5L, end.toString());
    }

    @Test
    void productThatIsAlsoTheSeedIsNotCountedTwice() throws Exception {
        // The goal target is 2 requested + 1 stocked = 3. Keeping the stocked B on top of that (B >= 4) would need
        // three P1 firings with F = 2 and fall back to the plain plan.
        var outcome = planSeedCycle(inventory(a, 1, b, 1, f, 2), 2);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertEquals(Map.of(p1.details(), 2L, p2.details(), 2L), outcome.state().patternTimes());
        assertHasDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);
        assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SEED_NOT_RETAINED);
        var end = replay(cycle(outcome).cycleResult(), counts(a, 1, b, 1, f, 2));
        assertTrue(end.get(a) >= 1L && end.get(b) >= 3L, end.toString());
    }

    @Test
    void alternativeStartingSeedIsTheWitnessOwnSeed() throws Exception {
        // Only B can start the loop (A = 0), so the witness starts from B. Strict retention keeps that very state;
        // an alternative restart state such as A instead of B is not modelled.
        var outcome = planSeedCycle(inventory(b, 1, f, 1), 1);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertEquals(Map.of(p1.details(), 1L, p2.details(), 1L), outcome.state().patternTimes());
        var seed = cycle(outcome).cycleResult().requiredSeed();
        assertEquals(1L, seed.getOrDefault(b, 0L), seed.toString());
        assertEquals(0L, seed.getOrDefault(a, 0L), seed.toString());
        var retained = outcome.trace().diagnostics().stream()
            .filter(d -> d.code() == PlannerDiagnostic.Code.CYCLE_SEED_RETAINED).findFirst();
        assertTrue(retained.isPresent(), describe(outcome));
        assertTrue(retained.get().message().contains("B=1") && !retained.get().message().contains("A="),
            retained.get().message());
        var end = replay(cycle(outcome).cycleResult(), counts(b, 1, f, 1));
        assertTrue(end.get(b) >= 2L, end.toString());
    }

    @Test
    void incompleteSupplySearchIsNotReportedAsExhausted() throws Exception {
        // The capped re-solve ends UNKNOWN_BUDGET, so no proof that every alternative vector fails exists.
        CycleSolver solver = (request, cancellation) -> request.boundaryImportCaps().isEmpty()
            ? new CycleSolveResult(CycleSolveStatus.SUCCESS, Map.of(p1.details(), 2L, p2.details(), 2L),
                Map.of(f, 2L), Map.of(), Map.of(), Map.of(a, 2L, b, 4L), Map.of(b, 2L), List.of(), List.of(),
                List.of(), CycleSolveMetrics.NONE)
            : CycleSolveResult.failure(CycleSolveStatus.UNKNOWN_BUDGET,
                CycleSolveDiagnostic.Code.STATE_BUDGET_EXHAUSTED, "test");
        var outcome = planBoundary(solver, inventory(a, 1), 2);
        assertNotEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertEquals(2L, outcome.state().missingItems().get(z), describe(outcome));
        assertHasDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SUPPLY_SEARCH_INCOMPLETE);
        assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SUPPLY_SEARCH_EXHAUSTED);
        assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SUPPLY_ALTERNATIVE);
    }

    @Test
    void unsupportedExternalVerdictSkipsTheFeedbackSearch() throws Exception {
        // The external DAG solver neither succeeds nor reports missing leaves: the verdict is UNSUPPORTED, which is
        // not a supply failure that import caps could steer around.
        var acyclic = spy(new AcyclicCraftingSolver());
        var unknown = new AcyclicCraftingSolver.Outcome(PlanningStatus.CYCLE_UNRESOLVED,
            new SolveState(new KeyCounter()), new ECOPlanTrace());
        doReturn(unknown).when(acyclic).solveDemands(any(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any());
        var outcome = planBoundary(acyclic, new BoundedCycleSolver(), inventory(a, 1), 2);
        assertNotEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        verify(acyclic, atLeastOnce()).solveDemands(any(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any());
        assertHasDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_EXTERNAL_DEMAND_UNSUPPORTED);
        assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SUPPLY_ALTERNATIVE);
        assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SUPPLY_SEARCH_EXHAUSTED);
        assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SUPPLY_SEARCH_INCOMPLETE);
    }

    @Test
    void exhaustedRetentionSliceFallsBackToThePlainPlan() throws Exception {
        var delegate = new BoundedCycleSolver();
        CycleSolver solver = (request, cancellation) -> {
            if (request.plannerRequiredOutputs().getOrDefault(a, PlannerAmount.ZERO).signum() > 0) {
                while (true) cancellation.checkpoint();
            }
            return delegate.solve(request, cancellation);
        };
        var outcome = planSeedCycle(solver, inventory(a, 1, f, 2), 2);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertEquals(Map.of(p1.details(), 1L), outcome.state().patternTimes());
        var notRetained = outcome.trace().diagnostics().stream()
            .filter(d -> d.code() == PlannerDiagnostic.Code.CYCLE_SEED_NOT_RETAINED).findFirst();
        assertTrue(notRetained.isPresent(), describe(outcome));
        assertTrue(notRetained.get().message().contains("budget slice"), notRetained.get().message());
        assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);
    }

    @Test
    void cycleBudgetExhaustionStaysUnknown() throws Exception {
        CycleSolver solver = (request, cancellation) -> CycleSolveResult.failure(CycleSolveStatus.UNKNOWN_BUDGET,
            CycleSolveDiagnostic.Code.STATE_BUDGET_EXHAUSTED, "test");
        var outcome = planSeedCycle(solver, inventory(a, 1, f, 2), 2);
        assertEquals(PlanningStatus.CYCLE_UNRESOLVED, outcome.status(), describe(outcome));
        assertTrue(outcome.state().missingItems().isEmpty(), describe(outcome));
        assertHasDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_BUDGET_EXHAUSTED);
        assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);
        assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SEED_NOT_RETAINED);
    }

    private ComponentPlanner.Outcome planSeedCycle(KeyCounter inventory, long amount) throws Exception {
        return planSeedCycle(new BoundedCycleSolver(), inventory, amount);
    }

    private ComponentPlanner.Outcome planSeedCycle(CycleSolver solver, KeyCounter inventory, long amount)
            throws Exception {
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(b, List.of(p1));
        producers.put(a, List.of(p2));
        producers.put(f, List.of());
        return plan(new AcyclicCraftingSolver(), solver, new CompiledNetwork(b, producers, Set.of(), 2, 3),
            inventory, amount);
    }

    /** The seed cycle with F produced outside it by Z -> F; Z has no producer. */
    private ComponentPlanner.Outcome planBoundary(CycleSolver solver, KeyCounter inventory, long amount)
            throws Exception {
        return planBoundary(new AcyclicCraftingSolver(), solver, inventory, amount);
    }

    private ComponentPlanner.Outcome planBoundary(AcyclicCraftingSolver acyclic, CycleSolver solver,
            KeyCounter inventory, long amount) throws Exception {
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(b, List.of(p1));
        producers.put(a, List.of(p2));
        producers.put(f, List.of(makeF));
        producers.put(z, List.of());
        return plan(acyclic, solver, new CompiledNetwork(b, producers, Set.of(), 3, 4), inventory, amount);
    }

    private static ComponentPlanner.Outcome plan(CompiledNetwork network, KeyCounter inventory, long amount)
            throws Exception {
        return plan(new AcyclicCraftingSolver(), new BoundedCycleSolver(), network, inventory, amount);
    }

    private static ComponentPlanner.Outcome plan(AcyclicCraftingSolver acyclic, CycleSolver solver,
            CompiledNetwork network, KeyCounter inventory, long amount) throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        return new ComponentPlanner(acyclic, solver)
            .plan(network, condensation, inventory, amount, true, ECOCancellation.NONE);
    }

    private static ComponentPlanningResult cycle(ComponentPlanner.Outcome outcome) {
        return outcome.components().stream().filter(c -> c.type() == ComponentPlanningResult.Type.CYCLIC)
            .findFirst().orElseThrow();
    }

    /** Replays the compact plan with the same circuit semantics as the witness expansion; no key may go negative. */
    private static Map<AEKey, Long> replay(CycleSolveResult result, Map<AEKey, Long> start) {
        Map<AEKey, Long> held = new LinkedHashMap<>(start);
        List<PatternRun> runs = result.executionPlan();
        long[] laps = new long[runs.size()];
        for (int i = 0; i < laps.length; i++) laps[i] = runs.get(i).repetitions();
        for (int index = 0; index < runs.size();) {
            PatternRun run = runs.get(index);
            for (long n = 0; n < run.count(); n++) {
                for (CompiledInput input : run.pattern().inputs()) {
                    long left = held.merge(input.key(), -input.amountPerPattern().longValueExact(), Long::sum);
                    assertTrue(left >= 0L, "Replay went negative on " + input.key() + " in " + held);
                }
                for (GenericStack output : run.pattern().grossOutputs()) held.merge(output.what(), output.amount(), Long::sum);
            }
            if (run.repetitions() > 1 && --laps[index] > 0) index -= run.repeatWidth() - 1;
            else index++;
        }
        return held;
    }

    private static Map<AEKey, Long> counts(Object... keyAmountPairs) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < keyAmountPairs.length; i += 2) {
            result.put((AEKey) keyAmountPairs[i], ((Number) keyAmountPairs[i + 1]).longValue());
        }
        return result;
    }

    private static Map<AEKey, Long> counts(KeyCounter counter) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (var entry : counter) result.put(entry.getKey(), entry.getLongValue());
        return result;
    }

    private static KeyCounter inventory(Object... keyAmountPairs) {
        var inventory = new KeyCounter();
        for (int i = 0; i < keyAmountPairs.length; i += 2) {
            inventory.add((AEKey) keyAmountPairs[i], ((Number) keyAmountPairs[i + 1]).longValue());
        }
        return inventory;
    }

    private static void assertHasDiagnostic(ComponentPlanner.Outcome outcome, PlannerDiagnostic.Code code) {
        assertTrue(outcome.trace().diagnostics().stream().anyMatch(d -> d.code() == code), describe(outcome));
    }

    private static void assertNoDiagnostic(ComponentPlanner.Outcome outcome, PlannerDiagnostic.Code code) {
        assertTrue(outcome.trace().diagnostics().stream().noneMatch(d -> d.code() == code), describe(outcome));
    }

    private static String describe(ComponentPlanner.Outcome outcome) {
        return outcome.status() + " missing=" + counts(outcome.state().missingItems()) + " used="
            + counts(outcome.state().usedItems()) + " times=" + outcome.state().patternTimes() + " "
            + outcome.trace().diagnostics().stream().map(d -> d.code() + ": " + d.message()).toList();
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledInput input(AEKey key, long amount) {
        return new CompiledInput(null, key, amount, true, null);
    }

    private static IPatternDetails details(List<GenericStack> outputs) {
        var details = mock(IPatternDetails.class);
        when(details.getOutputs()).thenReturn(outputs);
        return details;
    }

    private static PatternSemantics semantics(IPatternDetails details, List<GenericStack> outputs) {
        return new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
    }

    private static CompiledPattern fastPattern(int id, AEKey output, long amount, GenericStack... inputs) {
        var outputs = List.of(new GenericStack(output, amount));
        var details = details(outputs);
        return new CompiledPattern(id, details, output, PlannerAmount.of(amount),
            java.util.Arrays.stream(inputs).map(i -> input(i.what(), i.amount())).toList(),
            outputs, true, null, false, semantics(details, outputs));
    }
}
