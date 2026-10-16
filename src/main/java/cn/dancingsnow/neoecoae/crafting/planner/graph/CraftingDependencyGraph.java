package cn.dancingsnow.neoecoae.crafting.planner.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.core.planner.graph.DependencyGraph;
import java.util.List;
import java.util.Map;

/** AE compiler adapter for the version-independent structural planning graph. */
public final class CraftingDependencyGraph extends DependencyGraph<AEKey, CraftingGraphNode, CraftingGraphEdge> {
    public CraftingDependencyGraph(AEKey goal, Map<AEKey, CraftingGraphNode> nodes, List<CraftingGraphEdge> edges) {
        super(goal, nodes, edges);
    }
}
