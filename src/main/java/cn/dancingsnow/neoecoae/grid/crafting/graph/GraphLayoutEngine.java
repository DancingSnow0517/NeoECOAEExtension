package cn.dancingsnow.neoecoae.grid.crafting.graph;

/** Layout backend contract. Backends only produce renderer-facing immutable snapshots. */
@FunctionalInterface
public interface GraphLayoutEngine {
    GraphLayoutSnapshot layout(ClientCraftingGraph graph, long version);
}
