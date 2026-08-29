package cn.dancingsnow.neoecoae.impl.crafting.planner.component;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphEdge;
import java.util.List;

/** A complete cyclic SCC, isolated from the ordinary DAG numeric solver. */
public record CycleComponent(
    int componentId,
    List<AEKey> members,
    List<CompiledPattern> patterns,
    List<CraftingGraphEdge> internalEdges,
    List<ComponentDependency> incomingDependencies,
    List<ComponentDependency> outgoingDependencies
) implements PlanningComponent {
    public CycleComponent {
        members = List.copyOf(members);
        patterns = List.copyOf(patterns);
        internalEdges = List.copyOf(internalEdges);
        incomingDependencies = List.copyOf(incomingDependencies);
        outgoingDependencies = List.copyOf(outgoingDependencies);
        if (members.isEmpty()) throw new IllegalArgumentException("Cycle component must not be empty");
    }
}
