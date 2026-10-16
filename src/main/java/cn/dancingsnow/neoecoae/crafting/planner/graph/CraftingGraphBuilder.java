package cn.dancingsnow.neoecoae.crafting.planner.graph;

import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.core.planner.graph.DependencyClosure;

/** Adapts the compiler's producer and special-input contracts to the shared graph compiler. */
public final class CraftingGraphBuilder {
    public CraftingDependencyGraph build(CompiledNetwork network, ECOCancellation cancellation)
            throws InterruptedException {
        var graph = DependencyClosure.build(network.goal(), network.edgeCount(), network::producersOf,
            CraftingGraphNode::new, (key, pattern) -> PatternDependencyEdges.of(key, pattern, network::producersOf),
            cancellation);
        return new CraftingDependencyGraph(graph.goal(), graph.nodes(), graph.edges());
    }
}
