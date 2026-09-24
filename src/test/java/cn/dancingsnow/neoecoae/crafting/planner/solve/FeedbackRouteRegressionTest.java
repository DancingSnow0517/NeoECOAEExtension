package cn.dancingsnow.neoecoae.crafting.planner.solve;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExecutionRuntime;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.*;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.crafting.planner.graph.*;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.*;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.util.*;
import org.junit.jupiter.api.Test;

class FeedbackRouteRegressionTest {
    private final AEKey a = key("seed");
    private final AEKey b = key("intermediate");
    private final AEKey c = key("byproduct");
    private final AEKey goal = key("goal");

    @Test
    void byproductFeedbackNeedsStartupStockAndIsNotADag() throws Exception {
        var finish = pattern(0, goal, Map.of(b, 1L), Map.of(goal, 1L, a, 1L));
        var convert = pattern(1, b, Map.of(a, 1L), Map.of(b, 1L));
        var network = network(Map.of(goal, List.of(finish), b, List.of(convert), a, List.of()));
        var graph = graph(network);
        assertEquals(1, graph.cycles().size());
        assertFalse(new ActiveRouteSelector().select(graph.source(), ECOCancellation.NONE).acyclic());

        var missing = planner().plan(network, graph, new KeyCounter(), 2L, true, ECOCancellation.NONE);
        assertNotEquals(PlanningStatus.SUCCESS, missing.status());
        assertFalse(missing.state().missingItems().isEmpty());
        assertTrue(missing.state().patternTimes().isEmpty());

        var stock = new KeyCounter();
        stock.add(a, 1L);
        var solved = planner().plan(network, graph, stock, 2L, true, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, solved.status(), solved.trace().diagnostics().toString());
        assertEquals(2L, solved.state().patternTimes().get(finish.details()));
        assertEquals(2L, solved.state().patternTimes().get(convert.details()));
    }

    @Test
    void ordinaryByproductDoesNotCreateACycleOrBecomeAStandaloneProducer() throws Exception {
        var recipe = pattern(0, goal, Map.of(a, 1L), Map.of(goal, 1L, c, 1L));
        var network = network(Map.of(goal, List.of(recipe), a, List.of()));
        assertTrue(graph(network).cycles().isEmpty());
        assertTrue(network.producersOf(c).isEmpty());
        var stock = new KeyCounter();
        stock.add(a, 2L);
        assertEquals(PlanningStatus.SUCCESS,
            planner().plan(network, graph(network), stock, 2L, true, ECOCancellation.NONE).status());
    }

    @Test
    void threeRecipeWitnessSurvivesRuntimeConversionAndProtectsTheOnlySeed() throws Exception {
        var prepare = pattern(0, b, Map.of(a, 1L), Map.of(b, 1L, c, 1L));
        var grow = pattern(1, a, Map.of(b, 1L, c, 1L), Map.of(a, 2L));
        var finish = pattern(2, goal, Map.of(a, 1L), Map.of(goal, 1L, c, 1L));
        var network = network(Map.of(goal, List.of(finish), a, List.of(grow), b, List.of(prepare), c, List.of()));
        var stock = new KeyCounter();
        stock.add(a, 1L);
        var solved = planner().plan(network, graph(network), stock, 2L, true, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, solved.status(), solved.trace().diagnostics().toString());
        assertEquals(ECOExecutionRequirement.ORDERED,
            ECOExecutionRequirement.classify(solved.components(), solved.state().patternTimes()));
        Map<AEKey, Long> used = new HashMap<>();
        solved.state().usedItems().forEach(entry -> used.put(entry.getKey(), entry.getLongValue()));
        var signature = new PlanIdentity.Signature(goal, 2L, Map.of(), used, Map.of(), Map.of());
        var plan = ECOExecutionPlanBuilder.build(signature, ExecutionMode.ORDERED_CYCLE,
            solved.components(), solved.executionComponentOrder(), solved.state().patternTimes());
        Map<Integer, IPatternDetails> patterns = new HashMap<>();
        plan.tasks().forEach(task -> patterns.put(task.id(), task.pattern()));
        var runtime = new ECOExecutionRuntime(plan, patterns);
        Map<IPatternDetails, Long> remaining = new HashMap<>(solved.state().patternTimes());
        Map<AEKey, Long> actualStock = new HashMap<>(Map.of(a, 1L));
        Map<IPatternDetails, CompiledPattern> compiled = Map.of(
            prepare.details(), prepare, grow.details(), grow, finish.details(), finish);
        int firings = 0;
        while (!runtime.isComplete(remaining)) {
            assertTrue(firings++ < 20, "Schedule failed to finish");
            var candidates = runtime.candidates(remaining);
            assertEquals(1, candidates.size(), "Only the verified next transition may spend the seed");
            var candidate = candidates.getFirst();
            if (firings == 1) assertSame(prepare.details(), candidate.pattern());
            var recipe = compiled.get(candidate.pattern());
            var consumed = new KeyCounter();
            for (var input : recipe.inputs()) {
                long amount = input.amountPerPattern().longValueExact();
                assertTrue(actualStock.getOrDefault(input.key(), 0L) >= amount, "Runtime exhausted its seed");
                actualStock.merge(input.key(), -amount, Long::sum);
                consumed.add(input.key(), amount);
            }
            recipe.grossOutputs().forEach(output -> actualStock.merge(output.what(), output.amount(), Long::sum));
            remaining.merge(candidate.pattern(), -1L, Long::sum);
            runtime.onAccepted(candidate, 1L, new KeyCounter[] {consumed});
        }
        assertEquals(2L, actualStock.get(goal));
    }

    @Test
    void alternateMultiRecipeCycleIsReclassifiedAfterDownstreamFailure() throws Exception {
        var unavailable = key("unavailable");
        var first = pattern(0, goal, Map.of(unavailable, 1L), Map.of(goal, 1L));
        var second = pattern(1, goal, Map.of(b, 1L), Map.of(goal, 2L));
        var reverse = pattern(2, b, Map.of(goal, 1L), Map.of(b, 1L));
        var network = network(Map.of(goal, List.of(first, second), b, List.of(reverse), unavailable, List.of()));
        var stock = new KeyCounter();
        stock.add(goal, 1L);
        var solved = planner().plan(network, graph(network), stock, 2L, true, ECOCancellation.NONE);
        assertEquals(PlanningStatus.SUCCESS, solved.status(), solved.trace().diagnostics().toString());
        assertFalse(solved.state().patternTimes().containsKey(first.details()));
        assertFalse(solved.cycles().isEmpty());
        assertEquals(2L, solved.state().patternTimes().get(second.details()));
        assertEquals(2L, solved.state().patternTimes().get(reverse.details()));
    }

    private static AEKey key(String name) {
        var key = mock(AEKey.class, name);
        when(key.getAmountPerByte()).thenReturn(8);
        return key;
    }

    @Test
    void choosesCWhenTheRecipeUsingBHasNoMaterials() throws Exception {
        var fromB = pattern(0, goal, Map.of(b, 1L), Map.of(goal, 1L));
        var fromC = pattern(1, goal, Map.of(c, 1L), Map.of(goal, 1L));
        var network = network(Map.of(goal, List.of(fromB, fromC), b, List.of(), c, List.of()));
        var stock = new KeyCounter();
        stock.add(c, 2L);
        for (boolean cyclesEnabled : List.of(false, true)) {
            var solved = planner().plan(network, graph(network), stock, 2L, cyclesEnabled, ECOCancellation.NONE);
            assertEquals(PlanningStatus.SUCCESS, solved.status());
            assertEquals(Map.of(fromC.details(), 2L), solved.state().patternTimes());
            assertEquals(2L, solved.state().usedItems().get(c));
        }
    }

    @Test
    void blockedCyclicAlternativeDoesNotHideALaterUsableRecipe() throws Exception {
        var missing = pattern(0, goal, Map.of(b, 1L), Map.of(goal, 1L));
        var cyclic = pattern(1, goal, Map.of(a, 1L), Map.of(goal, 2L));
        var reverse = pattern(2, a, Map.of(goal, 1L), Map.of(a, 1L));
        var usable = pattern(3, goal, Map.of(c, 1L), Map.of(goal, 1L));
        for (var candidates : List.of(List.of(missing, cyclic, usable), List.of(cyclic, missing, usable))) {
            var network = network(Map.of(goal, candidates, a, List.of(reverse), b, List.of(), c, List.of()));
            var stock = new KeyCounter();
            stock.add(c, 2L);
            var planner = planner();
            var graph = graph(network);
            var solved = planner.planWithCycleFallback(network, graph,
                planner.selectRoutes(graph, true, ECOCancellation.NONE), stock, PlannerInventorySnapshot.of(stock),
                2L, false, ECOCancellation.NONE);
            assertEquals(PlanningStatus.SUCCESS, solved.status(), solved.trace().diagnostics().toString());
            assertEquals(Map.of(usable.details(), 2L), solved.state().patternTimes());
        }
    }

    @Test
    void alternateByproductFeedbackIsAlsoReclassifiedBeforeCreditingOutputs() throws Exception {
        var unavailable = key("unavailable");
        var first = pattern(0, goal, Map.of(unavailable, 1L), Map.of(goal, 1L));
        var finish = pattern(1, goal, Map.of(b, 1L), Map.of(goal, 1L, a, 1L));
        var convert = pattern(2, b, Map.of(a, 1L), Map.of(b, 1L));
        var network = network(Map.of(goal, List.of(first, finish), b, List.of(convert),
            a, List.of(), unavailable, List.of()));
        for (boolean seeded : List.of(false, true)) {
            var stock = new KeyCounter();
            if (seeded) stock.add(a, 1L);
            var solved = planner().plan(network, graph(network), stock, 2L, true, ECOCancellation.NONE);
            assertFalse(solved.cycles().isEmpty());
            if (seeded) {
                assertEquals(PlanningStatus.SUCCESS, solved.status(), solved.trace().diagnostics().toString());
                assertEquals(1L, solved.state().usedItems().get(a));
            } else {
                assertNotEquals(PlanningStatus.SUCCESS, solved.status());
                assertFalse(solved.state().missingItems().isEmpty());
                assertTrue(solved.state().patternTimes().isEmpty());
            }
        }
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

    private static ComponentPlanner planner() {
        return new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver());
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
