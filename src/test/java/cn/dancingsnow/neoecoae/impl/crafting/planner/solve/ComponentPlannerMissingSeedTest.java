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
    void committedCycleDoesNotBlockAnUnrepresentableParentOrder() throws Exception {
        AEKey product = mock(AEKey.class);
        AEKey seed = mock(AEKey.class);
        for (AEKey key : List.of(product, seed)) when(key.getAmountPerByte()).thenReturn(8);
        var consumer = staticPattern(0, product, 1L, seed, 4L);
        var growth = staticPattern(1, seed, 2L, seed, 1L);
        var network = new CompiledNetwork(product,
            Map.of(product, List.of(consumer), seed, List.of(growth)), Set.of(), 2, 3);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.add(seed, 1L);
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, stock, 2L, true, ECOCancellation.NONE);
        assertEquals(cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus.SUCCESS,
            outcome.status(), outcome.trace().diagnostics().toString());
        assertTrue(outcome.state().missingAmounts().isEmpty());
        assertTrue(outcome.state().plannerPatternTimes().get(growth.details()).signum() > 0);
        var cycle = outcome.components().stream()
            .filter(component -> component.type() == cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC)
            .findFirst().orElseThrow();
        assertNotEquals(CycleExecutionDisposition.BLOCKED, cycle.cycleDisposition());
        var result = new cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOPlanningResult(
            cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE,
            new AE2CraftingPlanBridge().unsupported(product, 2L), outcome.trace(),
            outcome.cycles(), outcome.components(), outcome.executionComponentOrder(), 0L);
        assertTrue(cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission.allows(result, false),
            "A committed cycle must not retain a diagnostic-only status: " + cycle.status());
    }

    @Test
    void compressedStockSuppliesLargeDownstreamDemandWithoutLooseSeed() throws Exception {
        AEKey product = mock(AEKey.class);
        AEKey crystal = mock(AEKey.class);
        AEKey block = mock(AEKey.class);
        for (AEKey key : List.of(product, crystal, block)) when(key.getAmountPerByte()).thenReturn(8);
        var finalRecipe = staticPattern(0, product, 1L, crystal, 400_000L);
        var unpack = staticPattern(1, crystal, 4L, block, 1L);
        var pack = staticPattern(2, block, 1L, crystal, 4L);
        var network = new CompiledNetwork(product, Map.of(product, List.of(finalRecipe),
            crystal, List.of(unpack), block, List.of(pack)), Set.of(), 3, 3);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var inventory = new KeyCounter();
        inventory.add(block, 100_000L);
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, inventory, 1L, true, ECOCancellation.NONE);
        assertEquals(cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus.SUCCESS,
            outcome.status(), outcome.trace().diagnostics().toString());
        assertTrue(outcome.state().missingItems().isEmpty());
        assertEquals(100_000L, outcome.state().usedItems().get(block));
        assertEquals(100_000L, outcome.state().patternTimes().get(unpack.details()));
        assertFalse(outcome.state().patternTimes().containsKey(pack.details()));
    }

    @Test
    void compressedIngredientRemainsIndependentOfFourGrowthSeeds() throws Exception {
        AEKey product = mock(AEKey.class);
        AEKey crystal = mock(AEKey.class);
        AEKey block = mock(AEKey.class);
        var seeds = java.util.stream.IntStream.range(0, 4).mapToObj(i -> mock(AEKey.class)).toList();
        var producers = new java.util.LinkedHashMap<AEKey, List<CompiledPattern>>();
        var finalInputs = new java.util.ArrayList<GenericStack>();
        finalInputs.add(new GenericStack(crystal, 400_000L));
        var inventory = new KeyCounter();
        inventory.add(block, 100_004L);
        for (int i = 0; i < seeds.size(); i++) {
            AEKey seed = seeds.get(i);
            inventory.add(seed, 1L);
            finalInputs.add(new GenericStack(seed, 4L));
            producers.put(seed, List.of(staticPattern(3 + i, seed, 2L,
                new GenericStack(seed, 1L), new GenericStack(crystal, 1L))));
        }
        var unpack = staticPattern(1, crystal, 4L, block, 1L);
        var pack = staticPattern(2, block, 1L, crystal, 4L);
        producers.put(product, List.of(staticPattern(0, product, 1L, finalInputs.toArray(GenericStack[]::new))));
        producers.put(crystal, List.of(unpack));
        producers.put(block, List.of(pack));
        producers.keySet().forEach(key -> when(key.getAmountPerByte()).thenReturn(8));
        var network = new CompiledNetwork(product, producers, Set.of(), 7, 15);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, inventory, 1L, true, ECOCancellation.NONE);
        assertEquals(cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus.SUCCESS,
            outcome.status(), outcome.trace().diagnostics().toString());
        assertTrue(outcome.state().missingItems().isEmpty());
        assertEquals(100_004L, outcome.state().usedItems().get(block));
        assertEquals(0L, outcome.state().usedItems().get(crystal));
        assertEquals(100_004L, outcome.state().patternTimes().get(unpack.details()));
        assertFalse(outcome.state().patternTimes().containsKey(pack.details()));
    }

    private static CompiledPattern staticPattern(int id, AEKey outputKey, long outputAmount,
            AEKey inputKey, long inputAmount) {
        return staticPattern(id, outputKey, outputAmount, new GenericStack(inputKey, inputAmount));
    }

    private static CompiledPattern staticPattern(int id, AEKey outputKey, long outputAmount,
            GenericStack... inputs) {
        IPatternDetails details = mock(IPatternDetails.class);
        var outputs = List.of(new GenericStack(outputKey, outputAmount));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, outputKey, PlannerAmount.of(outputAmount),
            java.util.Arrays.stream(inputs).map(input ->
                new CompiledInput(null, input.what(), input.amount(), true, null)).toList(), outputs,
            true, null, false, semantics);
    }

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
