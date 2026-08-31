package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ActiveRouteSelector;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.trace.PlannerDiagnostic;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActiveRouteSelectorTest {
    @Test
    void routeSelectionMayAdvanceToAnAcyclicAlternative() throws Exception {
        var input = PlannerTestKey.of("route_enabled_input");
        var goal = PlannerTestKey.of("route_enabled_goal");
        var cyclic = PlannerFixtures.compiled(0,
            PlannerFixtures.pattern("cyclic", goal, 1, goal, 1L), goal, true, "");
        var acyclic = PlannerFixtures.compiled(1,
            PlannerFixtures.pattern("acyclic", goal, 1, input, 1L), goal, true, "");
        var network = PlannerFixtures.network(goal, Map.of(goal, List.of(cyclic, acyclic), input, List.of()));
        var universe = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);

        var selection = new ActiveRouteSelector().select(universe, ECOCancellation.NONE);

        assertTrue(selection.acyclic());
        assertEquals(1, selection.choices().get(goal));
        assertEquals(List.of(cyclic), selection.deferredCyclicCandidates());
    }

    @Test
    void disabledCycleIsReportedMissingWithoutInvokingTheSolver() throws Exception {
        var goal = PlannerTestKey.of("route_disabled_goal");
        var cyclic = PlannerFixtures.compiled(0,
            PlannerFixtures.pattern("cyclic", goal, 1, goal, 1L), goal, true, "");
        var network = PlannerFixtures.network(goal, Map.of(goal, List.of(cyclic)));
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        var condensation = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);

        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), (request, cancellation) -> {
            throw new AssertionError("cycle solver must not run when cycle planning is disabled");
        })
            .plan(network, condensation, new KeyCounter(), 4, false, ECOCancellation.NONE);

        assertEquals(PlanningStatus.CYCLE_UNRESOLVED, outcome.status());
        assertEquals(CyclePlanningStatus.DISABLED, outcome.components().getFirst().cycleStatus());
        assertEquals(4, outcome.state().missingItems().get(goal));
        assertTrue(outcome.trace().diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code() == PlannerDiagnostic.Code.CYCLE_DISABLED));
    }

    @Test
    void disabledCyclePlanningUsesAnAcyclicAlternateProducer() throws Exception {
        var input = PlannerTestKey.of("off_alt_input");
        var goal = PlannerTestKey.of("off_alt_goal");
        var cyclic = PlannerFixtures.compiled(0,
            PlannerFixtures.pattern("off_alt_cyclic", goal, 1, goal, 1L), goal, true, "");
        var acyclic = PlannerFixtures.compiled(1,
            PlannerFixtures.pattern("off_alt_acyclic", goal, 1, input, 1L), goal, true, "");
        var network = PlannerFixtures.network(goal, producers(
            goal, List.of(acyclic, cyclic), input, List.of()));

        var outcome = planDisabledWithoutCallingSolver(network, stock(input, 1L));

        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertTrue(outcome.state().missingItems().isEmpty());
        assertTrue(outcome.trace().cycles().isEmpty());
        assertTrue(outcome.components().stream().noneMatch(component -> component.type()
            == cn.dancingsnow.neoecoae.impl.crafting.planner.result.ComponentPlanningResult.Type.CYCLIC));
        assertEquals(1L, outcome.state().patternTimes().get(acyclic.details()));
    }

    @Test
    void disabledCyclePlanningSkipsACyclicCandidateEvenWhenItIsFirst() throws Exception {
        var input = PlannerTestKey.of("off_first_input");
        var goal = PlannerTestKey.of("off_first_goal");
        var cyclic = PlannerFixtures.compiled(0,
            PlannerFixtures.pattern("off_first_cyclic", goal, 1, goal, 1L), goal, true, "");
        var acyclic = PlannerFixtures.compiled(1,
            PlannerFixtures.pattern("off_first_acyclic", goal, 1, input, 1L), goal, true, "");
        var network = PlannerFixtures.network(goal, producers(
            goal, List.of(cyclic, acyclic), input, List.of()));

        var outcome = planDisabledWithoutCallingSolver(network, stock(input, 1L));

        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertTrue(outcome.state().missingItems().isEmpty());
        assertTrue(outcome.trace().cycles().isEmpty());
        assertEquals(1L, outcome.state().patternTimes().get(acyclic.details()));
        assertFalse(outcome.state().patternTimes().containsKey(cyclic.details()));
        assertTrue(outcome.trace().diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code() == PlannerDiagnostic.Code.CANDIDATE_DEFERRED_CYCLE));
    }

    @Test
    void disabledCyclePlanningMarksAnUnavoidableTwoNodeCycleAsAe2Missing() throws Exception {
        var a = PlannerTestKey.of("off_pair_a");
        var b = PlannerTestKey.of("off_pair_b");
        var g = PlannerTestKey.of("off_pair_g");
        var aFromB = PlannerFixtures.compiled(0,
            PlannerFixtures.pattern("off_pair_a_from_b", a, 1, b, 1L), a, true, "");
        var bFromA = PlannerFixtures.compiled(1, PlannerFixtures.multiOutput("off_pair_b_from_a",
            List.of(new GenericStack(b, 1), new GenericStack(g, 1)), a, 1L), b, true, "");
        var network = PlannerFixtures.network(a, producers(a, List.of(aFromB), b, List.of(bFromA)));

        var outcome = planDisabledWithoutCallingSolver(network, new KeyCounter());

        assertEquals(PlanningStatus.CYCLE_UNRESOLVED, outcome.status());
        assertEquals(CyclePlanningStatus.DISABLED, outcome.trace().cycles().getFirst().status());
        assertEquals(1L, outcome.state().missingItems().get(a));
        assertFalse(outcome.cycles().getFirst().netOutputs().isEmpty());
        assertTrue(outcome.cycles().getFirst().totalNetOutputs().isEmpty(),
            "an unresolved cycle must not report one-cycle output as its planned total");
        assertEquals(CyclePlanningStatus.DISABLED, outcome.components().stream()
            .filter(component -> component.cycleStatus() != null).findFirst().orElseThrow().cycleStatus());
    }

    @Test
    void disabledCyclePlanningMarksAnUnavoidableSelfLoopAsAe2Missing() throws Exception {
        var a = PlannerTestKey.of("off_self_a");
        var g = PlannerTestKey.of("off_self_g");
        var self = PlannerFixtures.multiOutput("off_self_growth",
            List.of(new GenericStack(a, 1), new GenericStack(g, 1)), a, 1L);
        var asA = PlannerFixtures.compiled(0, self, a, true, "");
        var network = PlannerFixtures.network(a, producers(a, List.of(asA)));

        var outcome = planDisabledWithoutCallingSolver(network, new KeyCounter());

        assertEquals(PlanningStatus.CYCLE_UNRESOLVED, outcome.status());
        assertEquals(CyclePlanningStatus.DISABLED, outcome.trace().cycles().getFirst().status());
        assertEquals(1L, outcome.state().missingItems().get(a));
    }

    @Test
    void enabledCyclePlanningStillInvokesTheSolverForAnUnavoidableScc() throws Exception {
        var a = PlannerTestKey.of("on_pair_a");
        var b = PlannerTestKey.of("on_pair_b");
        var aFromB = PlannerFixtures.compiled(0,
            PlannerFixtures.pattern("on_pair_a_from_b", a, 1, b, 1L), a, true, "");
        var bFromA = PlannerFixtures.compiled(1,
            PlannerFixtures.pattern("on_pair_b_from_a", b, 1, a, 1L), b, true, "");
        var network = PlannerFixtures.network(a, producers(a, List.of(aFromB), b, List.of(bFromA)));
        int[] calls = {0};
        var delegate = new BoundedCycleSolver();
        var planner = new ComponentPlanner(new AcyclicCraftingSolver(), (request, cancellation) -> {
            calls[0]++;
            return delegate.solve(request, cancellation);
        });

        var outcome = planner.plan(network, condensation(network), stock(b, 1L), 1, true,
            ECOCancellation.NONE);

        assertEquals(1, calls[0]);
        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertEquals(CyclePlanningStatus.SOLVED, outcome.trace().cycles().getFirst().status());
        assertTrue(outcome.state().missingItems().isEmpty());
    }

    @Test
    void productionMysteriousCellShapeUsesItsOrdinaryProducerWhenCyclesAreDisabled() throws Exception {
        var raw = PlannerTestKey.of("prod_raw");
        var mysterious = PlannerTestKey.of("prod_mysterious_cell");
        var energy = PlannerTestKey.of("prod_creative_energy_cell");
        var mysteriousFromEnergy = PlannerFixtures.compiled(0,
            PlannerFixtures.pattern("prod_mysterious_from_energy", mysterious, 1, energy, 1L),
            mysterious, true, "");
        var mysteriousFromOrdinary = PlannerFixtures.compiled(1,
            PlannerFixtures.pattern("prod_mysterious_from_ordinary", mysterious, 1, raw, 4L),
            mysterious, true, "");
        var energyFromMysterious = PlannerFixtures.compiled(2,
            PlannerFixtures.pattern("prod_energy_from_mysterious", energy, 1, mysterious, 1L),
            energy, true, "");
        var network = PlannerFixtures.network(mysterious, producers(
            raw, List.of(), mysterious, List.of(mysteriousFromEnergy, mysteriousFromOrdinary),
            energy, List.of(energyFromMysterious)));

        var outcome = planDisabledWithoutCallingSolver(network, stock(raw, 4L));

        assertEquals(PlanningStatus.SUCCESS, outcome.status());
        assertTrue(outcome.trace().cycles().isEmpty());
        assertTrue(outcome.state().missingItems().isEmpty());
        assertEquals(1L, outcome.state().patternTimes().get(mysteriousFromOrdinary.details()));
    }

    private static ComponentPlanner.Outcome planDisabledWithoutCallingSolver(CompiledNetwork network,
            KeyCounter inventory) throws Exception {
        return new ComponentPlanner(new AcyclicCraftingSolver(), (request, cancellation) -> {
            throw new AssertionError("cycle solver must not run when cycle planning is disabled");
        }).plan(network, condensation(network), inventory, 1, false, ECOCancellation.NONE);
    }

    private static CondensationGraph condensation(CompiledNetwork network) throws Exception {
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        return CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
    }

    private static KeyCounter stock(AEKey key, long amount) {
        KeyCounter stock = new KeyCounter();
        stock.add(key, amount);
        return stock;
    }

    private static Map<AEKey, List<CompiledPattern>> producers(Object... pairs) {
        Map<AEKey, List<CompiledPattern>> producers = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            @SuppressWarnings("unchecked")
            List<CompiledPattern> patterns = (List<CompiledPattern>) pairs[index + 1];
            producers.put((AEKey) pairs[index], patterns);
        }
        return producers;
    }
}
