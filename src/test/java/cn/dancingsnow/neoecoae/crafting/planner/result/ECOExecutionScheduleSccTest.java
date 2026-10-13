package cn.dancingsnow.neoecoae.crafting.planner.result;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ECOExecutionScheduleSccTest {
    @Test void iterativeSccMatchesRecursiveReferenceOnRandomGraphs() {
        Random random = new Random(20261013L);
        for (int trial = 0; trial < 200; trial++) {
            int size = random.nextInt(13);
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < size; i++) order.add(i);
            List<Set<Integer>> outgoing = new ArrayList<>();
            for (int from = 0; from < size; from++) {
                Collections.shuffle(order, random);
                Set<Integer> targets = new LinkedHashSet<>();
                for (int to : order) if (random.nextInt(4) == 0) targets.add(to);
                outgoing.add(targets);
            }
            Collections.shuffle(order, random);
            Set<Integer> nodes = new LinkedHashSet<>();
            for (int node : order) if (random.nextInt(5) != 0) nodes.add(node);

            assertEquals(referenceStronglyConnectedComponents(nodes, outgoing),
                ECOExecutionSchedule.stronglyConnectedComponents(nodes, outgoing),
                "trial " + trial + " nodes=" + nodes + " outgoing=" + outgoing);
        }
    }

    @Test void longChainAndCycleCompleteWithoutStackOverflow() {
        int n = 200_000;
        Set<Integer> nodes = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) nodes.add(i);

        List<Set<Integer>> chain = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Set<Integer> edges = new LinkedHashSet<>();
            if (i + 1 < n) edges.add(i + 1);
            chain.add(edges);
        }
        List<List<Integer>> components = ECOExecutionSchedule.stronglyConnectedComponents(nodes, chain);
        assertEquals(n, components.size());
        // Tarjan emits sink components first, so a chain comes back in reverse order.
        assertEquals(List.of(n - 1), components.get(0));
        assertEquals(List.of(0), components.get(n - 1));

        List<Set<Integer>> cycle = new ArrayList<>();
        for (int i = 0; i < n; i++) cycle.add(new LinkedHashSet<>(List.of((i + 1) % n)));
        List<List<Integer>> cyclic = ECOExecutionSchedule.stronglyConnectedComponents(nodes, cycle);
        assertEquals(1, cyclic.size());
        List<Integer> members = cyclic.get(0);
        assertEquals(n, members.size());
        // The root is popped last, so the single component lists nodes in reverse visit order.
        assertEquals(n - 1, (int) members.get(0));
        assertEquals(0, (int) members.get(n - 1));
    }

    /** The original recursive Tarjan, kept verbatim as the behavioural reference for the iterative version. */
    private static List<List<Integer>> referenceStronglyConnectedComponents(Set<Integer> nodes,
            List<Set<Integer>> outgoing) {
        List<List<Integer>> result = new ArrayList<>();
        Map<Integer, Integer> index = new HashMap<>(), low = new HashMap<>();
        Set<Integer> onStack = new HashSet<>();
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        int[] next = {0};
        for (int node : nodes) if (!index.containsKey(node)) referenceTarjan(node, nodes, outgoing, index, low, onStack, stack, next, result);
        return result;
    }

    private static void referenceTarjan(int v, Set<Integer> nodes, List<Set<Integer>> outgoing, Map<Integer,Integer> index,
            Map<Integer,Integer> low, Set<Integer> onStack, ArrayDeque<Integer> stack, int[] next,
            List<List<Integer>> result) {
        index.put(v, next[0]); low.put(v, next[0]++); stack.push(v); onStack.add(v);
        for (int w : outgoing.get(v)) if (nodes.contains(w)) {
            if (!index.containsKey(w)) { referenceTarjan(w,nodes,outgoing,index,low,onStack,stack,next,result); low.put(v, Math.min(low.get(v), low.get(w))); }
            else if (onStack.contains(w)) low.put(v, Math.min(low.get(v), index.get(w)));
        }
        if (low.get(v).equals(index.get(v))) { List<Integer> scc = new ArrayList<>(); int w; do { w=stack.pop(); onStack.remove(w); scc.add(w); } while (w!=v); result.add(scc); }
    }
}
