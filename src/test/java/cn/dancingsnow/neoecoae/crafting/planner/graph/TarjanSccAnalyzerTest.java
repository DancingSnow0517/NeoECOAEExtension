package cn.dancingsnow.neoecoae.crafting.planner.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TarjanSccAnalyzerTest {
    @Test
    void zeroDfsIndexIsAVisitedNodeInACycle() throws InterruptedException {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), c = mock(AEKey.class);
        var graph = graph(List.of(a, b, c), List.of(edge(a, b), edge(b, c), edge(c, a)));
        var components = new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE);
        assertEquals(1, components.size());
        assertEquals(Set.of(a, b, c), Set.copyOf(components.getFirst().members()));
        assertTrue(components.getFirst().cyclic());
    }

    @Test
    void separatesSelfLoopAndAcyclicAndDisconnectedNodes() throws InterruptedException {
        AEKey a = mock(AEKey.class), b = mock(AEKey.class), c = mock(AEKey.class), d = mock(AEKey.class);
        var graph = graph(List.of(a, b, c, d), List.of(edge(a, b), edge(b, b), edge(c, b)));
        var components = new TarjanSccAnalyzer().analyze(graph, ECOCancellation.NONE);
        assertEquals(4, components.size());
        assertEquals(Set.of(b), components.stream().filter(SccComponent::cyclic)
                .flatMap(component -> component.members().stream()).collect(Collectors.toSet()));
        assertEquals(Set.of(a, b, c, d), components.stream()
                .flatMap(component -> component.members().stream()).collect(Collectors.toSet()));
    }

    private static CraftingGraphEdge edge(AEKey from, AEKey to) {
        return new CraftingGraphEdge(from, to, null, null);
    }

    private static CraftingDependencyGraph graph(List<AEKey> keys, List<CraftingGraphEdge> edges) {
        var nodes = new LinkedHashMap<AEKey, CraftingGraphNode>();
        for (AEKey key : keys) nodes.put(key, new CraftingGraphNode(key, List.of()));
        return new CraftingDependencyGraph(keys.getFirst(), nodes, edges);
    }
}
