package cn.dancingsnow.neoecoae.client.craftinggraph;

/** Explicit layout cache. Camera mutations intentionally do not appear in its key. */
public final class GraphLayoutCache {
    private final GraphLayout layout;
    private GraphLayoutSnapshot snapshot;
    private ClientCraftingGraph graph;
    private long version;

    public GraphLayoutCache() {
        this(GraphLayout.Mode.fromSystemProperty());
    }

    public GraphLayoutCache(GraphLayout.Mode mode) {
        this.layout = new GraphLayout(mode);
    }

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
