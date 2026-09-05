package cn.dancingsnow.neoecoae.impl.crafting.planner.component;

import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphEdge;
import java.util.List;

/** One deduplicated edge in the SCC condensation DAG. */
public record ComponentDependency(int fromComponentId, int toComponentId, List<CraftingGraphEdge> relationships) {
    public ComponentDependency {
        relationships = List.copyOf(relationships);
        if (fromComponentId == toComponentId) {
            throw new IllegalArgumentException("Condensation dependencies cannot be self edges");
        }
    }
}
