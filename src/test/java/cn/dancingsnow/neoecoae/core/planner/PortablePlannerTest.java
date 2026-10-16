package cn.dancingsnow.neoecoae.core.planner;

import static org.junit.jupiter.api.Assertions.*;
import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.core.planner.graph.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PortablePlannerTest {
    record Edge(String producer, String requiredInput) implements DependencyGraph.Edge<String> {}
    @Test void closureAndSccUseCallerKeysAndRetainEdgeIdentity() throws Exception {
        var forward = new Edge("A", "B");
        var reverse = new Edge("B", "A");
        Map<String, List<Edge>> producers = Map.of("A", List.of(forward), "B", List.of(reverse));
        var graph = DependencyClosure.build("A", 2, key -> producers.getOrDefault(key, List.of()),
            (key, recipes) -> key, (key, recipe) -> List.of(recipe), Cancellation.NONE);
        var components = DependencyScc.analyze(graph, Cancellation.NONE);
        assertEquals(Set.of("A", "B"), graph.nodes().keySet());
        assertEquals(1, components.size());
        assertTrue(components.getFirst().cyclic());
        assertEquals(Set.of(forward, reverse), Set.copyOf(components.getFirst().internalEdges()));
        assertSame(forward, graph.outgoing("A").getFirst());
    }
    @Test void exactCounterRetainsWideAndUnboundedStockWhileCopiesAreIndependent() {
        var counter = new PlannerCounter<String>();
        counter.add("finite", PlannerAmount.of(Long.MAX_VALUE).add(1));
        counter.setUnbounded("infinite");
        var copy = counter.copy();
        var requested = PlannerAmount.of(Long.MAX_VALUE).multiply(7);
        assertEquals(requested, copy.available("infinite", requested));
        copy.remove("infinite", requested);
        assertTrue(copy.isUnbounded("infinite"));
        copy.remove("finite", PlannerAmount.ONE);
        assertEquals(PlannerAmount.of(Long.MAX_VALUE), copy.get("finite"));
        assertEquals(PlannerAmount.of(Long.MAX_VALUE).add(1), counter.get("finite"));
        assertThrows(IllegalArgumentException.class, () -> copy.remove("finite", requested));
    }
}
