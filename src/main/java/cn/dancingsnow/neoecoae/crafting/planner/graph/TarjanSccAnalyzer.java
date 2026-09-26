package cn.dancingsnow.neoecoae.crafting.planner.graph;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Iterative Tarjan SCC analysis. Runtime and memory are O(V + E), without Java recursion. */
public final class TarjanSccAnalyzer {
    private static final class Frame {
        final AEKey node;
        final AEKey parent;
        final List<CraftingGraphEdge> edges;
        int nextEdge;

        Frame(AEKey node, AEKey parent, List<CraftingGraphEdge> edges) {
            this.node = node;
            this.parent = parent;
            this.edges = edges;
        }
    }

    public List<SccComponent> analyze(CraftingDependencyGraph graph, ECOCancellation cancellation)
            throws InterruptedException {
        var index = new Object2IntOpenHashMap<AEKey>(graph.nodes().size());
        // DFS indices start at zero; -1 means the node has not been visited.
        index.defaultReturnValue(-1);
        var lowlink = new Object2IntOpenHashMap<AEKey>(graph.nodes().size());
        ArrayDeque<AEKey> tarjanStack = new ArrayDeque<>();
        Set<AEKey> onStack = new HashSet<>();
        List<List<AEKey>> memberSets = new ArrayList<>();
        int nextIndex = 0;

        for (AEKey root : graph.nodes().keySet()) {
            cancellation.checkpoint();
            if (index.containsKey(root)) continue;
            ArrayDeque<Frame> dfs = new ArrayDeque<>();
            index.put(root, nextIndex);
            lowlink.put(root, nextIndex++);
            tarjanStack.push(root);
            onStack.add(root);
            dfs.push(new Frame(root, null, graph.outgoing(root)));

            while (!dfs.isEmpty()) {
                cancellation.checkpoint();
                Frame frame = dfs.peek();
                if (frame.nextEdge < frame.edges.size()) {
                    AEKey target = frame.edges.get(frame.nextEdge++).requiredInput();
                    int targetIndex = index.getInt(target);
                    if (targetIndex < 0) {
                        index.put(target, nextIndex);
                        lowlink.put(target, nextIndex++);
                        tarjanStack.push(target);
                        onStack.add(target);
                        dfs.push(new Frame(target, frame.node, graph.outgoing(target)));
                    } else if (onStack.contains(target)) {
                        lowlink.put(frame.node, Math.min(lowlink.getInt(frame.node), targetIndex));
                    }
                    continue;
                }

                dfs.pop();
                if (frame.parent != null) {
                    lowlink.put(frame.parent, Math.min(lowlink.getInt(frame.parent), lowlink.getInt(frame.node)));
                }
                if (lowlink.getInt(frame.node) == index.getInt(frame.node)) {
                    List<AEKey> members = new ArrayList<>();
                    AEKey member;
                    do {
                        member = tarjanStack.pop();
                        onStack.remove(member);
                        members.add(member);
                    } while (!member.equals(frame.node));
                    memberSets.add(List.copyOf(members));
                }
            }
        }

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
        return List.copyOf(result);
    }
}
