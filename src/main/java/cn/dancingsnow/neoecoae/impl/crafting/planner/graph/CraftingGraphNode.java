package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import java.util.List;

/** Amount-free material vertex in the goal-reachable recipe graph. */
public record CraftingGraphNode(AEKey key, List<CompiledPattern> candidatePatterns) {
    public CraftingGraphNode {
        candidatePatterns = List.copyOf(candidatePatterns);
    }
}
