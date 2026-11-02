package cn.dancingsnow.neoecoae.client.gui.graph;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.crafting.graph.ClientCraftingGraph;
import cn.dancingsnow.neoecoae.crafting.graph.GraphLayout;
import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class GraphVisibilityCacheTest {
    private static ClientCraftingGraph graph(int count) throws ReflectiveOperationException {
        var nodes = new HashMap<Integer, ClientCraftingGraph.Node>();
        var links = new ArrayList<ClientCraftingGraph.Link>();
        for (int i = 0; i < count; i++) {
            nodes.put(i, new ClientCraftingGraph.Node(i, ClientCraftingGraph.Kind.MATERIAL,
                "Node " + i, null, null, null, null));
            if (i > 0) links.add(new ClientCraftingGraph.Link(0, i, 1,
                CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true));
        }
        var factory = ClientCraftingGraph.class.getDeclaredMethod("synthetic", int.class, java.util.Map.class, java.util.List.class);
        factory.setAccessible(true);
        return (ClientCraftingGraph) factory.invoke(null, 0, nodes, links);
    }

    @Test
    void stationaryViewReusesQueriesButPanAndLayoutReplacementInvalidateThem() throws Exception {
        var engine = new GraphLayout(GraphLayout.Mode.COMPACT_TREE);
        var graph = graph(3);
        var layout = engine.layout(graph, 1);
        var cache = new GraphVisibilityCache();
        var area = layout.bounds().padded(1);
        var first = cache.get(layout, area);
        assertSame(first, cache.get(layout, area.padded(0)));
        assertNotSame(first, cache.get(layout, area.padded(1)));
        assertNotSame(first, cache.get(engine.layout(graph, 2), area));
        cache.clear();
        assertNotSame(first, cache.get(layout, area));
        assertEquals(3, first.nodeCount());
        assertEquals(2, first.edgeCount());
    }

    @Test
    void denseOverviewCapsLiveListsWhileKeepingAccurateVisibleCounters() throws Exception {
        var layout = new GraphLayout(GraphLayout.Mode.COMPACT_TREE).layout(graph(3001), 1);
        var visible = new GraphVisibilityCache().get(layout, layout.bounds().padded(1));
        assertEquals(GraphVisibilityCache.LIVE_LIMIT + 1, visible.nodes().size());
        assertTrue(visible.edges().isEmpty());
        assertEquals(3001, visible.nodeCount());
        assertEquals(3000, visible.edgeCount());
    }
}
