package cn.dancingsnow.neoecoae.impl.crafting.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.impl.crafting.planner.route.CycleDetector;
import cn.dancingsnow.neoecoae.impl.crafting.planner.route.ReachabilityScanner;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class ECOCycleDetectorTest {
    @Test void reachableTwoNodeCycleIsStructured() throws Exception {
        var a = PlannerTestKey.of("a"); var b = PlannerTestKey.of("b");
        var ab = PlannerFixtures.pattern("b_to_a", a, 1, b, 1L);
        var ba = PlannerFixtures.pattern("a_to_b", b, 1, a, 1L);
        var map = new LinkedHashMap<appeng.api.stacks.AEKey, List<cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern>>();
        map.put(a, List.of(PlannerFixtures.compiled(0, ab, a, true, "")));
        map.put(b, List.of(PlannerFixtures.compiled(1, ba, b, true, "")));
        var network = PlannerFixtures.network(a, map);
        var reachable = new ReachabilityScanner().scan(network, ECOCancellation.NONE);
        var result = new CycleDetector().detect(network, reachable, ECOCancellation.NONE);
        assertTrue(result.cyclic());
        assertEquals(2, result.cycles().getFirst().keys().size());
        assertEquals(0L, result.cycles().getFirst().netOutputs().get(a));
        assertEquals(0L, result.cycles().getFirst().netOutputs().get(b));
    }

    @Test void cycleNetOutputsUseCompiledAmounts() throws Exception {
        var a = PlannerTestKey.of("net_a"); var b = PlannerTestKey.of("net_b");
        var ab = PlannerFixtures.pattern("a_to_b", b, 3, a, 2L);
        var ba = PlannerFixtures.pattern("b_to_a", a, 1, b, 1L);
        var map = new LinkedHashMap<appeng.api.stacks.AEKey, List<cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern>>();
        map.put(a, List.of(PlannerFixtures.compiled(0, ba, a, true, "")));
        map.put(b, List.of(PlannerFixtures.compiled(1, ab, b, true, "")));
        var network = PlannerFixtures.network(a, map);
        var reachable = new ReachabilityScanner().scan(network, ECOCancellation.NONE);
        var cycle = new CycleDetector().detect(network, reachable, ECOCancellation.NONE).cycles().getFirst();
        assertEquals(-1L, cycle.netOutputs().get(a));
        assertEquals(2L, cycle.netOutputs().get(b));
    }

    @Test void selfLoopIsStructured() throws Exception {
        var a = PlannerTestKey.of("self");
        var p = PlannerFixtures.pattern("self_loop", a, 2, a, 1L);
        var network = PlannerFixtures.network(a, java.util.Map.of(a, List.of(PlannerFixtures.compiled(0, p, a, true, ""))));
        var reachable = new ReachabilityScanner().scan(network, ECOCancellation.NONE);
        var result = new CycleDetector().detect(network, reachable, ECOCancellation.NONE);
        assertTrue(result.cyclic());
        assertEquals(List.of(a), result.cycles().getFirst().keys());
    }

    @Test void unrelatedCycleDoesNotPoisonGoal() throws Exception {
        var g = PlannerTestKey.of("g"); var a = PlannerTestKey.of("a2");
        var x = PlannerTestKey.of("x"); var y = PlannerTestKey.of("y");
        var ga = PlannerFixtures.pattern("a_to_g", g, 1, a, 1L);
        var xy = PlannerFixtures.pattern("y_to_x", x, 1, y, 1L);
        var yx = PlannerFixtures.pattern("x_to_y", y, 1, x, 1L);
        var map = new LinkedHashMap<appeng.api.stacks.AEKey, List<cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern>>();
        map.put(g, List.of(PlannerFixtures.compiled(0, ga, g, true, ""))); map.put(a, List.of());
        map.put(x, List.of(PlannerFixtures.compiled(1, xy, x, true, "")));
        map.put(y, List.of(PlannerFixtures.compiled(2, yx, y, true, "")));
        var network = PlannerFixtures.network(g, map);
        var reachable = new ReachabilityScanner().scan(network, ECOCancellation.NONE);
        assertFalse(reachable.contains(x));
        assertFalse(new CycleDetector().detect(network, reachable, ECOCancellation.NONE).cyclic());
    }
}
