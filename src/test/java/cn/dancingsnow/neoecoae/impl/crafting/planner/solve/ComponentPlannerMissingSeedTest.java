package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.menu.me.crafting.CraftingPlanSummary;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.*;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.*;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.*;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.impl.crafting.planner.semantic.PatternSemantics;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ComponentPlannerMissingSeedTest {
    @Test
    void unproducibleInternalSeedReachesAe2MissingSummary() throws Exception {
        AEKey seed = mock(AEKey.class);
        when(seed.getAmountPerByte()).thenReturn(8);
        IPatternDetails details = mock(IPatternDetails.class);
        var output = new GenericStack(seed, 2L);
        when(details.getOutputs()).thenReturn(List.of(output));
        var semantics = new PatternSemantics(details, null, List.of(), List.of(output), List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        var pattern = new CompiledPattern(0, details, seed, PlannerAmount.of(2),
            List.of(new CompiledInput(null, seed, 1L, true, null)), List.of(output), true, null, false, semantics);
        var network = new CompiledNetwork(seed, Map.of(seed, List.of(pattern)), Set.of(), 1, 1);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var shortage = new CycleSolveResult(CycleSolveStatus.INSUFFICIENT_EXTERNAL_INPUT,
            Map.of(), Map.of(), Map.of(seed, 1L), Map.of(seed, 1L), Map.of(), Map.of(),
            List.of(), List.of(), CycleSolveMetrics.NONE);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), (request, cancellation) -> shortage);

        var outcome = planner.plan(network, condensation, new KeyCounter(), 100L, true, ECOCancellation.NONE);

        assertEquals(1L, outcome.state().missingItems().get(seed));
        assertEquals(Map.of(seed, 1L), outcome.trace().cycles().getFirst().solveResult().seedShortfall());
        assertEquals(CycleExecutionDisposition.BLOCKED, outcome.components().getFirst().cycleDisposition());
        assertTrue(outcome.state().patternTimes().isEmpty(), "Failed cycle must remain uncommitted");
        var plan = new AE2CraftingPlanBridge().partial(seed, 100L, false, outcome.state());
        var grid = mock(IGrid.class, RETURNS_DEEP_STUBS);
        var summary = CraftingPlanSummary.fromJob(grid, mock(IActionSource.class), plan);
        assertTrue(summary.isSimulation());
        assertEquals(1, summary.getEntries().size());
        assertEquals(seed, summary.getEntries().getFirst().getWhat());
        assertEquals(1L, summary.getEntries().getFirst().getMissingAmount());
    }

    @Test
    void seededGrowthReportsEveryExternalDeficit() throws Exception {
        AEKey seed = mock(AEKey.class);
        AEKey diamond = mock(AEKey.class);
        AEKey netherrack = mock(AEKey.class);
        for (AEKey key : List.of(seed, diamond, netherrack)) when(key.getAmountPerByte()).thenReturn(8);
        IPatternDetails details = mock(IPatternDetails.class);
        var output = new GenericStack(seed, 2L);
        when(details.getOutputs()).thenReturn(List.of(output));
        var semantics = new PatternSemantics(details, null, List.of(), List.of(output), List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        var pattern = new CompiledPattern(0, details, seed, PlannerAmount.of(2),
            List.of(new CompiledInput(null, seed, 1L, true, null),
                new CompiledInput(null, diamond, 7L, true, null),
                new CompiledInput(null, netherrack, 1L, true, null)),
            List.of(output), true, null, false, semantics);
        var network = new CompiledNetwork(seed, Map.of(seed, List.of(pattern), diamond, List.of(), netherrack, List.of()), Set.of(), 1, 3);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver());
        for (long storedDiamonds : List.of(0L, 10L)) {
            KeyCounter inventory = new KeyCounter();
            inventory.add(seed, 1L);
            inventory.add(diamond, storedDiamonds);
            var outcome = planner.plan(network, condensation, inventory, 4L, true, ECOCancellation.NONE);
            assertEquals(28L - storedDiamonds, outcome.state().missingItems().get(diamond), outcome.trace().diagnostics().toString());
            assertEquals(4L, outcome.state().missingItems().get(netherrack));
            assertEquals(0L, outcome.state().missingItems().get(seed));
            assertTrue(outcome.state().patternTimes().isEmpty());
            var plan = new AE2CraftingPlanBridge().partial(seed, 4L, false, outcome.state());
            var summary = CraftingPlanSummary.fromJob(mock(IGrid.class, RETURNS_DEEP_STUBS),
                mock(IActionSource.class), plan);
            assertTrue(summary.isSimulation());
            assertEquals(2L, summary.getEntries().stream().filter(entry -> entry.getMissingAmount() > 0).count());
            var result = new cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult(
                outcome.status(), plan, outcome.trace(), outcome.cycles(), outcome.components(),
                outcome.executionComponentOrder(), 0L);
            var snapshot = cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshotFactory.create(result);
            assertEquals(java.math.BigInteger.valueOf(28L - storedDiamonds), snapshot.nodes().stream()
                .filter(node -> node.key().equals(diamond)).findFirst().orElseThrow().missingBigInteger());
            assertEquals(java.math.BigInteger.valueOf(4L), snapshot.nodes().stream()
                .filter(node -> node.key().equals(netherrack)).findFirst().orElseThrow().missingBigInteger());
        }
    }
}
