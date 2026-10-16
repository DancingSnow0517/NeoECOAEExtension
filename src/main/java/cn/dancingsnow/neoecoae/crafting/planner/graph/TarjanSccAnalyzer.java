package cn.dancingsnow.neoecoae.crafting.planner.graph;

import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.core.planner.graph.DependencyScc;
import java.util.List;

/** Platform component projection of the shared SCC analysis. */
public final class TarjanSccAnalyzer {
    public List<SccComponent> analyze(CraftingDependencyGraph graph, ECOCancellation cancellation)
            throws InterruptedException {
        return DependencyScc.analyze(graph, cancellation).stream().map(component ->
            new SccComponent(component.componentId(), component.members(), component.internalEdges(), component.cyclic()))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }
}
