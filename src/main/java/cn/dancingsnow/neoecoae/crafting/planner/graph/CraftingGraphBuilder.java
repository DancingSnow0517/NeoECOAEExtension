package cn.dancingsnow.neoecoae.crafting.planner.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
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
                for (CraftingGraphEdge edge : PatternDependencyEdges.of(key, pattern)) {
                    edges.add(edge);
                    if (reachable.add(edge.producer())) work.addLast(edge.producer());
                    if (reachable.add(edge.requiredInput())) work.addLast(edge.requiredInput());
                    nodes.putIfAbsent(edge.producer(),
                        new CraftingGraphNode(edge.producer(), network.producersOf(edge.producer())));
                    nodes.putIfAbsent(edge.requiredInput(),
                        new CraftingGraphNode(edge.requiredInput(), network.producersOf(edge.requiredInput())));
                }
            }
        }
        return new CraftingDependencyGraph(network.goal(), nodes, edges);
    }
}
