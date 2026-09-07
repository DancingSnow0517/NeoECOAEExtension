package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.growth.SinglePatternGrowthCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ECOPlanMaterialValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeedbackSeedRetentionTest {
    @Test
    void feedbackRequestsPreserveStockForDirectAndDownstreamGoals() throws Exception {
        for (boolean validated : List.of(false, true)) {
            for (boolean downstream : List.of(false, true)) {
                for (long seed : List.of(1L, 9L)) {
                    for (long stored : List.of(seed, seed + 20)) {
                        var a = PlannerTestKey.of("retained_seed_a");
                        var b = PlannerTestKey.of("retained_seed_b");
                        var grow = PlannerFixtures.compiled(0,
                            PlannerFixtures.pattern("grow", a, seed + 1, a, seed), a, true, "", validated);
                        var consume = PlannerFixtures.compiled(1,
                            PlannerFixtures.pattern("consume", b, 1, a, 2L), b, true, "");
                        var goal = downstream ? b : a;
                        var network = PlannerFixtures.network(goal,
                            Map.of(a, List.of(grow), b, List.of(consume)));
                        var inventory = new KeyCounter();
                        inventory.add(a, stored);
                        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
                        var condensation = CondensationGraph.build(graph,
                            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE);
                        var outcome = new ComponentPlanner(new AcyclicCraftingSolver(),
                            SinglePatternGrowthCycleSolver.overBoundedSolver())
                            .plan(network, condensation, inventory, 1_000L, true, ECOCancellation.NONE);

                        assertEquals(PlanningStatus.SUCCESS, outcome.status());
                        assertNull(ECOPlanMaterialValidator.firstDeficit(outcome.state(), goal, 1_000L, inventory));
                        long firings = outcome.state().patternTimes().get(grow.details());
                        long consumed = downstream
                            ? outcome.state().patternTimes().get(consume.details()) * 2 : 1_000L;
                        assertEquals(stored, stored + firings - consumed,
                            "feedback stock must survive delivery: validated=" + validated
                                + ", downstream=" + downstream + ", seed=" + seed + ", stock=" + stored);
                        assertEquals(seed, outcome.state().usedItems().get(a));
                    }
                }
            }
        }
    }
}
