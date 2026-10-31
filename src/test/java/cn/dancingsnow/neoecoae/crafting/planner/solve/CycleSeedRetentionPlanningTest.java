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
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CycleSeedRetentionPlanningTest {
    private final AEKey a = key("A"), b = key("B"), f = key("F"), g = key("G"), h = key("H");
    private final AEKey z = key("Z"), zf = key("Zf"), zg = key("Zg");

    // P1: A + F -> 2B, P2: B -> A. B is the goal; F is a boundary input without a producer.
    private final CompiledPattern p1 = fastPattern(0, b, 2, new GenericStack(a, 1), new GenericStack(f, 1));
    private final CompiledPattern p2 = fastPattern(1, a, 1, new GenericStack(b, 1));
    private final AEKey x = key("X");
    private final CompiledPattern px = fastPattern(2, x, 1, new GenericStack(a, 1));

    @Test
    void consumableSeedIsNotReplenishedEvenWhenFuelAllows() throws Exception {
        var outcome = planSeedCycle(inventory(a, 1, f, 2), 2);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertEquals(Map.of(p1.details(), 1L), outcome.state().patternTimes());
        assertEquals(1L, outcome.state().usedItems().get(f));
        assertEquals(1L, outcome.state().usedItems().get(a));
        assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);
        var end = replay(cycle(outcome).cycleResult(), counts(a, 1, f, 1));
        assertEquals(0L, end.get(a));
        assertEquals(2L, end.get(b));
    }

    // Parent X consumes A as an ordinary input; existing stock is spent before growing the shortfall.
    @Test
    void stockedInputsAreConsumedAndGrowthOnlyFillsTheShortfall() throws Exception {
        for (long stocked : new long[] {99L, 100L}) {
            var outcome = planParentOfSeed(inventory(a, stocked, f, 10_000), 100);
            assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
            assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);
            long laps = 100L - stocked;
            Map<IPatternDetails, Long> expected = new LinkedHashMap<>();
            expected.put(px.details(), 100L);
            if (laps > 0) {
                expected.put(p1.details(), laps);
                expected.put(p2.details(), 2L * laps);
            }
            assertEquals(expected, outcome.state().patternTimes(), describe(outcome));
            assertEquals(laps, outcome.state().usedItems().get(f));
            var end = replay(cycle(outcome).cycleResult(), counts(a, stocked, f, laps));
            assertEquals(100L, end.get(a), "Only the downstream demand must be supplied: " + end);
        }
    }

    @Test
    void exactStockWithoutFuelStillUsesTheStoredSeeds() throws Exception {
        var outcome = planParentOfSeed(inventory(a, 100), 100);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertEquals(Map.of(px.details(), 100L), outcome.state().patternTimes());
        assertEquals(100L, outcome.state().usedItems().get(a));
        assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);
    }

    @Test
    void stockBeyondTheDemandNeedsNoGrowth() throws Exception {
        var outcome = planParentOfSeed(inventory(a, 150, f, 10_000), 100);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertEquals(Map.of(px.details(), 100L), outcome.state().patternTimes());
        assertEquals(0L, outcome.state().usedItems().get(f));
    }

    @Test
    void concurrentOrdersReserveOnlyTheStockTheyActuallyConsume() throws Exception {
        var first = planSeedCycle(inventory(a, 64, f, 4000), 1000);
        assertEquals(PlanningStatus.SUCCESS, first.status(), describe(first));
        assertEquals(1L, first.state().usedItems().get(a));
        assertEquals(Map.of(p1.details(), 999L, p2.details(), 998L), first.state().patternTimes());
        assertNoDiagnostic(first, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);
        var second = planSeedCycle(inventory(a, 63, f, 3001), 1000);
        assertEquals(PlanningStatus.SUCCESS, second.status(), describe(second));
        assertEquals(1L, second.state().usedItems().get(a));
        assertEquals(first.state().patternTimes(), second.state().patternTimes());
        var end = replay(cycle(first).cycleResult(), counts(a, 1, f, 999));
        assertEquals(0L, end.get(a));
        assertEquals(1000L, end.get(b));
        assertEquals(0L, end.get(f));
    }

    @Test
    void optionalJointOptimizationKeepsThePlanWithoutSeedReplenishment() throws Exception {
        var network = new CompiledNetwork(b, Map.of(b, List.of(p1), a, List.of(p2), f, List.of()), Set.of(), 2, 3);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
                new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = inventory(a, 1, f, 2);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver());
        var result = planner.planWithCycleFallback(network, condensation,
                planner.selectRoutes(condensation, true, ECOCancellation.NONE), stock,
                PlannerInventorySnapshot.of(stock), 2, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status(), describe(result));
        assertEquals(Map.of(p1.details(), 1L), result.state().patternTimes());
        assertNoDiagnostic(result, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);
    }

    @Test
    void fallsBackToConsumingTheSeedWhenFuelIsShort() throws Exception {
        var outcome = planSeedCycle(inventory(a, 1, f, 1), 2);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), outcome.trace().diagnostics().toString());
        assertEquals(Map.of(p1.details(), 1L), outcome.state().patternTimes());
        assertHasDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SEED_NOT_RETAINED);
        assertNoDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);
    }

    @Test
    void consecutiveOrdersUseRemainingStockWithoutManufacturingReplacementSeeds() throws Exception {
        var first = planSeedCycle(inventory(a, 2, f, 4), 2);
        assertEquals(PlanningStatus.SUCCESS, first.status(), describe(first));
        Map<AEKey, Long> afterFirst = afterJob(first, counts(a, 2, f, 4), 2);
        assertEquals(counts(a, 1, b, 0, f, 3), afterFirst);
        var second = planSeedCycle(inventory(a, 1, f, 3), 2);
        assertEquals(PlanningStatus.SUCCESS, second.status(), describe(second));
        Map<AEKey, Long> afterSecond = afterJob(second, afterFirst, 2);
        assertEquals(counts(a, 0, b, 0, f, 2), afterSecond);
        var exhausted = planSeedCycle(inventory(f, 2), 2);
        assertNotEquals(PlanningStatus.SUCCESS, exhausted.status(), describe(exhausted));
        assertTrue(exhausted.state().missingItems().get(a) + exhausted.state().missingItems().get(b) > 0L);
    }

    @Test
    void supplyFeedbackSwitchesToTheSuppliableBoundaryInput() throws Exception {
        var outcome = planBranching(inventory(a, 3, zg, 4));
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        var times = outcome.state().patternTimes();
        assertFalse(times.containsKey(branchF.details()), times.toString());
        assertTrue(times.containsKey(branchG.details()), times.toString());
        assertEquals(0L, outcome.state().usedItems().get(zf));
        assertHasDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SUPPLY_ALTERNATIVE);
    }

    @Test
    void ownedBoundaryFuelCannotBeCountedAgainAsAnImportDuringFallback() throws Exception {
        var outcome = planBranching(inventory(a, 3, f, 1, zg, 4));
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertTrue(outcome.state().usedItems().get(f) <= 1L, describe(outcome));
        assertEquals(0L, outcome.state().usedItems().get(zf));
        assertTrue(outcome.state().patternTimes().containsKey(branchG.details()), describe(outcome));
    }

    @Test
    void sharedUpstreamIsNeverPromisedTwice() throws Exception {
        var outcome = planShared(inventory(a, 3, z, 1, h, 5), true);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), describe(outcome));
        assertTrue(outcome.state().usedItems().get(z) <= 1L, describe(outcome));
        assertTrue(outcome.state().patternTimes().containsKey(branchH.details()), describe(outcome));

        var starved = planShared(inventory(a, 3, z, 1), false);
        assertNotEquals(PlanningStatus.SUCCESS, starved.status(), describe(starved));
        assertTrue(starved.state().missingItems().get(z) > 0L, describe(starved));
        assertHasDiagnostic(starved, PlannerDiagnostic.Code.CYCLE_SUPPLY_SEARCH_EXHAUSTED);
        assertTrue(starved.state().patternTimes().isEmpty(), describe(starved));
        assertTrue(starved.state().usedItems().isEmpty(), describe(starved));
    }

    @Test
    void deterministicAcrossRepeatedPlanning() throws Exception {
        var first = planBranching(inventory(a, 3, zg, 4));
        for (int i = 0; i < 2; i++) {
            var again = planBranching(inventory(a, 3, zg, 4));
            assertEquals(first.state().patternTimes(), again.state().patternTimes());
            assertEquals(counts(first.state().usedItems()), counts(again.state().usedItems()));
            assertEquals(cycle(first).cycleResult().executionPlan(), cycle(again).cycleResult().executionPlan());
        }
    }

    // Test 4/5 networks. P3: B -> A is shared; F/G come from outside the cycle.
    private final CompiledPattern branchF = fastPattern(10, b, 2, new GenericStack(a, 1), new GenericStack(f, 1));
    private final CompiledPattern branchG = fastPattern(11, b, 2, new GenericStack(a, 1), new GenericStack(g, 1));
    private final CompiledPattern branchH = fastPattern(12, b, 2, new GenericStack(a, 1), new GenericStack(h, 1));
    private final CompiledPattern back = fastPattern(13, a, 1, new GenericStack(b, 1));
    private final CompiledPattern makeF = fastPattern(14, f, 1, new GenericStack(zf, 1));
    private final CompiledPattern makeG = fastPattern(15, g, 1, new GenericStack(zg, 1));
    private final CompiledPattern sharedF = fastPattern(16, f, 1, new GenericStack(z, 1));
    private final CompiledPattern sharedG = fastPattern(17, g, 1, new GenericStack(z, 1));

    @Test
    void retainedSeedDiagnosticDoesNotBlockACheaperExecutablePlan() throws Exception {
        var slow = fastPattern(30, b, 1, new GenericStack(a, 1));
        var fast = fastPattern(31, b, 2, new GenericStack(f, 1));
        var slowInput = mock(IPatternDetails.IInput.class);
        when(slowInput.getMultiplier()).thenReturn(1L);
        when(slowInput.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(a, 1)});
        when(slow.details().getInputs()).thenReturn(new IPatternDetails.IInput[] {slowInput});
        when(slow.details().getPrimaryOutput()).thenReturn(new GenericStack(b, 1));
        var fastInput = mock(IPatternDetails.IInput.class);
        when(fastInput.getMultiplier()).thenReturn(1L);
        when(fastInput.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(f, 1)});
        when(fast.details().getInputs()).thenReturn(new IPatternDetails.IInput[] {fastInput});
        when(fast.details().getPrimaryOutput()).thenReturn(new GenericStack(b, 2));
        var network = new CompiledNetwork(b, Map.of(b, List.of(slow, fast), a, List.of(), f, List.of()),
                Set.of(), 2, 3);
        var stock = inventory(a, 10, f, 10);
        var state = new SolveState(stock);
        state.patternTimes.put(slow.details(), PlannerAmount.of(10));
        var trace = new cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace();
        trace.addDiagnostic(new PlannerDiagnostic(PlannerDiagnostic.Code.CYCLE_SEED_RETAINED, "Seed returned"));
        var preferred = new ComponentPlanner.Outcome(PlanningStatus.SUCCESS, state, trace,
                List.of(), List.of(), List.of());
        var result = new JointRouteOptimizer().optimize(network, PlannerInventorySnapshot.of(stock),
                10, preferred, ECOCancellation.NONE);
        assertNotSame(preferred, result);
        assertEquals(Map.of(fast.details(), 5L), result.state().patternTimes());
        result.state().executionProvenance().requireComplete();
    }

    private ComponentPlanner.Outcome planBranching(KeyCounter inventory) throws Exception {
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(b, List.of(branchF, branchG));
        producers.put(a, List.of(back));
        producers.put(f, List.of(makeF));
        producers.put(g, List.of(makeG));
        producers.put(zf, List.of());
        producers.put(zg, List.of());
        return planAllProducers(new CompiledNetwork(b, producers, Set.of(), 6, 6), inventory, 4);
    }

    private ComponentPlanner.Outcome planShared(KeyCounter inventory, boolean withH) throws Exception {
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(b, withH ? List.of(branchF, branchG, branchH) : List.of(branchF, branchG));
        producers.put(a, List.of(back));
        producers.put(f, List.of(sharedF));
        producers.put(g, List.of(sharedG));
        producers.put(z, List.of());
        if (withH) producers.put(h, List.of());
        return planAllProducers(new CompiledNetwork(b, producers, Set.of(), withH ? 7 : 6, withH ? 7 : 6), inventory, 4);
    }

    private ComponentPlanner.Outcome planSeedCycle(KeyCounter inventory, long amount) throws Exception {
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(b, List.of(p1));
        producers.put(a, List.of(p2));
        producers.put(f, List.of());
        return plan(new CompiledNetwork(b, producers, Set.of(), 2, 3), inventory, amount);
    }

    private ComponentPlanner.Outcome planParentOfSeed(KeyCounter inventory, long amount) throws Exception {
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(x, List.of(px));
        producers.put(b, List.of(p1));
        producers.put(a, List.of(p2));
        producers.put(f, List.of());
        return plan(new CompiledNetwork(x, producers, Set.of(), 3, 4), inventory, amount);
    }

    /**
     * Route selection keeps one producer per key, so alternative firing vectors inside one SCC need a hand-built
     * selection that leaves every producer of the universe graph in the component.
     */
    private static ComponentPlanner.Outcome planAllProducers(CompiledNetwork network, KeyCounter inventory,
            long amount) throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var choices = new LinkedHashMap<AEKey, Integer>();
        graph.nodes().keySet().forEach(key -> choices.put(key, 0));
        var selection = new ActiveRouteSelector.Selection(false, choices, condensation, condensation.cycles(), List.of());
        return new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, selection, inventory, amount, true, ECOCancellation.NONE);
    }

    private static ComponentPlanner.Outcome plan(CompiledNetwork network, KeyCounter inventory, long amount)
            throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        return new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
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

    /** Start inventory minus what the plan took, plus what the cycle holds at the end, minus the delivered goal. */
    private Map<AEKey, Long> afterJob(ComponentPlanner.Outcome outcome, Map<AEKey, Long> start, long delivered) {
        Map<AEKey, Long> used = counts(outcome.state().usedItems());
        Map<AEKey, Long> finalMarking = replay(cycle(outcome).cycleResult(), used);
        Map<AEKey, Long> after = new LinkedHashMap<>(start);
        used.forEach((key, amount) -> after.merge(key, -amount, Long::sum));
        finalMarking.forEach((key, amount) -> after.merge(key, amount, Long::sum));
        after.merge(b, -delivered, Long::sum);
        return after;
    }

    private static Map<AEKey, Long> counts(KeyCounter counter) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (var entry : counter) result.put(entry.getKey(), entry.getLongValue());
        return result;
    }

    private static Map<AEKey, Long> counts(Object... keyAmountPairs) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < keyAmountPairs.length; i += 2) {
            result.put((AEKey) keyAmountPairs[i], ((Number) keyAmountPairs[i + 1]).longValue());
        }
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

    private static CompiledPattern fastPattern(int id, AEKey output, long amount, GenericStack... inputs) {
        var details = mock(IPatternDetails.class);
        var outputs = List.of(new GenericStack(output, amount));
        when(details.getOutputs()).thenReturn(outputs.toArray(GenericStack[]::new));
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, output, PlannerAmount.of(amount),
            java.util.Arrays.stream(inputs).map(i -> new CompiledInput(null, i.what(), i.amount(), true, null)).toList(),
            outputs, true, null, false, semantics);
    }
}
