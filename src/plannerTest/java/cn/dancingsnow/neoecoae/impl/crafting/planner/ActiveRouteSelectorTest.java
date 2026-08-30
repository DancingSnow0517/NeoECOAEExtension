package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ActiveRouteSelector;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import appeng.api.stacks.KeyCounter;
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

        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(), new BoundedCycleSolver())
            .plan(network, condensation, new KeyCounter(), 4, false, ECOCancellation.NONE);

        assertEquals(CyclePlanningStatus.DISABLED, outcome.components().getFirst().cycleStatus());
        assertEquals(4, outcome.state().missingItems().get(goal));
    }
}
