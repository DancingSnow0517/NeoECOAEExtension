package cn.dancingsnow.neoecoae.crafting.graph;

import static org.junit.jupiter.api.Assertions.*;

import cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CraftingTreeModuleTest {
    private static ClientCraftingGraph graph() {
        return ClientCraftingGraph.synthetic(
                0,
                Map.of(0, node(0), 1, node(1), 2, node(2)),
                List.of(
                        new ClientCraftingGraph.Link(0, 1, 2, CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true),
                        new ClientCraftingGraph.Link(0, 2, 3, CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT, true)));
    }

    private static ClientCraftingGraph.Node node(int id) {
        return new ClientCraftingGraph.Node(
                id, ClientCraftingGraph.Kind.MATERIAL, "Material " + id, null, null, null, null);
    }

    @Test
    void compactProjectionAndLayoutWorkWithoutOpeningAScreen() {
        var source = graph();
        var tree = CompactTreeProjection.project(source, 4, Set.of(), Set.of());
        assertTrue(tree.isCompactTree());
        assertEquals(3, source.nodes().size());
        assertEquals(2, source.links().size());
        var layout = new CompactTreeLayout().layout(tree, 1);
        assertEquals(tree.nodes().keySet(), layout.boxes().keySet());
        assertNotNull(layout.box(tree.rootId()));
        for (var link : tree.links()) {
            assertTrue(layout.edgePoints(link).size() >= 2);
            var parent = layout.box(link.fromId());
            var child = layout.box(link.toId());
            assertTrue(child.y() >= parent.y() + parent.height());
        }
        var root = layout.box(tree.rootId());
        assertTrue(layout.query(root.x(), root.y(), root.x() + root.width(), root.y() + root.height(), 0)
                .contains(root));
    }

    @Test
    void layoutCacheSurvivesViewportQueriesAndRebuildsForNewGraph() {
        var source = graph();
        var cache = new GraphLayoutCache(GraphLayout.Mode.COMPACT_TREE);
        var first = cache.get(source);
        first.query(-100, -100, 500, 500, 0);
        first.query(1000, 1000, 1500, 1500, 0);
        assertSame(first, cache.get(source));
        assertNotSame(first, cache.get(graph()));
        cache.invalidate();
        assertNotSame(first, cache.get(source));
    }
}
