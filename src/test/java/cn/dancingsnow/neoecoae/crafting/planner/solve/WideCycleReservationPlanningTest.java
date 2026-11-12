package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.bridge.AE2CraftingPlanBridge;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Two real growing cycles share a creative input through an external DAG, including beyond 2^126. */
class WideCycleReservationPlanningTest {
    @Test
    void directAndDelegatedCycleDemandCanSumBeyondLong() throws Exception {
        AEKey product = key("product"), consumer = key("consumer seed"), supplier = key("supplier seed");
        long half = Long.MAX_VALUE / 2 + 1;
        var patterns = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        patterns.put(product, List.of(pattern(0, product, 1,
            new GenericStack(consumer, 1), new GenericStack(supplier, half))));
        patterns.put(consumer, List.of(pattern(1, consumer, 2,
            new GenericStack(consumer, 1), new GenericStack(supplier, half))));
        patterns.put(supplier, List.of(pattern(2, supplier, 2, new GenericStack(supplier, 1))));
        var network = new CompiledNetwork(product, patterns, Set.of(), 3, 3);
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var stock = new KeyCounter(); stock.set(consumer, 1); stock.set(supplier, 1);
        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, stock, 1, true, ECOCancellation.NONE);
        assertEquals(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, outcome.status());
        assertTrue(outcome.completeCandidate(), outcome.trace().diagnostics().toString());
        assertTrue(outcome.components().stream().filter(component -> component.cycleResult() != null)
            .allMatch(component -> component.cycleResult().status()
                == cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus.SUCCESS));
        assertTrue(outcome.state().plannerPatternTimes().values().stream().anyMatch(count -> !count.fitsLong()));
    }

    @ParameterizedTest
    @CsvSource({"1,1", "2305843009213693951,1", "9223372036854775807,1", "9223372036854775807,2"})
    void sharedBoundaryReservationsRemainExact(long ingredient, int depth) throws Exception {
        var fixture = new Fixture(ingredient, depth);
        var expected = BigInteger.valueOf(ingredient).pow(depth).multiply(BigInteger.valueOf(8));
        for (int attempt = 0; attempt < 2; attempt++) {
            var outcome = fixture.plan(true);
            assertEquals(expected.bitLength() < 64 ? PlanningStatus.SUCCESS
                : PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, outcome.status(),
                outcome.trace().diagnostics().toString());
            assertEquals(expected, outcome.state().usedAmounts().get(fixture.water).toBigInteger());
            assertTrue(outcome.state().missingAmounts().isEmpty());
            assertEquals(2, outcome.components().stream().filter(component ->
                component.cycleDisposition() == CycleExecutionDisposition.ORDERED_EXECUTION).count());
            outcome.state().executionProvenance().requireComplete();
            assertEquals(Long.MAX_VALUE, fixture.stock.get(fixture.water), "Planning must not consume stock");
            if (!expected.equals(BigInteger.valueOf(8))) {
                var shell = new AE2CraftingPlanBridge().unsupported(fixture.product, 4);
                var result = new ECOPlanningResult(outcome.status(), shell, outcome.trace(), outcome.cycles(),
                    outcome.components(), outcome.executionComponentOrder(), 0, outcome.state().executionProvenance());
                result.setExactMaterials(outcome.state().usedAmounts(), outcome.state().emittedAmounts(),
                    outcome.state().missingAmounts());
                result.setExactPatternTimes(outcome.state().plannerPatternTimes());
                result.setTheoreticalBytes(outcome.state().plannerBytes());
                var executable = new ECOExactCraftingPlan(result, false);
                assertEquals(expected, BigInteger.valueOf(executable.usedItems().get(fixture.water))
                    .add(executable.deferredStock().getOrDefault(fixture.water, BigInteger.ZERO)));
                assertNotNull(executable.execution());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"9223372036854775807,1", "9223372036854775807,2"})
    void saturatedFiniteStockCannotBecomeCreativeSupply(long ingredient, int depth) throws Exception {
        var outcome = new Fixture(ingredient, depth).plan(false);
        assertTrue(outcome.components().stream().anyMatch(component ->
            component.cycleDisposition() == CycleExecutionDisposition.BLOCKED));
        assertTrue(outcome.state().usedAmounts().isEmpty(), "Rejected external work must not be committed");
    }

    private static final class Fixture {
        final AEKey product = key("product"), first = key("first seed"), second = key("second seed");
        final AEKey token = key("token"), intermediate = key("intermediate"), water = key("water");
        final KeyCounter stock = new KeyCounter();
        final CompiledNetwork network;
        final CondensationGraph condensation;
        final ComponentPlanner planner = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver());

        Fixture(long ingredient, int depth) throws Exception {
            var patterns = new LinkedHashMap<AEKey, List<CompiledPattern>>();
            patterns.put(product, List.of(pattern(0, product, 1, new GenericStack(first, 1), new GenericStack(second, 1))));
            patterns.put(first, List.of(pattern(1, first, 2, new GenericStack(first, 1), new GenericStack(token, 1))));
            patterns.put(second, List.of(pattern(2, second, 2, new GenericStack(second, 1), new GenericStack(token, 1))));
            patterns.put(token, List.of(pattern(3, token, 1, new GenericStack(depth == 1 ? water : intermediate, ingredient))));
            if (depth == 2) patterns.put(intermediate,
                List.of(pattern(4, intermediate, 1, new GenericStack(water, ingredient))));
            patterns.put(water, List.of());
            network = new CompiledNetwork(product, patterns, Set.of(), depth + 3, depth + 5);
            var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
            condensation = CondensationGraph.build(graph,
                new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
            stock.set(first, 1); stock.set(second, 1); stock.set(water, Long.MAX_VALUE);
        }

        ComponentPlanner.Outcome plan(boolean creative) throws Exception {
            return planner.plan(network, condensation, stock,
                PlannerInventorySnapshot.of(stock, creative ? Set.of(water) : Set.of()),
                4, true, false, ECOCancellation.NONE);
        }
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledPattern pattern(int id, AEKey output, long count, GenericStack... ingredients) {
        var details = mock(IPatternDetails.class, "pattern " + id);
        var inputs = new ArrayList<CompiledInput>();
        var rawInputs = new ArrayList<IPatternDetails.IInput>();
        for (var ingredient : ingredients) {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[]{ingredient});
            when(input.getMultiplier()).thenReturn(1L);
            rawInputs.add(input);
            inputs.add(new CompiledInput(input, ingredient.what(), ingredient.amount(), true, null));
        }
        when(details.getInputs()).thenReturn(rawInputs.toArray(new IPatternDetails.IInput[0]));
        var outputs = List.of(new GenericStack(output, count));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, output, PlannerAmount.of(count), inputs, outputs,
            true, null, false, semantics);
    }
}
