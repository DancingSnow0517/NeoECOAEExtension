package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.AcyclicComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.UnsupportedCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** SCC, condensation, component-planning and cycle-toggle regression coverage. */
class ECOCycleDetectorTest {
    @Test void pureDagContainsNoCycleComponent() throws Exception {
        AEKey a = key("dag_a"), b = key("dag_b"), c = key("dag_c"), d = key("dag_d");
        var result = analyze(network(d, Map.of(a, List.of(), b, one(edge(0, b, a)),
            c, one(edge(1, c, b)), d, one(edge(2, d, c)))));
        assertEquals(4, result.components().size());
        assertTrue(result.cycles().isEmpty());
        assertTrue(result.topologicalOrder().stream().allMatch(AcyclicComponent.class::isInstance));
    }

    @Test void twoNodeCycleProducesTheWholeScc() throws Exception {
        AEKey a = key("two_a"), b = key("two_b");
        var result = analyze(network(a, Map.of(a, one(edge(0, a, b)), b, one(edge(1, b, a)))));
        assertEquals(1, result.cycles().size());
        assertEquals(Set.of(a, b), Set.copyOf(result.cycles().getFirst().members()));
        assertEquals(2, result.cycles().getFirst().internalEdges().size());
    }

    @Test void threeNodeCycleProducesExactlyOneScc() throws Exception {
        AEKey a = key("three_a"), b = key("three_b"), c = key("three_c");
        var result = analyze(network(a, Map.of(a, one(edge(0, a, b)), b, one(edge(1, b, c)),
            c, one(edge(2, c, a)))));
        assertEquals(1, result.cycles().size());
        assertEquals(Set.of(a, b, c), Set.copyOf(result.cycles().getFirst().members()));
    }

    @Test void selfLoopIsACyclicSingletonComponent() throws Exception {
        AEKey a = key("self");
        CycleComponent cycle = assertInstanceOf(CycleComponent.class,
            analyze(network(a, Map.of(a, one(edge(0, a, a))))).componentFor(a));
        assertEquals(List.of(a), cycle.members());
        assertEquals(1, cycle.internalEdges().size());
    }

    @Test void condensationKeepsDagBoundariesAroundCycle() throws Exception {
        AEKey raw = key("bound_raw"), a = key("bound_a"), b = key("bound_b"), goal = key("bound_goal");
        var aPattern = PlannerFixtures.pattern("a_from_b_raw", a, 1, b, 1L, raw, 1L);
        var result = analyze(network(goal, Map.of(raw, List.of(),
            a, one(PlannerFixtures.compiled(0, aPattern, a, true, "")), b, one(edge(1, b, a)),
            goal, one(edge(2, goal, a)))));
        assertEquals(3, result.components().size());
        assertEquals(Set.of(a, b), Set.copyOf(result.cycles().getFirst().members()));
        assertEquals(2, result.dependencies().size());
        assertEquals(goal, ((AcyclicComponent) result.topologicalOrder().getFirst()).key());
        assertEquals(raw, ((AcyclicComponent) result.topologicalOrder().getLast()).key());
    }

    @Test void twoReachableCyclesRemainIndependentComponents() throws Exception {
        AEKey goal = key("multi_goal"), a = key("multi_a"), b = key("multi_b");
        AEKey c = key("multi_c"), d = key("multi_d");
        var goalPattern = PlannerFixtures.pattern("goal_from_a_c", goal, 1, a, 1L, c, 1L);
        var result = analyze(network(goal, Map.of(
            goal, one(PlannerFixtures.compiled(0, goalPattern, goal, true, "")),
            a, one(edge(1, a, b)), b, one(edge(2, b, a)),
            c, one(edge(3, c, d)), d, one(edge(4, d, c)))));
        assertEquals(2, result.cycles().size());
    }

    @Test void unrelatedNetworkCycleIsNotInGoalReachableGraph() throws Exception {
        AEKey goal = key("reach_goal"), a = key("reach_a"), x = key("reach_x"), y = key("reach_y");
        var result = analyze(network(goal, Map.of(goal, one(edge(0, goal, a)), a, List.of(),
            x, one(edge(1, x, y)), y, one(edge(2, y, x)))));
        assertFalse(result.source().nodes().containsKey(x));
        assertTrue(result.cycles().isEmpty());
    }

    @Test void disabledCycleIsShownInCycleListWithoutCycleSolving() throws Exception {
        Fixture fixture = simpleCycle();
        KeyCounter stock = new KeyCounter(); stock.add(fixture.goal(), 1);
        var result = plan(fixture.network(), stock, false);
        assertEquals(PlanningStatus.CYCLE_UNRESOLVED, result.status());
        assertEquals(0, result.state().missingItems().get(fixture.goal()));
        assertEquals(CyclePlanningStatus.DISABLED, result.trace().cycles().getFirst().status());
        assertTrue(result.trace().cycles().getFirst().members().contains(fixture.goal()));
    }

    @Test void enabledCycleCallsUnsupportedStubWithoutCrashing() throws Exception {
        var result = plan(simpleCycle().network(), new KeyCounter(), true);
        assertEquals(PlanningStatus.CYCLE_UNRESOLVED, result.status());
        assertEquals(CyclePlanningStatus.NOT_IMPLEMENTED, result.trace().cycles().getFirst().status());
        assertTrue(result.state().missingItems().isEmpty());
    }

    @Test void independentDagBranchStillPlansBesideCycle() throws Exception {
        AEKey raw = key("partial_raw"), x = key("partial_x"), a = key("partial_a");
        AEKey b = key("partial_b"), goal = key("partial_goal");
        var px = PlannerFixtures.pattern("raw_x", x, 1, raw, 1L);
        var pa = PlannerFixtures.pattern("b_a", a, 1, b, 1L);
        var pb = PlannerFixtures.pattern("a_b", b, 1, a, 1L);
        var pg = PlannerFixtures.pattern("x_a_goal", goal, 1, x, 1L, a, 1L);
        CompiledNetwork network = network(goal, Map.of(raw, List.of(),
            x, one(PlannerFixtures.compiled(0, px, x, true, "")),
            a, one(PlannerFixtures.compiled(1, pa, a, true, "")),
            b, one(PlannerFixtures.compiled(2, pb, b, true, "")),
            goal, one(PlannerFixtures.compiled(3, pg, goal, true, ""))));
        KeyCounter stock = new KeyCounter(); stock.add(raw, 1);
        var result = plan(network, stock, false);
        assertEquals(PlanningStatus.PARTIAL, result.status());
        assertEquals(1, result.state().usedItems().get(raw));
        assertEquals(1, result.state().patternTimes().get(px));
        assertEquals(0, result.state().missingItems().get(a));
        assertTrue(result.trace().cycles().stream().anyMatch(c -> c.members().contains(a)));
    }

    @Test void structuralAnalysisPropagatesCancellation() {
        AEKey a = key("cancel_a"), b = key("cancel_b");
        CompiledNetwork network = network(a, Map.of(a, one(edge(0, a, b)), b, List.of()));
        assertThrows(InterruptedException.class, () -> new CraftingGraphBuilder().build(network,
            () -> { throw new InterruptedException("cancelled"); }));
    }

    @Test void disabledCyclePathDoesNotSelectAnAcyclicAlternative() throws Exception {
        AEKey iron = key("false_iron"), x = key("false_x"), y = key("false_y");
        var p1 = PlannerFixtures.pattern("iron_x", x, 1, iron, 1L);
        var p2 = PlannerFixtures.pattern("y_x", x, 1, y, 1L);
        var p3 = PlannerFixtures.pattern("x_y", y, 1, x, 1L);
        CompiledNetwork network = network(x, Map.of(iron, List.of(),
            x, List.of(PlannerFixtures.compiled(0, p1, x, true, ""), PlannerFixtures.compiled(1, p2, x, true, "")),
            y, one(PlannerFixtures.compiled(2, p3, y, true, ""))));
        KeyCounter stock = new KeyCounter(); stock.add(iron, 1);
        var result = plan(network, stock, false);
        assertEquals(PlanningStatus.CYCLE_UNRESOLVED, result.status());
        assertEquals(0, result.state().patternTimes().getOrDefault(p1, 0L));
        assertEquals(0, result.state().missingItems().get(x));
        assertEquals(CyclePlanningStatus.DISABLED, result.trace().cycles().getFirst().status());
    }

    @Test void disabledCyclePathDoesNotDeferAcyclicAlternatives() throws Exception {
        AEKey iron = key("defer_iron"), x = key("defer_x"), y = key("defer_y");
        var cyclic = PlannerFixtures.pattern("y_x", x, 1, y, 1L);
        var valid = PlannerFixtures.pattern("iron_x", x, 1, iron, 1L);
        var back = PlannerFixtures.pattern("x_y", y, 1, x, 1L);
        CompiledNetwork network = network(x, Map.of(iron, List.of(),
            x, List.of(PlannerFixtures.compiled(0, cyclic, x, true, ""),
                PlannerFixtures.compiled(1, valid, x, true, "")),
            y, one(PlannerFixtures.compiled(2, back, y, true, ""))));
        KeyCounter stock = new KeyCounter(); stock.add(iron, 1);
        var result = plan(network, stock, false);
        assertEquals(PlanningStatus.CYCLE_UNRESOLVED, result.status());
        assertEquals(0, result.state().patternTimes().getOrDefault(valid, 0L));
        assertEquals(0, result.state().missingItems().get(x));
        assertEquals(CyclePlanningStatus.DISABLED, result.trace().cycles().getFirst().status());
    }

    @Test void cycleComponentIsCreatedOnlyWhenNoAlternateProducerExists() throws Exception {
        AEKey x = key("only_x"), y = key("only_y");
        CompiledNetwork network = network(x, Map.of(x, one(edge(0, x, y)), y, one(edge(1, y, x))));
        var result = plan(network, new KeyCounter(), false);
        assertEquals(PlanningStatus.CYCLE_UNRESOLVED, result.status());
        assertEquals(Set.of(x, y), Set.copyOf(result.trace().cycles().getFirst().members()));
        assertEquals(0, result.state().missingItems().get(x));
        assertEquals(0, result.state().missingItems().get(y));
    }

    @Test void activeTarjanExcludesAcyclicTailFromComplexScc() throws Exception {
        AEKey a = key("complex_a"), b = key("complex_b"), c = key("complex_c"), d = key("complex_d");
        var result = analyze(network(a, Map.of(a, one(edge(0, a, b)), b, one(edge(1, b, c)),
            c, one(edge(2, c, d)), d, one(edge(3, d, b)))));
        assertEquals(Set.of(b, c, d), Set.copyOf(result.cycles().getFirst().members()));
        assertFalse(result.cycles().getFirst().members().contains(a));
    }

    private static ComponentPlanner.Outcome plan(CompiledNetwork network, KeyCounter stock, boolean enabled)
            throws Exception {
        var condensation = analyze(network);
        return new ComponentPlanner(new AcyclicCraftingSolver(), new UnsupportedCycleSolver())
            .plan(network, condensation, stock, 1, enabled, ECOCancellation.NONE);
    }

    private static CondensationGraph analyze(CompiledNetwork network) throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        return CondensationGraph.build(graph, new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE),
            ECOCancellation.NONE);
    }

    private static Fixture simpleCycle() {
        AEKey a = key("toggle_a"), b = key("toggle_b");
        return new Fixture(a, network(a, Map.of(a, one(edge(0, a, b)), b, one(edge(1, b, a)))));
    }
    private static CompiledPattern edge(int id, AEKey output, AEKey input) {
        var pattern = PlannerFixtures.pattern("p_" + id, output, 1, input, 1L);
        return PlannerFixtures.compiled(id, pattern, output, true, "");
    }
    private static List<CompiledPattern> one(CompiledPattern pattern) { return List.of(pattern); }
    private static PlannerTestKey key(String name) { return PlannerTestKey.of(name); }
    private static CompiledNetwork network(AEKey goal, Map<AEKey, List<CompiledPattern>> patterns) {
        return PlannerFixtures.network(goal, new LinkedHashMap<>(patterns));
    }
    private record Fixture(AEKey goal, CompiledNetwork network) {}
}
