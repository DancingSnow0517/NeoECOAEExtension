package cn.dancingsnow.neoecoae.crafting.graph;

/** Compatibility entry point; all geometry is computed by the portable core. */
public final class CircularCycleLayout implements GraphLayoutEngine {
    @Override
    public GraphLayoutSnapshot layout(ClientCraftingGraph graph, long version) {
        return new GraphLayoutSnapshot(new cn.dancingsnow.neoecoae.core.graph.presentation.CircularCycleLayout().layout(graph, version));
    }
}
