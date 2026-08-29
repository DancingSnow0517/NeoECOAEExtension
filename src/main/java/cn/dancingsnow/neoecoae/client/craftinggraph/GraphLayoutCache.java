package cn.dancingsnow.neoecoae.client.craftinggraph;

/** Explicit layout cache. Camera mutations intentionally do not appear in its key. */
public final class GraphLayoutCache {
    private final LayeredGraphLayout layout = new LayeredGraphLayout();
    private GraphLayoutSnapshot snapshot;
    private ClientCraftingGraph graph;
    private long version;

    public GraphLayoutSnapshot get(ClientCraftingGraph requested) {
        if (snapshot == null || graph != requested) {
            graph = requested;
            snapshot = layout.layout(requested, ++version);
        }
        return snapshot;
    }

    public void invalidate() {
        graph = null;
        snapshot = null;
    }
}
