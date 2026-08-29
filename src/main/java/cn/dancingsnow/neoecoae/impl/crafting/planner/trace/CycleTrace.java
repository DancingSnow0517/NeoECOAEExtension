package cn.dancingsnow.neoecoae.impl.crafting.planner.trace;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import java.util.List;
import java.util.Map;

/** Complete SCC explanation for the future cycle graph UI. */
public record CycleTrace(
    int componentId,
    List<AEKey> members,
    List<CraftingGraphEdge> internalEdges,
    Map<AEKey, Long> requiredOutputs,
    CyclePlanningStatus status
) {
    public CycleTrace {
        members = List.copyOf(members);
        internalEdges = List.copyOf(internalEdges);
        requiredOutputs = Map.copyOf(requiredOutputs);
    }
}
