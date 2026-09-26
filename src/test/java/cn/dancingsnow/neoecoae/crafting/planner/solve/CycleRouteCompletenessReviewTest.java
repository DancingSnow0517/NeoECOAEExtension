package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.*;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.graph.*;
import cn.dancingsnow.neoecoae.crafting.planner.result.*;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import cn.dancingsnow.neoecoae.crafting.planner.trace.PlannerDiagnostic;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CycleRouteCompletenessReviewTest {
    private final AEKey a = key("A");
    private final AEKey c = key("C");
    private final AEKey goal = key("goal");

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 256})
    void bothCyclicRoutesMustBeSwitchedTogether(int budget) throws Exception {
        var missing = key("missing");
        var fuel = key("fuel");
        var finish = pattern(0, goal, Map.of(a, 2L, c, 2L), Map.of(goal, 1L));
        var badA = pattern(1, a, Map.of(a, 1L, missing, 1L), Map.of(a, 2L));
        var goodA = pattern(2, a, Map.of(a, 1L, fuel, 1L), Map.of(a, 2L));
        var badC = pattern(3, c, Map.of(c, 1L, missing, 1L), Map.of(c, 2L));
        var goodC = pattern(4, c, Map.of(c, 1L, fuel, 1L), Map.of(c, 2L));
        var network = network(Map.of(goal, List.of(finish), a, List.of(badA, goodA),
            c, List.of(badC, goodC), missing, List.of(), fuel, List.of()));
        var stock = new KeyCounter();
        stock.add(a, 1L);
        stock.add(c, 1L);
        stock.add(fuel, 2L);
        var graph = graph(network);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver(), budget);
        var both = new ActiveRouteSelector().selectWithChoices(graph.source(), Map.of(a, 1, c, 1), ECOCancellation.NONE);
        var control = planner.plan(network, both, stock, 1L, true, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, control.status(), "Explicit feasible route must work: " + control.trace().diagnostics());
        var result = planner.planWithCycleFallback(network, graph,
            planner.selectRoutes(graph, true, ECOCancellation.NONE), stock, PlannerInventorySnapshot.of(stock),
            1L, false, ECOCancellation.NONE);
        assertEquals(budget >= 4 ? PlanningStatus.SUCCESS : PlanningStatus.CYCLE_UNRESOLVED,
            result.status(), result.trace().diagnostics().toString());
        assertEquals(budget < 4, result.trace().diagnostics().stream().anyMatch(d ->
            d.code() == PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED));
        assertEquals(1L, stock.get(a));
        assertEquals(1L, stock.get(c));
        assertEquals(2L, stock.get(fuel), "Speculative routes must not mutate the caller's inventory");
    }

    @Test
    void exhaustingAllRoutesIsNotABudgetCut() throws Exception {
        var missing = key("missing");
        var finish = pattern(0, goal, Map.of(a, 2L), Map.of(goal, 1L));
        var first = pattern(1, a, Map.of(a, 1L, missing, 1L), Map.of(a, 2L));
        var second = pattern(2, a, Map.of(a, 1L, missing, 2L), Map.of(a, 2L));
        var network = network(Map.of(goal, List.of(finish), a, List.of(first, second), missing, List.of()));
        var stock = new KeyCounter();
        stock.add(a, 1L);
        var graph = graph(network);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver(), 2);
        var result = planner.planWithCycleFallback(network, graph,
            planner.selectRoutes(graph, true, ECOCancellation.NONE), stock, PlannerInventorySnapshot.of(stock),
            1L, false, ECOCancellation.NONE);
        assertNotEquals(PlanningStatus.SUCCESS, result.status());
        assertTrue(result.trace().diagnostics().stream().noneMatch(d ->
            d.code() == PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED));
    }

    @Test
    void switchingGoalRecipeCanBypassASeedlessCycle() throws Exception {
        var blockedFinish = pattern(0, goal, Map.of(a, 2L), Map.of(goal, 1L));
        var availableFinish = pattern(1, goal, Map.of(c, 1L), Map.of(goal, 1L));
        var growth = pattern(2, a, Map.of(a, 1L), Map.of(a, 2L));
        var network = network(Map.of(goal, List.of(blockedFinish, availableFinish),
            a, List.of(growth), c, List.of()));
        var stock = new KeyCounter();
        stock.add(c, 1L);
        var graph = graph(network);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver());
        var initial = new ActiveRouteSelector().selectWithChoices(graph.source(),
            Map.of(goal, 0), ECOCancellation.NONE);
        var alternative = new ActiveRouteSelector().selectWithChoices(graph.source(),
            Map.of(goal, 1), ECOCancellation.NONE);
        assertNotEquals(PlanningStatus.SUCCESS,
            planner.plan(network, initial, stock, 1L, true, ECOCancellation.NONE).status());
        assertEquals(PlanningStatus.SUCCESS,
            planner.plan(network, alternative, stock, 1L, true, ECOCancellation.NONE).status());

        var result = planner.planWithCycleFallback(network, graph, initial, stock,
            PlannerInventorySnapshot.of(stock), 1L, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
        assertEquals(1L, stock.get(c));
    }

    @Test
    void fallbackComputesChoicesForDeferredCycleNodesNotDemandedByPreferredRoute() throws Exception {
        var blockedFinish = pattern(0, goal, Map.of(a, 2L), Map.of(goal, 1L));
        var availableFinish = pattern(1, goal, Map.of(c, 1L), Map.of(goal, 1L));
        var blockedGrowth = pattern(2, a, Map.of(a, 1L), Map.of(a, 2L));
        // This producer is not visited by the preferred route, but it is a cyclic candidate in the
        // structural universe. Its stocked feedback key must still receive a concrete route choice
        // before the fallback enumerates the alternative goal producer.
        var stockedGrowth = pattern(3, c, Map.of(c, 1L), Map.of(c, 2L));
        var network = network(Map.of(goal, List.of(blockedFinish, availableFinish),
            a, List.of(blockedGrowth), c, List.of(stockedGrowth)));
        var stock = new KeyCounter();
        stock.add(c, 1L);
        var graph = graph(network);
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver());
        var initial = new ActiveRouteSelector().selectWithChoices(graph.source(),
            Map.of(goal, 0, c, 0), ECOCancellation.NONE);

        var result = planner.planWithCycleFallback(network, graph, initial, stock,
            PlannerInventorySnapshot.of(stock), 1L, false, ECOCancellation.NONE);

        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
        assertEquals(1L, stock.get(c), "Speculative route enumeration must not consume stock");
    }

    @Test
    void seedlessCycleStopsBeforeEnumeratingHundredsOfUnhelpfulRoutes() throws Exception {
        var finishes = new ArrayList<CompiledPattern>();
        for (int index = 0; index < 300; index++) {
            finishes.add(pattern(index, goal, Map.of(a, 2L, c, (long) index + 1), Map.of(goal, 1L)));
        }
        var growth = pattern(300, a, Map.of(a, 1L), Map.of(a, 2L));
        var network = network(Map.of(goal, finishes, a, List.of(growth), c, List.of()));
        var stock = new KeyCounter();
        stock.add(c, 1_000L);
        var graph = graph(network);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var solver = new BoundedCycleSolver();
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), (request, cancellation) -> {
            calls.incrementAndGet();
            return solver.solve(request, cancellation);
        });
        var initial = new ActiveRouteSelector().selectWithChoices(graph.source(), Map.of(goal, 0), ECOCancellation.NONE);
        var result = planner.planWithCycleFallback(network, graph, initial, stock,
            PlannerInventorySnapshot.of(stock), 1L, false, ECOCancellation.NONE);

        assertNotEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(1, calls.get(), "An unavoidable seedless cycle must not be solved per goal recipe");
        assertEquals(1L, result.state().missingItems().get(a));
        assertTrue(result.trace().diagnostics().stream().anyMatch(d ->
            d.code() == PlannerDiagnostic.Code.ROUTE_PROVEN_UNREACHABLE));
        assertTrue(result.trace().diagnostics().stream().noneMatch(d ->
            d.code() == PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED));
    }

    @Test
    void unchangedCycleFailureIsReusedAcrossDifferentGoalRecipes() throws Exception {
        var b = key("B");
        var finishes = new ArrayList<CompiledPattern>();
        for (int index = 0; index < 3; index++) {
            finishes.add(pattern(index, goal, Map.of(a, 2L, c, (long) index + 1), Map.of(goal, 1L)));
        }
        var first = pattern(3, a, Map.of(b, 1L), Map.of(a, 1L));
        var second = pattern(4, b, Map.of(a, 1L), Map.of(b, 1L));
        var network = network(Map.of(goal, finishes, a, List.of(first), b, List.of(second), c, List.of()));
        var stock = new KeyCounter();
        stock.add(a, 1L);
        stock.add(c, 10L);
        var graph = graph(network);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var solver = new BoundedCycleSolver();
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), (request, cancellation) -> {
            calls.incrementAndGet();
            return solver.solve(request, cancellation);
        });
        var initial = new ActiveRouteSelector().selectWithChoices(graph.source(), Map.of(goal, 0), ECOCancellation.NONE);
        var result = planner.planWithCycleFallback(network, graph, initial, stock,
            PlannerInventorySnapshot.of(stock), 1L, false, ECOCancellation.NONE);

        assertNotEquals(PlanningStatus.SUCCESS, result.status());
        assertEquals(1, calls.get(), "Identical local impossibility must be solved only once");
        assertTrue(result.trace().diagnostics().stream().noneMatch(d ->
            d.code() == PlannerDiagnostic.Code.ROUTE_PROVEN_UNREACHABLE
                || d.code() == PlannerDiagnostic.Code.ROUTE_SEARCH_BUDGET_EXHAUSTED));
        stock.add(a, 1L);
        var recovered = planner.planWithCycleFallback(network, graph, initial, stock,
            PlannerInventorySnapshot.of(stock), 1L, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, recovered.status(), recovered.trace().diagnostics().toString());
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }
    private CompiledNetwork network(Map<AEKey, List<CompiledPattern>> producers) {
        return new CompiledNetwork(goal, producers, Set.of(),
            producers.values().stream().mapToInt(List::size).sum(), 10);
    }

    private static CondensationGraph graph(CompiledNetwork network) throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        return CondensationGraph.build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE),
            ECOCancellation.NONE);
    }

    private static CompiledPattern pattern(int id, AEKey primary, Map<AEKey, Long> inputs,
            Map<AEKey, Long> outputs) {
        var details = mock(IPatternDetails.class, "pattern" + id);
        var stacks = new ArrayList<GenericStack>();
        stacks.add(new GenericStack(primary, outputs.get(primary)));
        outputs.forEach((key, amount) -> { if (!key.equals(primary)) stacks.add(new GenericStack(key, amount)); });
        var rawInputs = inputs.entrySet().stream().map(entry -> {
            var input = mock(IPatternDetails.IInput.class);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[] {new GenericStack(entry.getKey(), entry.getValue())});
            when(input.getMultiplier()).thenReturn(1L);
            return input;
        }).toArray(IPatternDetails.IInput[]::new);
        when(details.getInputs()).thenReturn(rawInputs);
        when(details.getOutputs()).thenReturn(stacks);
        when(details.getPrimaryOutput()).thenReturn(stacks.getFirst());
        var compiled = inputs.entrySet().stream().map(entry ->
            new CompiledInput(null, entry.getKey(), entry.getValue(), true, null)).toList();
        var consumed = inputs.entrySet().stream().map(entry -> new PatternSemantics.Input(null,
            entry.getKey(), PlannerAmount.of(entry.getValue()), null, PlannerAmount.ZERO)).toList();
        var semantics = new PatternSemantics(details, null, consumed, stacks, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, primary, PlannerAmount.of(outputs.get(primary)),
            compiled, stacks, true, null, false, semantics);
    }
}
