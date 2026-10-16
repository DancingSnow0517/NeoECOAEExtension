package cn.dancingsnow.neoecoae.core.graph.presentation;

import cn.dancingsnow.neoecoae.core.graph.GraphAdjacency;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortableGraphPresentationTest {
    private record Node(int id, LayoutGraph.Kind layoutKind, String label, boolean missing) implements LayoutGraph.Node {}
    private record Link(int fromId, int toId, long amount, LayoutGraph.EdgeKind layoutKind,
            boolean selected, int materialNodeId) implements LayoutGraph.Link {}
    private record Tree(boolean folder, boolean reference) implements CompactTreeProjection.TreeNode {}
    private static final CompactTreeProjection<Node, Link, Tree> PROJECTOR = new CompactTreeProjection<>(
        new CompactTreeProjection.Factory<>() {
            public Node copyNode(int id, LayoutGraph.Node n) { return new Node(id, n.layoutKind(), n.label(), n.missing()); }
            public Node referenceNode(int id, LayoutGraph.Node n) { return new Node(id, LayoutGraph.Kind.REFERENCE, n.label(), n.missing()); }
            public Node folderNode(int id, LayoutGraph.Node n) { return new Node(id, LayoutGraph.Kind.FOLDER, n.label(), false); }
            public Link link(int from, int to, long amount, LayoutGraph.EdgeKind kind, boolean selected, int material) {
                return new Link(from, to, amount, kind, selected, material);
            }
            public Tree treeNode(int id, int source, int parent, String path, int depth, boolean folder, boolean reference,
                    int hidden, int hiddenDepth, boolean missing, boolean cycle, boolean unsupported) {
                return new Tree(folder, reference);
            }
        });

    private static Node material(int id, boolean missing) { return new Node(id, LayoutGraph.Kind.MATERIAL, "node" + id, missing); }
    private static Link link(int from, int to) { return new Link(from, to, 1, LayoutGraph.EdgeKind.PATTERN_INPUT, true, to); }

    @Test void layoutsAndSpatialQueriesWorkWithoutPlatformPayloads() {
        var graph = LayoutGraph.of(0, Map.of(0, material(0, false), 1, material(1, true)), List.of(link(0, 1)));
        for (var engine : List.of(new LayeredGraphLayout(), new CompactTreeLayout(), new CircularCycleLayout())) {
            var first = engine.layout(graph, 42);
            var second = engine.layout(graph, 43);
            assertEquals(first.boxes(), second.boxes());
            assertEquals(42, first.version());
            var box = first.box(0);
            assertTrue(first.query(box.x(), box.y(), box.x(), box.y(), 0).contains(box));
            assertEquals(List.of(graph.links().getFirst()), first.queryLinks(box.x(), box.y(), box.x(), box.y(), 0));
        }
    }

    @Test void sharedMaterialsBecomeReferencesAndCyclesTerminate() {
        var graph = LayoutGraph.of(0, Map.of(0, material(0, false), 1, material(1, false), 2, material(2, true)),
            List.of(link(0, 1), link(0, 2), link(1, 2), link(2, 0)));
        var result = PROJECTOR.project(graph, 10, Set.of(), Set.of());
        assertTrue(result.treeNodes().values().stream().anyMatch(Tree::reference));
        assertTrue(result.nodes().size() < 10);
        assertEquals(List.of("/0:0", "/0:0/2:1"), CompactTreeProjection.pathTo(graph, 2));
    }

    @Test void depthAndMissingPathFiltersKeepTheRootAndIgnoreUnreachableDeficits() {
        var graph = LayoutGraph.of(0, Map.of(0, material(0, false), 1, material(1, false),
            2, material(2, true), 3, material(3, true)), List.of(link(0, 1), link(1, 2)));
        assertEquals(Set.of(0, 1), GraphVisibility.withinDepth(graph, 0, 9, Set.of(1)));
        assertEquals(Set.of(0, 1, 2), GraphVisibility.missingPaths(graph, graph.adjacency()));
        assertEquals(Set.of(0), GraphVisibility.missingPaths(graph, GraphAdjacency.of(List.<Link>of(), Link::fromId, Link::toId)));
        var result = PROJECTOR.project(graph, 0, Set.of(), Set.of());
        assertTrue(result.treeNodes().values().stream().anyMatch(Tree::folder));
    }
}
