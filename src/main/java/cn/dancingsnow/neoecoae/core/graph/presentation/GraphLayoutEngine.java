package cn.dancingsnow.neoecoae.core.graph.presentation;

/** Platform-independent graph geometry backend. */
@FunctionalInterface
public interface GraphLayoutEngine {
    GraphLayoutSnapshot layout(LayoutGraph graph, long version);
}
