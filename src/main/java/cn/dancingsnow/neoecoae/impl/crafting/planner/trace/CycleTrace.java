package cn.dancingsnow.neoecoae.impl.crafting.planner.trace;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.impl.crafting.planner.graph.CraftingGraphEdge;
import cn.dancingsnow.neoecoae.impl.crafting.planner.result.CyclePlanningStatus;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Complete SCC explanation for the cycle graph UI, including the solver's own answer when it ran. */
public record CycleTrace(
    int componentId,
    List<AEKey> members,
    List<CraftingGraphEdge> internalEdges,
    List<CraftingGraphEdge> externalEdges,
    Map<AEKey, Long> requiredOutputs,
    CyclePlanningStatus status,
    @Nullable CycleSolveResult solveResult
) {
    public CycleTrace(int componentId, List<AEKey> members, List<CraftingGraphEdge> internalEdges,
            Map<AEKey, Long> requiredOutputs, CyclePlanningStatus status) {
        this(componentId, members, internalEdges, List.of(), requiredOutputs, status, null);
    }

    public CycleTrace {
        members = List.copyOf(members);
        internalEdges = List.copyOf(internalEdges);
        externalEdges = List.copyOf(externalEdges);
        requiredOutputs = Map.copyOf(requiredOutputs);
    }
}
