package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.AcyclicComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ECOAcyclicCraftingSolverTest {
    @Test void basicChainProducesOneOfEveryPatternAndConsumesTheLeaf() throws Exception {
        var a = PlannerTestKey.of("chain_a"); var b = PlannerTestKey.of("chain_b");
        var c = PlannerTestKey.of("chain_c"); var d = PlannerTestKey.of("chain_d");
        var pb = PlannerFixtures.pattern("a_b", b, 1, a, 1L);
        var pc = PlannerFixtures.pattern("b_c", c, 1, b, 1L);
        var pd = PlannerFixtures.pattern("c_d", d, 1, c, 1L);
        var network = network(d, Map.of(b, List.of(cp(0, pb, b, true)), c, List.of(cp(1, pc, c, true)),
            d, List.of(cp(2, pd, d, true)), a, List.of()));
        KeyCounter stock = stock(a, 1);
        var solved = solve(network, stock, 1);
        assertEquals(PlanningStatus.SUCCESS, solved.status());
        assertEquals(1, solved.state().usedItems().get(a));
        assertEquals(1, solved.state().patternTimes().get(pb));
        assertEquals(1, solved.state().patternTimes().get(pc));
        assertEquals(1, solved.state().patternTimes().get(pd));
    }

    @Test void diamondMergesSharedLeafDemand() throws Exception {
        var x = PlannerTestKey.of("diamond_x"); var a = PlannerTestKey.of("diamond_a");
        var b = PlannerTestKey.of("diamond_b"); var g = PlannerTestKey.of("diamond_g");
        var pa = PlannerFixtures.pattern("x_a", a, 1, x, 1L);
        var pb = PlannerFixtures.pattern("x_b", b, 1, x, 1L);
        var pg = PlannerFixtures.pattern("ab_g", g, 1, a, 1L, b, 1L);
        var network = network(g, Map.of(x, List.of(), a, List.of(cp(0, pa, a, true)),
            b, List.of(cp(1, pb, b, true)), g, List.of(cp(2, pg, g, true))));
        var solved = solve(network, stock(x, 2), 1);
        assertEquals(PlanningStatus.SUCCESS, solved.status());
        assertEquals(2, solved.state().usedItems().get(x));
        assertTrue(solved.trace().nodes().stream().anyMatch(n -> x.equals(n.key()) && n.requested() == 2));
    }

    @Test void billionScaleRequestUsesBatchArithmetic() throws Exception {
        var a = PlannerTestKey.of("large_a"); var g = PlannerTestKey.of("large_g");
        var p = PlannerFixtures.pattern("four_a_g", g, 1, a, 4L);
        var network = network(g, Map.of(a, List.of(), g, List.of(cp(0, p, g, true))));
        var solved = solve(network, stock(a, 4_000_000_000L), 1_000_000_000L);
        assertEquals(PlanningStatus.SUCCESS, solved.status());
        assertEquals(1_000_000_000L, solved.state().patternTimes().get(p));
        assertEquals(4_000_000_000L, solved.state().usedItems().get(a));
    }

    @Test void laterProducerIsRetriedAfterFirstProducerFailsDownstream() throws Exception {
        var a = PlannerTestKey.of("retry_a"); var b = PlannerTestKey.of("retry_b"); var g = PlannerTestKey.of("retry_g");
        var p1 = PlannerFixtures.pattern("a_g", g, 1, a, 1L);
        var p2 = PlannerFixtures.pattern("b_g", g, 1, b, 1L);
        var network = network(g, Map.of(a, List.of(), b, List.of(),
            g, List.of(cp(0, p1, g, true), cp(1, p2, g, true))));
        var solved = solve(network, stock(b, 1), 1);
        assertEquals(PlanningStatus.SUCCESS, solved.status());
        assertEquals(0, solved.state().patternTimes().getOrDefault(p1, 0L));
        assertEquals(1, solved.state().patternTimes().get(p2));
        assertEquals(1, solved.state().usedItems().get(b));
    }

    @Test void malformedFirstProducerIsSkippedForAValidSecondProducer() throws Exception {
        var a = PlannerTestKey.of("mal_a"); var g = PlannerTestKey.of("mal_g");
        var malformed = PlannerFixtures.pattern("malformed", g, 1, a, 1L);
        var valid = PlannerFixtures.pattern("valid", g, 1, a, 1L);
        var network = network(g, Map.of(a, List.of(),
            g, List.of(cp(0, malformed, g, false), cp(1, valid, g, true))));
        var solved = solve(network, stock(a, 1), 1);
        assertEquals(PlanningStatus.SUCCESS, solved.status());
        assertEquals(1, solved.state().patternTimes().get(valid));
    }

    @Test void ordinaryUnavailableLeafIsMissingNotUnsupported() throws Exception {
        var a = PlannerTestKey.of("missing_a"); var g = PlannerTestKey.of("missing_g");
        var p = PlannerFixtures.pattern("a_g", g, 1, a, 1L);
        var solved = solve(network(g, Map.of(a, List.of(), g, List.of(cp(0, p, g, true)))), new KeyCounter(), 1);
        assertEquals(PlanningStatus.MISSING_ITEMS, solved.status());
        assertEquals(1, solved.state().missingItems().get(a));
    }

    @Test void unsafeRemainderCandidateRequestsNativeFallbackInsteadOfGuessing() throws Exception {
        var bucket = PlannerTestKey.of("bucket"); var g = PlannerTestKey.of("remainder_g");
        var p = PlannerFixtures.pattern("bucket_g", g, 1, bucket, 1L);
        var solved = solve(network(g, Map.of(bucket, List.of(), g, List.of(cp(0, p, g, false)))), stock(bucket, 1), 1);
        assertEquals(PlanningStatus.PARTIAL_UNSUPPORTED, solved.status());
    }

    @Test void multiplicationOverflowIsAResultNotWrappedArithmetic() throws Exception {
        var a = PlannerTestKey.of("overflow_a"); var g = PlannerTestKey.of("overflow_g");
        var p = PlannerFixtures.pattern("huge", g, 1, a, Long.MAX_VALUE);
        var solved = solve(network(g, Map.of(a, List.of(), g, List.of(cp(0, p, g, true)))), new KeyCounter(), 2);
        assertEquals(PlanningStatus.AMOUNT_OVERFLOW, solved.status());
    }

    private static AcyclicCraftingSolver.Outcome solve(CompiledNetwork network, KeyCounter stock, long amount) throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
        var route = new AcyclicRoutePlan(condensation.topologicalOrder().stream()
            .map(AcyclicComponent.class::cast).map(AcyclicComponent::key).toList());
        return new AcyclicCraftingSolver().solve(network, route, stock, amount, ECOCancellation.NONE);
    }
    private static CompiledNetwork network(AEKey goal, Map<AEKey, List<CompiledPattern>> patterns) {
        return PlannerFixtures.network(goal, new LinkedHashMap<>(patterns));
    }
    private static CompiledPattern cp(int id, PlannerFixtures.Pattern p, AEKey output, boolean fast) {
        return PlannerFixtures.compiled(id, p, output, fast, fast ? "" : "UNSUPPORTED_INPUT");
    }
    private static KeyCounter stock(AEKey key, long amount) { var stock = new KeyCounter(); stock.add(key, amount); return stock; }
}
