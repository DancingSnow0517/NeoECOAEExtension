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

    @Test
    void keepsTheOnlySeedWhenFuelAllows() throws Exception {
        var outcome = planSeedCycle(inventory(a, 1, f, 2), 2);
        assertEquals(PlanningStatus.SUCCESS, outcome.status(), outcome.trace().diagnostics().toString());
        assertEquals(Map.of(p1.details(), 2L, p2.details(), 2L), outcome.state().patternTimes());
        assertEquals(2L, outcome.state().usedItems().get(f));
        assertEquals(1L, outcome.state().usedItems().get(a));
        assertHasDiagnostic(outcome, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);
        var end = replay(cycle(outcome).cycleResult(), counts(a, 1, f, 2));
        assertTrue(end.get(a) >= 1L && end.get(b) >= 2L, end.toString());
    }

    @Test
    void concurrentOrdersBorrowOneSeedEachAndReturnTheirOwnSeed() throws Exception {
        var first = planSeedCycle(inventory(a, 64, f, 4000), 1000);
        assertEquals(PlanningStatus.SUCCESS, first.status(), describe(first));
        assertEquals(1L, first.state().usedItems().get(a), "Do not reserve the other 63 seeds for parallelism");
        assertEquals(Map.of(p1.details(), 1000L, p2.details(), 1000L), first.state().patternTimes());
        assertHasDiagnostic(first, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);

        // Submit the second order while the first order's one seed and fuel are still held by its CPU.
        var second = planSeedCycle(inventory(a, 63, f, 3000), 1000);
        assertEquals(PlanningStatus.SUCCESS, second.status(), describe(second));
        assertEquals(1L, second.state().usedItems().get(a));
        assertEquals(Map.of(p1.details(), 1000L, p2.details(), 1000L), second.state().patternTimes());

        // Each CPU must execute from only what it reserved. The other network copies are not seed credit.
        var end = replay(cycle(first).cycleResult(), counts(a, 1, f, 1000));
        assertEquals(1L, end.get(a));
        assertEquals(1000L, end.get(b));
        assertEquals(0L, end.get(f));
    }

    @Test
    void optionalJointOptimizationCannotSpendTheSeedThatWasRetained() throws Exception {
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
        assertEquals(Map.of(p1.details(), 2L, p2.details(), 2L), result.state().patternTimes());
        assertHasDiagnostic(result, PlannerDiagnostic.Code.CYCLE_SEED_RETAINED);
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
    void twoConsecutiveOrdersRestartFromTheRetainedSeed() throws Exception {
        var first = planSeedCycle(inventory(a, 1, f, 2), 2);
        assertEquals(PlanningStatus.SUCCESS, first.status(), first.trace().diagnostics().toString());
        Map<AEKey, Long> afterFirst = afterJob(first, counts(a, 1, f, 2), 2);
        assertEquals(counts(a, 1, b, 0, f, 0), afterFirst);

        var noFuel = planSeedCycle(inventory(a, 1), 2);
        assertNotEquals(PlanningStatus.SUCCESS, noFuel.status(), noFuel.trace().diagnostics().toString());
        assertTrue(noFuel.state().missingItems().get(f) > 0L, "Fuel shortage expected: " + describe(noFuel));
        assertEquals(0L, noFuel.state().missingItems().get(a), "The retained seed must not be missing: " + describe(noFuel));

        var second = planSeedCycle(inventory(a, 1, f, 2), 2);
        assertEquals(PlanningStatus.SUCCESS, second.status(), second.trace().diagnostics().toString());
        assertEquals(first.state().patternTimes(), second.state().patternTimes());

        // Contrast: the fallback plan spends the only seed, so a later order with fuel cannot restart.
        var fallback = planSeedCycle(inventory(a, 1, f, 1), 2);
        assertEquals(PlanningStatus.SUCCESS, fallback.status(), fallback.trace().diagnostics().toString());
        Map<AEKey, Long> afterFallback = afterJob(fallback, counts(a, 1, f, 1), 2);
        assertEquals(counts(a, 0, b, 0, f, 0), afterFallback);
        var lost = planSeedCycle(inventory(f, 2), 2);
        assertNotEquals(PlanningStatus.SUCCESS, lost.status(), lost.trace().diagnostics().toString());
        long seedLoss = lost.state().missingItems().get(a) + lost.state().missingItems().get(b);
        assertTrue(seedLoss > 0L, "Seed loss expected: " + describe(lost));
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
