package cn.dancingsnow.neoecoae.crafting.graph;

/** Compatibility entry point for the portable condensation-DAG layout. */
public final class CycleClusterLayout implements GraphLayoutEngine {
    @Override
    public GraphLayoutSnapshot layout(ClientCraftingGraph graph, long version) {
        return new GraphLayoutSnapshot(new cn.dancingsnow.neoecoae.core.graph.presentation.CycleClusterLayout()
            .layout(graph, version));
    }
}
