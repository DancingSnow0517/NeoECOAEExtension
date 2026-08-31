package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.component.CycleComponent;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.BoundedCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ECOBatchCycleSearchTest {
    @Test
    void largeTwoPatternRawMaterialLoopUsesCompactVerifiedBatches() throws Exception {
        AEKey dust = PlannerTestKey.of("batch_dust_tracked");
        AEKey crystal = PlannerTestKey.of("batch_crystal_tracked");
        AEKey certus = PlannerTestKey.of("batch_certus_tracked");
        CompiledPattern dustToCrystal = compiled(0,
            PlannerFixtures.pattern("batch_dust_to_crystal_tracked", crystal, 8,
                dust, 4L, certus, 4L), crystal);
        CompiledPattern crystalToDust = compiled(1,
            PlannerFixtures.pattern("batch_crystal_to_dust_tracked", dust, 1, crystal, 1L), dust);
        CompiledNetwork network = PlannerFixtures.network(dust, producers(
            dust, List.of(crystalToDust), crystal, List.of(dustToCrystal), certus, List.of()));
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        CycleComponent cycle = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE).cycles().getFirst();

        CycleSolveResult result = new BoundedCycleSolver().solve(
            new cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveRequest(
                cycle, Map.of(dust, 25_194_240L), Map.of(dust, 4L), cycle.outgoingDependencies(),
                new cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveRequest.PlannerOptions()),
            ECOCancellation.NONE);

        assertEquals(CycleSolveStatus.SUCCESS, result.status(), result::summary);
        assertTrue(result.patternTimes().getOrDefault(dustToCrystal.details(), 0L) > 0L);
        assertTrue(result.patternTimes().getOrDefault(crystalToDust.details(), 0L) > 0L);
        assertEquals(Map.of(dust, 4L), result.requiredSeed());
        assertTrue(result.deliverableOutputs().getOrDefault(dust, 0L) >= 25_194_240L);
        assertTrue(result.executionWitness().isEmpty());
        assertFalse(result.executionPlan().isEmpty());
        assertTrue(result.metrics().statesVisited() < 1_000L);
    }

    private static CompiledPattern compiled(int id, PlannerFixtures.Pattern pattern, AEKey output) {
        return PlannerFixtures.compiled(id, pattern, output, true, "");
    }

    private static Map<AEKey, List<CompiledPattern>> producers(Object... pairs) {
        Map<AEKey, List<CompiledPattern>> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<CompiledPattern> patterns = (List<CompiledPattern>) pairs[i + 1];
            result.put((AEKey) pairs[i], patterns);
        }
        return result;
    }
}
