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
                // A returned/reusable seed is a real production edge. Keeping it in the normalized semantic graph
                // is what makes a feedback contract visible to Tarjan instead of presenting it as a DAG.
                for (var feedback : pattern.semantics().feedbackEdges()) {
                    nodes.putIfAbsent(feedback.returnedKey(),
                        new CraftingGraphNode(feedback.returnedKey(), network.producersOf(feedback.returnedKey())));
                    nodes.putIfAbsent(feedback.dependentOutput(),
                        new CraftingGraphNode(feedback.dependentOutput(),
                            network.producersOf(feedback.dependentOutput())));
                    CompiledInput edgeInput = pattern.inputs().stream()
                        .filter(input -> feedback.returnedKey().equals(input.remainderKey())
                            || feedback.returnedKey().equals(input.key()))
                        .findFirst()
                        .orElse(pattern.inputs().isEmpty() ? null : pattern.inputs().getFirst());
                    if (edgeInput != null) {
                        edges.add(new CraftingGraphEdge(feedback.returnedKey(), feedback.dependentOutput(),
                            pattern, edgeInput));
                    }
                    if (reachable.add(feedback.returnedKey())) work.addLast(feedback.returnedKey());
                    if (reachable.add(feedback.dependentOutput())) work.addLast(feedback.dependentOutput());
                }
            }
        }
        return new CraftingDependencyGraph(network.goal(), nodes, edges);
    }
}
