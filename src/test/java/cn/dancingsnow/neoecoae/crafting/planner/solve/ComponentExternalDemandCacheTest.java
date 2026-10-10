package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import cn.dancingsnow.neoecoae.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ComponentExternalDemandCacheTest {
    @Test
    void repeatedExternalShortageIsSolvedOnceAndDoesNotHideAFeasibleAlternative() throws Exception {
        AEKey goal = key("goal"), seed = key("seed"), boundary = key("boundary"), raw = key("raw"), fuel = key("fuel");
        var first = pattern(0, goal, 1L, new GenericStack(seed, 2L));
        var second = pattern(1, goal, 1L, new GenericStack(seed, 2L));
        var third = pattern(2, goal, 1L, new GenericStack(seed, 2L));
        var feasible = pattern(3, goal, 1L, new GenericStack(fuel, 1L));
        var growth = pattern(4, seed, 2L, new GenericStack(seed, 1L), new GenericStack(boundary, 1L));
        var external = pattern(5, boundary, 1L, new GenericStack(raw, 2L));
        var network = new CompiledNetwork(goal, Map.of(goal, List.of(first, second, third, feasible),
            seed, List.of(growth), boundary, List.of(external), raw, List.of(), fuel, List.of()), Set.of(), 6, 8);
        var dependencyGraph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var graph = CondensationGraph.build(dependencyGraph,
            new TarjanSccAnalyzer().analyze(dependencyGraph, ECOCancellation.NONE), ECOCancellation.NONE);
        var initial = new ActiveRouteSelector().selectWithChoices(dependencyGraph, Map.of(goal, 0), ECOCancellation.NONE);
        var stock = new KeyCounter();
        stock.add(seed, 1L);
        stock.add(raw, 1L);
        stock.add(fuel, 1L);
        var solver = spy(new AcyclicCraftingSolver());
        var planner = new ComponentPlanner(solver, new BoundedCycleSolver(), 4);

        var result = planner.planWithCycleFallback(network, graph, initial, stock,
            PlannerInventorySnapshot.of(stock), 1L, false, ECOCancellation.NONE);

        assertEquals(PlanningStatus.SUCCESS, result.status(), result.trace().diagnostics().toString());
        assertEquals(Map.of(feasible.details(), 1L), result.state().patternTimes());
        verify(solver, times(4)).solve(same(network), any(), any(PlannerInventorySnapshot.class), eq(1L),
            anyMap(), anySet(), eq(false), any());
        verify(solver).solveDemands(any(), any(), eq(Map.of(boundary, 1L)), anyMap(), anySet(), eq(false), any());
        verify(solver, times(1)).solveDemands(any(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any());
        assertEquals(1L, stock.get(seed));
        assertEquals(1L, stock.get(raw));
        assertEquals(1L, stock.get(fuel));

        // A new request can observe changed stock and must not inherit the previous missing proof.
        stock.add(raw, 1L);
        var recovered = planner.planWithCycleFallback(network, graph, initial, stock,
            PlannerInventorySnapshot.of(stock), 1L, false, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, recovered.status(), recovered.trace().diagnostics().toString());
        assertTrue(recovered.state().patternTimes().containsKey(growth.details()));
        assertTrue(recovered.state().patternTimes().containsKey(external.details()));
        verify(solver, times(2)).solveDemands(any(), any(), anyMap(), anyMap(), anySet(), anyBoolean(), any());
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    private static CompiledPattern pattern(int id, AEKey output, long amount, GenericStack... inputs) {
        var details = mock(IPatternDetails.class);
        var outputs = List.of(new GenericStack(output, amount));
        when(details.getOutputs()).thenReturn(outputs);
        var semantics = new PatternSemantics(details, null, List.of(), outputs, List.of(), List.of(),
            PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE, true, true, null);
        return new CompiledPattern(id, details, output, PlannerAmount.of(amount),
            java.util.Arrays.stream(inputs).map(input -> new CompiledInput(null, input.what(), input.amount(), true, null)).toList(),
            outputs, true, null, false, semantics);
    }
}
