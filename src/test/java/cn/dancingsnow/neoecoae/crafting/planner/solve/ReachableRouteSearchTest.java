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
import java.util.*;
import org.junit.jupiter.api.Test;

class ReachableRouteSearchTest {
    private final AEKey goal = key("goal");

    @Test
    void routeSearchOnlyVariesProducersTheRouteActuallyReaches() throws Exception {
        var gold = key("gold");
        var c = key("c");
        var scarce = key("scarce");
        var raw = key("raw");
        var e1 = key("e1");
        var e2 = key("e2");
        var e3 = key("e3");
        var producers = new LinkedHashMap<AEKey, List<CompiledPattern>>();
        producers.put(goal, List.of(pattern(0, goal, Map.of(gold, 2L), Map.of(goal, 1L)),
            pattern(1, goal, Map.of(c, 1L), Map.of(goal, 1L))));
        producers.put(gold, List.of(pattern(2, gold, Map.of(gold, 1L), Map.of(gold, 2L))));
        producers.put(c, List.of(pattern(3, c, Map.of(e1, 1L, e2, 1L, e3, 1L, scarce, 5L), Map.of(c, 1L))));
        int id = 4;
        for (var e : List.of(e1, e2, e3)) {
            producers.put(e, List.of(pattern(id++, e, Map.of(raw, 1L), Map.of(e, 1L)),
                pattern(id++, e, Map.of(raw, 1L), Map.of(e, 1L))));
        }
        producers.put(scarce, List.of());
        producers.put(raw, List.of());
        var network = network(producers);
        var stock = new KeyCounter();
        stock.add(scarce, 1L);
        stock.add(raw, 10L);
        var graph = graph(network);
        var acyclic = spy(new AcyclicCraftingSolver());
        var cycleSolves = new java.util.concurrent.atomic.AtomicInteger();
        var cycleSolver = new BoundedCycleSolver();
        var planner = new ComponentPlanner(acyclic, (request, cancellation) -> {
            cycleSolves.incrementAndGet();
            return cycleSolver.solve(request, cancellation);
        });
        var initial = new ActiveRouteSelector().selectWithChoices(graph.source(), Map.of(goal, 1), ECOCancellation.NONE);

        var result = planner.planWithCycleFallback(network, graph, initial, stock,
            PlannerInventorySnapshot.of(stock), 1L, false, ECOCancellation.NONE);

        assertNotEquals(PlanningStatus.SUCCESS, result.status());
        // Only the preferred material report and the cyclic goal alternative require numeric planning.
        // Every prefix still choosing c lacks four scarce units regardless of e1/e2/e3 combinations.
        verify(acyclic, times(2)).solve(same(network), any(), any(PlannerInventorySnapshot.class), eq(1L),
            anyMap(), anySet(), eq(false), any());
        assertEquals(1, cycleSolves.get(), "The seedless cycle must be solved once and then reused");
        assertEquals(1L, stock.get(scarce));
        assertEquals(10L, stock.get(raw));
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
