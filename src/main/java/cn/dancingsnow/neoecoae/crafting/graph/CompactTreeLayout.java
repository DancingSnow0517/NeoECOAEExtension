package cn.dancingsnow.neoecoae.crafting.graph;

/** Compatibility entry point; all geometry is computed by the portable core. */
public final class CompactTreeLayout implements GraphLayoutEngine {
    public static final float LEVEL_GAP = cn.dancingsnow.neoecoae.core.graph.presentation.CompactTreeLayout.LEVEL_GAP;
    public static final float SIBLING_GAP = cn.dancingsnow.neoecoae.core.graph.presentation.CompactTreeLayout.SIBLING_GAP;
    static float nodeWidth(ClientCraftingGraph graph, int id) {
        return cn.dancingsnow.neoecoae.core.graph.presentation.CompactTreeLayout.nodeWidth(graph, id);
    }
    static float nodeHeight(ClientCraftingGraph graph, int id) {
        return cn.dancingsnow.neoecoae.core.graph.presentation.CompactTreeLayout.nodeHeight(graph, id);
    }
    @Override
    public GraphLayoutSnapshot layout(ClientCraftingGraph graph, long version) {
        if (!graph.isCompactTree() && graph.view() == ClientCraftingGraph.View.MAIN) {
            ClientCraftingGraph projected = CompactTreeProjection.project(graph,
                CompactTreeProjection.DEFAULT_DEPTH, java.util.Set.of(), java.util.Set.of());
            if (projected != graph) return layout(projected, version);
        }
        return new GraphLayoutSnapshot(new cn.dancingsnow.neoecoae.core.graph.presentation.CompactTreeLayout().layout(graph, version));
    }
}
