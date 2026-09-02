package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.AcyclicComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.route.AcyclicRoutePlan;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Test void retryRebuildsTopologyForAnAlternateWithANewDependency() throws Exception {
        var leaf = PlannerTestKey.of("retry_topology_leaf");
        var missing = PlannerTestKey.of("retry_topology_missing");
        var energy = PlannerTestKey.of("retry_topology_energy");
        var middle = PlannerTestKey.of("retry_topology_middle");
        var goal = PlannerTestKey.of("retry_topology_goal");
        var goalPattern = PlannerFixtures.pattern("retry_topology_goal", goal, 1, middle, 1L);
        var rejected = PlannerFixtures.pattern("retry_topology_rejected", middle, 1, missing, 1L);
        var alternate = PlannerFixtures.pattern("retry_topology_alternate", middle, 1, energy, 1L);
        var energyPattern = PlannerFixtures.pattern("retry_topology_energy", energy, 1, leaf, 1L);
        var network = network(goal, Map.of(
            goal, List.of(cp(0, goalPattern, goal, true)),
            middle, List.of(cp(1, rejected, middle, true), cp(2, alternate, middle, true)),
            energy, List.of(cp(3, energyPattern, energy, true)),
            missing, List.of(),
            leaf, List.of()));

        // This is valid for the rejected candidate but stale for the alternate: energy appears before the
        // middle pattern that starts demanding it. The solver must rebuild the route after the retry.
        var staleRoute = new AcyclicRoutePlan(List.of(goal, energy, middle, leaf, missing));
        var solved = new AcyclicCraftingSolver().solve(
            network, staleRoute, stock(leaf, 1), 1, ECOCancellation.NONE);

        assertEquals(PlanningStatus.SUCCESS, solved.status());
        assertEquals(0L, solved.state().patternTimes().getOrDefault(rejected, 0L));
        assertEquals(1L, solved.state().patternTimes().get(alternate));
        assertEquals(1L, solved.state().patternTimes().get(energyPattern));
        assertEquals(1L, solved.state().usedItems().get(leaf));
    }

    @Test void overflowedRejectedCandidateDoesNotChangeProducerSelection() throws Exception {
        var hugeLeaf = PlannerTestKey.of("retry_wide_leaf"); var usableLeaf = PlannerTestKey.of("retry_usable_leaf");
        var goal = PlannerTestKey.of("retry_wide_goal");
        var rejected = PlannerFixtures.pattern("retry_huge", goal, 1, hugeLeaf, Long.MAX_VALUE);
        var usable = PlannerFixtures.pattern("retry_usable", goal, 1, usableLeaf, 1L);
        var solved = solve(network(goal, Map.of(hugeLeaf, List.of(), usableLeaf, List.of(),
            goal, List.of(cp(0, rejected, goal, true), cp(1, usable, goal, true)))),
            stock(usableLeaf, 2), 2);

        assertEquals(PlanningStatus.SUCCESS, solved.status());
        assertEquals(2L, solved.state().plannerPatternTimes().get(usable).longValueExact());
        assertTrue(!solved.state().plannerPatternTimes().containsKey(rejected));
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

    @Test void multiplicationOverflowCompletesPlanningAndReportsExecutionBoundary() throws Exception {
        var a = PlannerTestKey.of("overflow_a"); var g = PlannerTestKey.of("overflow_g");
        var p = PlannerFixtures.pattern("huge", g, 1, a, Long.MAX_VALUE);
        var solved = solve(network(g, Map.of(a, List.of(), g, List.of(cp(0, p, g, true)))), new KeyCounter(), 2);
        assertEquals(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, solved.status());
        assertEquals("18446744073709551614", solved.state().missingAmounts().get(a).toString());
        assertTrue(solved.trace().diagnostics().stream().anyMatch(d ->
            d.code() == cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlannerDiagnostic.Code
                .EXECUTION_AMOUNT_UNREPRESENTABLE
                && d.message().contains("amount=18446744073709551614")));
    }

    @Test void overflowedDemandContinuesThroughSeveralRecipeLayersExactly() throws Exception {
        var leaf = PlannerTestKey.of("wide_leaf"); var middle = PlannerTestKey.of("wide_middle");
        var inner = PlannerTestKey.of("wide_inner"); var goal = PlannerTestKey.of("wide_goal");
        var innerPattern = PlannerFixtures.pattern("wide_inner", inner, 1, middle, Long.MAX_VALUE);
        var middlePattern = PlannerFixtures.pattern("wide_middle", middle, 1, leaf, 3L);
        var goalPattern = PlannerFixtures.pattern("wide_goal", goal, 1, inner, 2L);
        var network = network(goal, Map.of(leaf, List.of(), middle, List.of(cp(0, middlePattern, middle, true)),
            inner, List.of(cp(1, innerPattern, inner, true)), goal, List.of(cp(2, goalPattern, goal, true))));

        var solved = solve(network, new KeyCounter(), 2);
        assertEquals(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, solved.status());
        assertEquals("36893488147419103228", solved.state().plannerDemands().get(middle).toString());
        assertEquals("110680464442257309684", solved.state().plannerDemands().get(leaf).toString());
        assertTrue(solved.state().plannerPatternTimes().containsKey(innerPattern));
    }

    @Test void wideDemandStillUsesLongInventoryBeforeReportingTheExactDeficit() throws Exception {
        var leaf = PlannerTestKey.of("wide_stock_leaf"); var goal = PlannerTestKey.of("wide_stock_goal");
        var pattern = PlannerFixtures.pattern("wide_stock", goal, 1, leaf, Long.MAX_VALUE);
        var solved = solve(network(goal, Map.of(leaf, List.of(), goal, List.of(cp(0, pattern, goal, true)))),
            stock(leaf, 5), 2);

        assertEquals(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, solved.status());
        assertEquals(5L, solved.state().usedItems().get(leaf));
        assertEquals("18446744073709551609", solved.state().missingAmounts().get(leaf).toString());
    }

    @Test void executionScheduleOrdersADagProducerBeforeAReversedConsumer() {
        AEKey raw = PlannerTestKey.of("schedule_raw");
        AEKey middle = PlannerTestKey.of("schedule_middle");
        AEKey goal = PlannerTestKey.of("schedule_goal");
        var producer = PlannerFixtures.pattern("schedule_producer", middle, 1, raw, 1L);
        var consumer = PlannerFixtures.pattern("schedule_consumer", goal, 1, middle, 1L);

        var schedule = ECOExecutionSchedule.from(
            List.of(scheduleComponent(0, middle, producer), scheduleComponent(1, goal, consumer)), List.of(1, 0));

        assertEquals(List.of(producer, consumer), schedule.phases().stream()
            .map(phase -> phase.patternSet().iterator().next()).toList());
        assertEquals(List.of(new ECOExecutionSchedule.PhaseDependency(0, 1)), schedule.dependencies());
    }

    @Test void executionScheduleDoesNotTreatAReturnedInputAsAProducer() {
        AEKey raw = PlannerTestKey.of("remainder_raw");
        AEKey tool = PlannerTestKey.of("remainder_tool");
        AEKey filler = PlannerTestKey.of("remainder_filler");
        AEKey firstGoal = PlannerTestKey.of("remainder_first_goal");
        AEKey secondGoal = PlannerTestKey.of("remainder_second_goal");
        var returningConsumer = new PlannerFixtures.Pattern("returning_consumer",
            new IPatternDetails.IInput[] { new PlannerFixtures.Input(tool, 1L, true) },
            List.of(new GenericStack(firstGoal, 1L)));
        var trueProducer = PlannerFixtures.multiOutput("true_tool_producer",
            List.of(new GenericStack(filler, 1L), new GenericStack(tool, 1L)), raw, 1L);
        var secondConsumer = PlannerFixtures.pattern("second_tool_consumer", secondGoal, 1L, tool, 1L);

        var schedule = ECOExecutionSchedule.from(List.of(
            scheduleComponent(0, firstGoal, returningConsumer),
            scheduleComponent(1, filler, trueProducer),
            scheduleComponent(2, secondGoal, secondConsumer)), List.of(0, 1, 2));

        int returningPhase = schedulePhaseOf(schedule, returningConsumer);
        int producerPhase = schedulePhaseOf(schedule, trueProducer);
        int secondConsumerPhase = schedulePhaseOf(schedule, secondConsumer);
        assertEquals(0, producerPhase);
        assertEquals(2, schedule.dependencies().size());
        assertFalse(schedule.dependencies().contains(
            new ECOExecutionSchedule.PhaseDependency(returningPhase, secondConsumerPhase)));
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
    private static ComponentPlanningResult scheduleComponent(int id, AEKey requiredOutput,
            IPatternDetails pattern) {
        return new ComponentPlanningResult(id, ComponentPlanningResult.Type.ACYCLIC,
            ComponentPlanningResult.Status.PLANNED, Map.of(requiredOutput, 1L), Set.of(pattern),
            null, null, Map.of(), null, null);
    }
    private static int schedulePhaseOf(ECOExecutionSchedule schedule, IPatternDetails pattern) {
        for (int i = 0; i < schedule.phases().size(); i++) {
            if (schedule.phases().get(i).patternSet().contains(pattern)) return i;
        }
        throw new AssertionError("Pattern has no execution phase: " + pattern);
    }
    private static KeyCounter stock(AEKey key, long amount) { var stock = new KeyCounter(); stock.add(key, amount); return stock; }
}
