package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.UnsupportedCycleSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CondensationGraph;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphBuilder;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.TarjanSccAnalyzer;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.AcyclicCraftingSolver;
import cn.dancingsnow.neoecoae.impl.crafting.planner.solve.ComponentPlanner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class ECOPlannerBenchmarkTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "ECO_PLANNER_BENCHMARK", matches = "true")
    void benchmarkLinearChainsByGraphSizeAndAmount() throws Exception {
        int[] sizes = {1_000, 5_000, 10_000, 20_000};
        long[] amounts = {64L, 64_000L, 64_000_000L, 1_000_000_000L};
        for (int size : sizes) {
            long compileStart = System.nanoTime();
            var fixture = chain(size);
            long compileNanos = System.nanoTime() - compileStart;
            long graphStart = System.nanoTime();
            var graph = new CraftingGraphBuilder().build(fixture.network, ECOCancellation.NONE);
            long graphNanos = System.nanoTime() - graphStart;
            long tarjanStart = System.nanoTime();
            var sccs = new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE);
            long tarjanNanos = System.nanoTime() - tarjanStart;
            long condensationStart = System.nanoTime();
            var condensation = CondensationGraph.build(graph, sccs, ECOCancellation.NONE);
            long condensationNanos = System.nanoTime() - condensationStart;
            for (long amount : amounts) {
                KeyCounter stock = new KeyCounter(); stock.add(fixture.leaf, amount);
                long solveStart = System.nanoTime();
                var solved = new ComponentPlanner(new AcyclicCraftingSolver(), new UnsupportedCycleSolver())
                    .plan(fixture.network, condensation, stock, amount, false, ECOCancellation.NONE);
                long solveNanos = System.nanoTime() - solveStart;
                assertEquals(PlanningStatus.SUCCESS, solved.status());
                System.out.printf("ECO_BENCH nodes=%d amount=%d fixtureMs=%.3f graphMs=%.3f tarjanMs=%.3f condensationMs=%.3f solveMs=%.3f patterns=%d edges=%d%n",
                    size, amount, compileNanos / 1_000_000.0, graphNanos / 1_000_000.0,
                    tarjanNanos / 1_000_000.0, condensationNanos / 1_000_000.0,
                    solveNanos / 1_000_000.0, fixture.network.reachablePatternCount(), fixture.network.edgeCount());
            }
        }
    }

    private static Fixture chain(int size) {
        List<AEKey> keys = new ArrayList<>(size + 1);
        for (int i = 0; i <= size; i++) keys.add(PlannerTestKey.of("bench_" + size + "_" + i));
        Map<AEKey, List<CompiledPattern>> patterns = new LinkedHashMap<>();
        patterns.put(keys.getFirst(), List.of());
        for (int i = 1; i <= size; i++) {
            var pattern = PlannerFixtures.pattern("bench_pattern_" + i, keys.get(i), 1, keys.get(i - 1), 1L);
            patterns.put(keys.get(i), List.of(PlannerFixtures.compiled(i - 1, pattern, keys.get(i), true, "")));
        }
        return new Fixture(PlannerFixtures.network(keys.getLast(), patterns), keys.getFirst());
    }
    private record Fixture(cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork network, AEKey leaf) {}
}
