package cn.dancingsnow.neoecoae.crafting.graph;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientCraftingGraphAdjacencyTest {
    @Test
    void clientGraphAccessorsDelegateToItsAdjacency() {
        var graph = ClientCraftingGraph.synthetic(0, Map.of(0, node(0), 1, node(1), 2, node(2)), List.of(
            new ClientCraftingGraph.Link(0, 1, 2, CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true),
            new ClientCraftingGraph.Link(0, 2, 3, CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true)));
        assertEquals(List.of(1, 2), List.copyOf(graph.downstream(0)));
        assertEquals(List.of(0), List.copyOf(graph.upstream(1)));
        assertEquals(graph.adjacency().neighborhood(1), graph.neighborhood(1));
        assertEquals(List.of(0), List.copyOf(graph.neighborhood(1)));
    }

    private static ClientCraftingGraph.Node node(int id) {
        return new ClientCraftingGraph.Node(id, ClientCraftingGraph.Kind.MATERIAL, "Material " + id, null, null,
            null, null);
    }
}
