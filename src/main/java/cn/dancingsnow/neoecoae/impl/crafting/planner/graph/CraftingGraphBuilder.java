package cn.dancingsnow.neoecoae.impl.crafting.planner.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts the compiler's reachable closure into an explicit structural graph. */
public final class CraftingGraphBuilder {
    public CraftingDependencyGraph build(CompiledNetwork network, ECOCancellation cancellation)
            throws InterruptedException {
        Map<AEKey, CraftingGraphNode> nodes = new LinkedHashMap<>();
        List<CraftingGraphEdge> edges = new ArrayList<>(network.edgeCount());
        ArrayDeque<AEKey> work = new ArrayDeque<>();
        LinkedHashSet<AEKey> reachable = new LinkedHashSet<>();
        work.add(network.goal());
        reachable.add(network.goal());
        while (!work.isEmpty()) {
            cancellation.checkpoint();
            AEKey key = work.removeFirst();
            List<CompiledPattern> patterns = network.producersOf(key);
            nodes.put(key, new CraftingGraphNode(key, patterns));
            for (CompiledPattern pattern : patterns) {
                cancellation.checkpoint();
                for (CompiledInput input : pattern.inputs()) {
                    nodes.putIfAbsent(input.key(), new CraftingGraphNode(input.key(), network.producersOf(input.key())));
                    edges.add(new CraftingGraphEdge(key, input.key(), pattern, input));
                    if (reachable.add(input.key())) work.addLast(input.key());
                }
            }
        }
        return new CraftingDependencyGraph(network.goal(), nodes, edges);
    }
}
