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
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.ExecutionCountKnowledge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;

class ECOBatchCycleSearchTest {
    @Test
    void heuristicWorkStaysBoundedAcrossProductionScaleRequests() throws Exception {
        AEKey dust = PlannerTestKey.of("scale_dust"), crystal = PlannerTestKey.of("scale_crystal");
        CompiledPattern forward = compiled(0,
            PlannerFixtures.pattern("scale_forward", crystal, 8, dust, 4L), crystal);
        CompiledPattern backward = compiled(1,
            PlannerFixtures.pattern("scale_backward", dust, 1, crystal, 1L), dust);
        CompiledNetwork network = PlannerFixtures.network(dust, producers(
            dust, List.of(backward), crystal, List.of(forward)));
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        CycleComponent cycle = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE).cycles().getFirst();

        long largestLookahead = 0L;
        for (long requested : new long[] {64L, 25_194_240L, 1_000_000_000_000L,
                2_000_000_000_000_000_000L}) {
            CycleSolveResult result = new BoundedCycleSolver().solve(
                new cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveRequest(
                    cycle, Map.of(dust, requested), Map.of(dust, 4L), cycle.outgoingDependencies(),
                    new cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveRequest.PlannerOptions()),
                ECOCancellation.NONE);
            assertEquals(CycleSolveStatus.SUCCESS, result.status(), result::summary);
            assertEquals(ExecutionCountKnowledge.EXACT, result.executionCountKnowledge());
            assertTrue(result.metrics().heuristicMacroSteps() < 256,
                "macro work must grow logarithmically, requested=" + requested);
            largestLookahead = Math.max(largestLookahead, result.metrics().lookaheadNodes());
        }
        assertTrue(largestLookahead < 16_384L, "lookahead must remain under its independent heuristic budget");
    }

    @Test
    void exhaustedHeuristicFallsBackToExactSearchAndLookaheadObservesCancellation() throws Exception {
        AEKey a = PlannerTestKey.of("budget_a"), b = PlannerTestKey.of("budget_b");
        CompiledPattern forward = compiled(0, PlannerFixtures.pattern("budget_forward", b, 2, a, 1L), b);
        CompiledPattern backward = compiled(1, PlannerFixtures.pattern("budget_backward", a, 1, b, 1L), a);
        CompiledNetwork network = PlannerFixtures.network(a, producers(a, List.of(backward), b, List.of(forward)));
        var graph = new CraftingGraphBuilder().build(network, ECOCancellation.NONE);
        CycleComponent cycle = CondensationGraph.build(graph,
            new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE), ECOCancellation.NONE).cycles().getFirst();
        var request = new cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveRequest(
            cycle, Map.of(a, 64L), Map.of(a, 1L), cycle.outgoingDependencies(),
            new cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveRequest.PlannerOptions());

        CycleSolveResult fallback = new BoundedCycleSolver(1, 1, 1, 1).solve(request, ECOCancellation.NONE);
        assertEquals(CycleSolveStatus.SUCCESS, fallback.status(), fallback::summary);
        assertTrue(fallback.metrics().heuristicBudgetExhausted());

        AtomicInteger checkpoints = new AtomicInteger();
        boolean cancelled = false;
        try {
            new BoundedCycleSolver().solve(request, () -> {
                if (checkpoints.incrementAndGet() >= 4) throw new InterruptedException("test cancellation");
            });
        } catch (InterruptedException expected) {
            cancelled = true;
        }
        assertTrue(cancelled);
        assertTrue(checkpoints.get() < 32, "lookahead must checkpoint internally");
    }

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
