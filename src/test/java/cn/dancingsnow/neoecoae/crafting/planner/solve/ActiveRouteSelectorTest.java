package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingDependencyGraph;
import cn.dancingsnow.neoecoae.crafting.planner.graph.CraftingGraphNode;
import cn.dancingsnow.neoecoae.crafting.planner.semantic.PatternSemantics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActiveRouteSelectorTest {
    @Test
    void jumpsOverCandidatesThatCloseTheCurrentReachableCycle() throws Exception {
        AEKey a = mock(AEKey.class);
        AEKey b = mock(AEKey.class);
        List<CompiledPattern> aCandidates = new ArrayList<>();
        for (int i = 0; i < 14; i++) aCandidates.add(pattern(i, a, b));
        aCandidates.add(pattern(14, a, null));
        CompiledPattern bCandidate = pattern(15, b, a);

        Map<AEKey, CraftingGraphNode> nodes = new LinkedHashMap<>();
        nodes.put(a, new CraftingGraphNode(a, aCandidates));
        nodes.put(b, new CraftingGraphNode(b, List.of(bCandidate)));
        CraftingDependencyGraph graph = new CraftingDependencyGraph(a, nodes, List.of());

        ActiveRouteSelector.Selection selection = new ActiveRouteSelector().select(graph, ECOCancellation.NONE);

        assertTrue(selection.acyclic());
        assertEquals(ActiveRouteSelector.Selection.Status.ACYCLIC, selection.status());
        assertFalse(selection.budgetExhausted());
        assertEquals(14, selection.choices().get(a));
        assertTrue(selection.condensation().cycles().isEmpty());
    }

    @Test
    void retryCertificateReusesUnchangedTopologyAndRejectsNewFeedback() throws Exception {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), c = mock(AEKey.class);
        var first = pattern(0, a, b, c);
        var sameEdges = pattern(1, a, b, c);
        var feedback = pattern(2, a, c, c);
        var graph = graph(a, Map.of(a, List.of(first, sameEdges, feedback), b, List.of(), c, List.of()));
        var metrics = new AcyclicSolveMetrics();
        var check = new ActiveRouteSelector.RetryCheck(graph, Set.of(), metrics, ECOCancellation.NONE);
        assertFalse(check.requiresCycle(Map.of(), ECOCancellation.NONE));
        assertFalse(check.requiresCycle(Map.of(a, 1), ECOCancellation.NONE));
        assertEquals(1, metrics.activeGraphBuilds);
        assertEquals(1, metrics.graphCacheHits);
        assertTrue(check.requiresCycle(Map.of(a, 2), ECOCancellation.NONE));
        assertEquals(2, metrics.activeGraphBuilds);
        assertEquals(1, metrics.graphInvalidations);
        assertEquals(fullGuard(graph, Map.of(a, 2), Set.of()),
            check.requiresCycle(Map.of(a, 2), ECOCancellation.NONE));
    }

    @Test
    void retryCertificateUsesRawSccsBeforeOrderingEdgesArePruned() throws Exception {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), raw = mock(AEKey.class);
        var graph = graph(a, Map.of(a, List.of(pattern(0, a, raw, b), pattern(1, a, b, b)),
            b, List.of(pattern(2, b, raw, a)), raw, List.of()));
        var metrics = new AcyclicSolveMetrics();
        var check = new ActiveRouteSelector.RetryCheck(graph, Set.of(), metrics, ECOCancellation.NONE);
        assertFalse(check.requiresCycle(Map.of(), ECOCancellation.NONE));
        assertEquals(2, metrics.sccPasses, "The initial ordering ring is pruned and analyzed again");
        assertTrue(check.requiresCycle(Map.of(a, 1), ECOCancellation.NONE));
        assertEquals(1, metrics.graphInvalidations);
        assertTrue(fullGuard(graph, Map.of(a, 1), Set.of()));
    }

    @Test
    void changingAPrimaryViewInvalidatesSiblingOutputAnchors() throws Exception {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), raw = mock(AEKey.class);
        var primary = pattern(0, a, raw, b);
        var sibling = new CompiledPattern(0, primary.details(), b, PlannerAmount.ONE, primary.inputs(),
            primary.outputs(), true, null, true, primary.semantics());
        var graph = graph(a, Map.of(a, List.of(primary, pattern(1, a, raw)),
            b, List.of(sibling), raw, List.of(pattern(2, raw, a))));
        var metrics = new AcyclicSolveMetrics();
        var check = new ActiveRouteSelector.RetryCheck(graph, Set.of(), metrics, ECOCancellation.NONE);
        assertTrue(check.requiresCycle(Map.of(), ECOCancellation.NONE));
        assertTrue(check.requiresCycle(Map.of(a, 1), ECOCancellation.NONE),
            "B's physical recipe must now anchor at B, bringing its A output into the material SCC");
        assertEquals(fullGuard(graph, Map.of(a, 1), Set.of()),
            check.requiresCycle(Map.of(a, 1), ECOCancellation.NONE));
        assertEquals(5, metrics.edgeRowsRebuilt, "Changing A also rebuilds its former sibling B");
    }

    @Test
    void interruptedRetryCannotRetainAPartialRowUpdate() throws Exception {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), raw = mock(AEKey.class);
        var graph = graph(a, Map.of(a, List.of(pattern(0, a, raw, b), pattern(1, a, b, b)),
            b, List.of(), raw, List.of()));
        var check = new ActiveRouteSelector.RetryCheck(graph, Set.of(), null, ECOCancellation.NONE);
        assertFalse(check.requiresCycle(Map.of(), ECOCancellation.NONE));
        int[] checkpoints = {2};
        assertThrows(InterruptedException.class, () -> check.requiresCycle(Map.of(a, 1), () -> {
            if (--checkpoints[0] == 0) throw new InterruptedException("test cancellation");
        }));
        assertTrue(check.requiresCycle(Map.of(a, 1), ECOCancellation.NONE));
    }

    @Test
    void retryGuardMatchesFullAnalysisAcrossCandidateAndAliasChanges() throws Exception {
        var random = new Random(0x5cc20261110L);
        List<AEKey> keys = new ArrayList<>();
        for (int i = 0; i < 7; i++) keys.add(mock(AEKey.class, "key" + i));
        for (int scenario = 0; scenario < 24; scenario++) {
            Map<AEKey, List<CompiledPattern>> candidates = new LinkedHashMap<>();
            for (AEKey key : keys) candidates.put(key, new ArrayList<>());
            for (int i = 0; i < 18; i++) {
                AEKey key = keys.get(i % keys.size());
                AEKey input = random.nextInt(4) == 0 ? null : keys.get(random.nextInt(keys.size()));
                AEKey side = keys.get(random.nextInt(keys.size()));
                var pattern = random.nextBoolean() ? pattern(i, key, input, side) : pattern(i, key, input);
                candidates.get(key).add(pattern);
                if (!key.equals(side) && pattern.outputs().size() > 1 && random.nextBoolean()) {
                    candidates.get(side).add(new CompiledPattern(i, pattern.details(), side, PlannerAmount.ONE,
                        pattern.inputs(), pattern.outputs(), true, null, true, pattern.semantics()));
                }
            }
            var graph = graph(keys.get(0), candidates);
            Set<IPatternDetails> deferred = scenario % 3 == 0
                ? Set.of(candidates.get(keys.get(0)).get(0).details()) : Set.of();
            var check = new ActiveRouteSelector.RetryCheck(graph, deferred, null, ECOCancellation.NONE);
            Map<AEKey, Integer> choices = new LinkedHashMap<>();
            for (int retry = 0; retry < 35; retry++) {
                // Include negative/no-view and clamped choices, not only normal monotone fallback.
                AEKey key = keys.get(random.nextInt(keys.size()));
                choices.put(key, random.nextInt(candidates.get(key).size() + 3) - 1);
                assertEquals(fullGuard(graph, choices, deferred), check.requiresCycle(choices, ECOCancellation.NONE),
                    "scenario=" + scenario + " retry=" + retry);
            }
        }
    }

    private static CraftingDependencyGraph graph(AEKey goal, Map<AEKey, List<CompiledPattern>> candidates) {
        Map<AEKey, CraftingGraphNode> nodes = new LinkedHashMap<>();
        candidates.forEach((key, patterns) -> nodes.put(key, new CraftingGraphNode(key, patterns)));
        return new CraftingDependencyGraph(goal, nodes, List.of());
    }

    private static boolean fullGuard(CraftingDependencyGraph graph, Map<AEKey, Integer> choices,
            Set<IPatternDetails> deferred) throws Exception {
        return new ActiveRouteSelector().selectWithChoices(graph, choices, ECOCancellation.NONE).cyclicComponents().stream()
            .filter(cycle -> cycle.patterns().stream().anyMatch(pattern -> pattern.outputs().stream()
                .anyMatch(output -> !output.what().equals(pattern.producedKey()))))
            .flatMap(cycle -> cycle.patterns().stream()).anyMatch(pattern -> !deferred.contains(pattern.details()));
    }

    private static CompiledPattern pattern(int id, AEKey output, AEKey input, AEKey... byproducts) {
        IPatternDetails details = mock(IPatternDetails.class);
        GenericStack product = new GenericStack(output, 1L);
        List<GenericStack> products = new ArrayList<>();
        products.add(product);
        for (AEKey byproduct : byproducts) products.add(new GenericStack(byproduct, 1L));
        when(details.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(details.getOutputs()).thenReturn(products.toArray(GenericStack[]::new));
        when(details.getPrimaryOutput()).thenReturn(product);
        List<CompiledInput> inputs = input == null
            ? List.of() : List.of(new CompiledInput(null, input, 1L, true, null));
        List<PatternSemantics.Input> consumed = input == null
            ? List.of() : List.of(new PatternSemantics.Input(null, input, PlannerAmount.ONE, null, PlannerAmount.ZERO));
        PatternSemantics semantics = new PatternSemantics(details, null, consumed, products, List.of(),
            List.of(), PatternSemantics.MatchingMode.EXACT, PatternSemantics.ExecutionRestriction.NONE,
            true, true, null);
        return new CompiledPattern(id, details, output, PlannerAmount.ONE, inputs, products, true,
            null, true, semantics);
    }
}
