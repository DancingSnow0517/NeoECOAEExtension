package cn.dancingsnow.neoecoae.client.craftinggraph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dancingsnow.neoecoae.impl.crafting.planner.snapshot.CraftingGraphSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CraftingGraphLayoutTest {
    private static final CraftingGraphSnapshot.EdgeKind INPUT = CraftingGraphSnapshot.EdgeKind.PATTERN_INPUT;

    @Test void simpleChainGetsMonotonicLayers() {
        var layout = layout(graph(4, List.of(link(0, 1), link(1, 2), link(2, 3))));
        assertTrue(layout.box(0).x() < layout.box(1).x());
        assertTrue(layout.box(1).x() < layout.box(2).x());
    }

    @Test void diamondSharesOneDependencyNode() {
        var graph = graph(4, List.of(link(0, 1), link(0, 2), link(1, 3), link(2, 3)));
        assertEquals(4, graph.nodes().size());
        assertEquals(Set.of(1, 2), graph.upstream(3));
    }

    @Test void multipleInputsRemainDistinct() {
        var graph = graph(4, List.of(link(0, 1), link(0, 2), link(0, 3)));
        assertEquals(3, graph.downstream(0).size());
    }

    @Test void byproductRelationshipIsRetained() {
        var byproduct = new ClientCraftingGraph.Link(0, 1, 4, CraftingGraphSnapshot.EdgeKind.BYPRODUCT, true);
        assertEquals(CraftingGraphSnapshot.EdgeKind.BYPRODUCT, graph(2, List.of(byproduct)).links().getFirst().kind());
    }

    @Test void missingMaterialStatusIsExplicit() {
        var material = new CraftingGraphSnapshot.MaterialNode(0, null, 8, 0, 0, 8,
            CraftingGraphSnapshot.MaterialStatus.MISSING);
        assertEquals(CraftingGraphSnapshot.MaterialStatus.MISSING, material.status());
    }

    @Test void rejectedCandidatesAreHiddenUnlessAdvanced() {
        var rejected = new CraftingGraphSnapshot.PatternNode(0, "rejected", List.of(), List.of(), 0,
            CraftingGraphSnapshot.CandidateStatus.REJECTED, "MISSING", -1);
        var snapshot = new CraftingGraphSnapshot(-1, List.of(), List.of(rejected), List.of(), List.of(),
            new CraftingGraphSnapshot.Summary("MISSING_ITEMS", 0, 1, 0, 0, 0));
        assertTrue(ClientCraftingGraph.main(snapshot, false).nodes().isEmpty());
        assertEquals(1, ClientCraftingGraph.main(snapshot, true).nodes().size());
    }

    @Test void oneCycleGroupActsAsOneLayoutNode() {
        var cycle = cycleNode(1);
        var graph = ClientCraftingGraph.synthetic(cycle.id(), Map.of(cycle.id(), cycle), List.of());
        assertEquals(1, layout(graph).boxes().size());
    }

    @Test void twoCycleGroupsRemainSeparate() {
        var one = cycleNode(1);
        var two = cycleNode(2);
        var graph = ClientCraftingGraph.synthetic(one.id(), Map.of(one.id(), one, two.id(), two),
            List.of(link(one.id(), two.id())));
        assertEquals(2, layout(graph).boxes().size());
    }

    @Test void cycleFocusCanEnterAndExitWithoutPlannerObjects() {
        var cycle = new CraftingGraphSnapshot.CycleGroup(7, List.of(), List.of(), "SOLVED", List.of(), List.of(),
            List.of(), List.of(), List.of());
        var snapshot = new CraftingGraphSnapshot(-1, List.of(), List.of(), List.of(), List.of(cycle),
            new CraftingGraphSnapshot.Summary("SUCCESS", 0, 0, 0, 1, 0));
        assertEquals(ClientCraftingGraph.View.CYCLE_FOCUS, ClientCraftingGraph.cycle(snapshot, 7, false).view());
        assertEquals(ClientCraftingGraph.View.MAIN, ClientCraftingGraph.main(snapshot, false).view());
    }

    @Test void cycleFocusLayoutTerminatesOnInternalCycle() {
        var cyclic = graph(3, List.of(link(0, 1), link(1, 2), link(2, 0)));
        assertEquals(3, layout(cyclic).boxes().size());
    }

    @Test void oneThousandNodeGraphUsesViewportCulling() { assertCulled(1_000); }
    @Test void tenThousandNodeGraphUsesViewportCulling() { assertCulled(10_000); }
    @Test void twentyThousandNodeGraphUsesViewportCulling() { assertCulled(20_000); }

    @Test void cameraOnlyChangesDoNotRecomputeLayout() {
        var graph = chain(100);
        var cache = new GraphLayoutCache();
        var first = cache.get(graph);
        // Camera state is deliberately absent from GraphLayoutCache.
        var second = cache.get(graph);
        assertSame(first, second);
        assertEquals(first.version(), second.version());
    }

    @Test void collapseAndExpandInvalidateToNewLayout() {
        var graph = chain(20);
        var cache = new GraphLayoutCache();
        var full = cache.get(graph);
        var collapsed = graph.limited(0, 2, Set.of(1));
        var limited = cache.get(collapsed);
        assertNotSame(full, limited);
        assertTrue(limited.boxes().size() < full.boxes().size());
    }

    @Test void graphClassesHaveNoEmiDependency() {
        for (Class<?> type : List.of(ClientCraftingGraph.class, LayeredGraphLayout.class,
                GraphLayoutSnapshot.class, GraphLayoutCache.class)) {
            assertFalse(type.getName().startsWith("dev.emi"));
            for (var field : type.getDeclaredFields()) assertFalse(field.getType().getName().startsWith("dev.emi"));
        }
    }

    private static void assertCulled(int size) {
        var graph = chain(size);
        var layout = layout(graph);
        var visible = layout.query(-10, -10, 800, 300, 0);
        var visibleEdges = layout.queryLinks(-10, -10, 800, 300, 0);
        assertTrue(visible.size() < 20, "visible nodes=" + visible.size());
        assertTrue(visibleEdges.size() < 20, "visible edges=" + visibleEdges.size());
        assertEquals(size, layout.boxes().size());
        System.out.printf(Locale.ROOT, "ECO graph %,d: layout %.3f ms, visible nodes %d, visible edges %d%n",
            size, layout.layoutNanos() / 1_000_000.0, visible.size(), visibleEdges.size());
    }

    private static ClientCraftingGraph chain(int size) {
        List<ClientCraftingGraph.Link> links = new ArrayList<>(size - 1);
        for (int i = 0; i < size - 1; i++) links.add(link(i, i + 1));
        return graph(size, links);
    }

    private static ClientCraftingGraph graph(int size, List<ClientCraftingGraph.Link> links) {
        Map<Integer, ClientCraftingGraph.Node> nodes = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            nodes.put(i, new ClientCraftingGraph.Node(i, ClientCraftingGraph.Kind.MATERIAL, "node-" + i,
                null, null, null, null));
        }
        return ClientCraftingGraph.synthetic(0, nodes, links);
    }

    private static ClientCraftingGraph.Node cycleNode(int componentId) {
        int id = ClientCraftingGraph.cycleVisualId(componentId);
        var cycle = new CraftingGraphSnapshot.CycleGroup(componentId, List.of(), List.of(), "SOLVED", List.of(),
            List.of(), List.of(), List.of(), List.of());
        return new ClientCraftingGraph.Node(id, ClientCraftingGraph.Kind.CYCLE_GROUP, "Cycle #" + componentId,
            null, null, null, cycle);
    }

    private static ClientCraftingGraph.Link link(int from, int to) {
        return new ClientCraftingGraph.Link(from, to, 1, INPUT, true);
    }

    private static GraphLayoutSnapshot layout(ClientCraftingGraph graph) {
        return new LayeredGraphLayout().layout(graph, 1);
    }
}
