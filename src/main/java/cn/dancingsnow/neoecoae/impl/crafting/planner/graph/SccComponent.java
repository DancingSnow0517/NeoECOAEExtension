package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import appeng.api.stacks.AEKey;
import java.util.List;

/** One maximal strongly-connected set returned by Tarjan. */
public record SccComponent(
    int componentId,
    List<AEKey> members,
    List<CraftingGraphEdge> internalEdges,
    boolean cyclic
) {
    public SccComponent {
        members = List.copyOf(members);
        internalEdges = List.copyOf(internalEdges);
        if (members.isEmpty()) throw new IllegalArgumentException("SCC must have at least one member");
    }
}
