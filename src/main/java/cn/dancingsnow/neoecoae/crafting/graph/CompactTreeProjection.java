package cn.dancingsnow.neoecoae.crafting.graph;

import cn.dancingsnow.neoecoae.core.graph.presentation.LayoutGraph;
import java.util.List;
import java.util.Set;

/** Adapts platform payloads while the core owns dependency selection, occurrence ids, limits and summaries. */
public final class CompactTreeProjection {
    public static final int DEFAULT_DEPTH = cn.dancingsnow.neoecoae.core.graph.presentation.CompactTreeProjection.DEFAULT_DEPTH;
    public static final int MAX_PROJECTED_NODES = cn.dancingsnow.neoecoae.core.graph.presentation.CompactTreeProjection.MAX_PROJECTED_NODES;
    private CompactTreeProjection() {}

    private static final cn.dancingsnow.neoecoae.core.graph.presentation.CompactTreeProjection<
        ClientCraftingGraph.Node, ClientCraftingGraph.Link, CompactTreeNode> CORE =
        new cn.dancingsnow.neoecoae.core.graph.presentation.CompactTreeProjection<>(
            new cn.dancingsnow.neoecoae.core.graph.presentation.CompactTreeProjection.Factory<>() {
                public ClientCraftingGraph.Node copyNode(int id, LayoutGraph.Node source) {
                    var node = (ClientCraftingGraph.Node) source;
                    return new ClientCraftingGraph.Node(id, node.kind(), node.label(), node.key(), node.material(),
                        node.pattern(), node.cycle(), node.cluster());
                }
                public ClientCraftingGraph.Node referenceNode(int id, LayoutGraph.Node source) {
                    var node = (ClientCraftingGraph.Node) source;
                    return new ClientCraftingGraph.Node(id, ClientCraftingGraph.Kind.REFERENCE,
                        "\u2192 " + node.label(), node.key(), node.material(), node.pattern(), node.cycle(), node.cluster());
                }
                public ClientCraftingGraph.Node folderNode(int id, LayoutGraph.Node source) {
                    return new ClientCraftingGraph.Node(id, ClientCraftingGraph.Kind.FOLDER,
                        source.label(), null, null, null, null);
                }
                public ClientCraftingGraph.Link link(int from, int to, long amount, LayoutGraph.EdgeKind kind,
                        boolean selected, int material) {
                    return new ClientCraftingGraph.Link(from, to, amount,
                        cn.dancingsnow.neoecoae.crafting.planner.snapshot.CraftingGraphSnapshot.EdgeKind.valueOf(kind.name()),
                        selected, material);
                }
                public CompactTreeNode treeNode(int id, int source, int parent, String path, int depth,
                        boolean folder, boolean reference, int hiddenNodes, int hiddenDepth,
                        boolean missing, boolean cycle, boolean unsupported) {
                    return new CompactTreeNode(id, source, parent, path, depth, folder, reference, hiddenNodes,
                        hiddenDepth, missing, cycle, unsupported);
                }
            });

    public static ClientCraftingGraph project(ClientCraftingGraph source, int maxDepth,
            Set<String> expandedPaths, Set<String> fullyExpandedPaths) {
        return project(source, maxDepth, expandedPaths, fullyExpandedPaths, Set.of());
    }
    public static ClientCraftingGraph project(ClientCraftingGraph source, int maxDepth,
            Set<String> expandedPaths, Set<String> fullyExpandedPaths, Set<String> collapsedPaths) {
        if (source.view() != ClientCraftingGraph.View.MAIN) return source;
        var result = CORE.project(source, maxDepth, expandedPaths, fullyExpandedPaths, collapsedPaths);
        return result == null ? source : ClientCraftingGraph.compact(source, result.nodes(), result.links(),
            result.treeNodes(), result.rootId());
    }
    public static List<String> pathTo(ClientCraftingGraph source, int targetSourceId) {
        if (source.view() != ClientCraftingGraph.View.MAIN) return List.of();
        return cn.dancingsnow.neoecoae.core.graph.presentation.CompactTreeProjection.pathTo(source, targetSourceId);
    }
}
