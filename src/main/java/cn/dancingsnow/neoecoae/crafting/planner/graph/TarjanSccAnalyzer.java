package cn.dancingsnow.neoecoae.crafting.planner.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.core.graph.StronglyConnected;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Tarjan SCC analysis over a crafting dependency graph. Components follow {@link StronglyConnected} order. */
public final class TarjanSccAnalyzer {
    public List<SccComponent> analyze(CraftingDependencyGraph graph, ECOCancellation cancellation)
            throws InterruptedException {
        List<List<AEKey>> memberSets = StronglyConnected.components(graph.nodes().keySet(),
            node -> graph.outgoing(node).stream().map(CraftingGraphEdge::requiredInput).toList(),
            cancellation::checkpoint);

        List<SccComponent> result = new ArrayList<>(memberSets.size());
        int id = 0;
        for (List<AEKey> members : memberSets) {
            cancellation.checkpoint();
            Set<AEKey> memberLookup = new LinkedHashSet<>(members);
            List<CraftingGraphEdge> internal = new ArrayList<>();
            for (AEKey member : members) {
                for (CraftingGraphEdge edge : graph.outgoing(member)) {
                    if (memberLookup.contains(edge.requiredInput())) internal.add(edge);
                }
            }
            boolean cyclic = members.size() > 1 || internal.stream()
                .anyMatch(edge -> edge.producer().equals(edge.requiredInput()));
            result.add(new SccComponent(id++, members, internal, cyclic));
        }
        return result;
    }
}
