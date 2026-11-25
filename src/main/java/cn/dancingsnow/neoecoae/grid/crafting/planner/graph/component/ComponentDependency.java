package cn.dancingsnow.neoecoae.grid.crafting.planner.graph.component;

import cn.dancingsnow.neoecoae.grid.crafting.planner.graph.CraftingGraphEdge;
import java.util.List;

/** One deduplicated edge in the SCC condensation DAG. */
public record ComponentDependency(int fromComponentId, int toComponentId, List<CraftingGraphEdge> relationships) {
    public ComponentDependency {

        if (fromComponentId == toComponentId) {
            throw new IllegalArgumentException("Condensation dependencies cannot be self edges");
        }
    }
}
