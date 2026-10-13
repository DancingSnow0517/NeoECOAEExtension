package cn.dancingsnow.neoecoae.core.graph;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Iterative Tarjan strongly-connected-component search. Java recursion is not used, so depth is bounded by heap.
 *
 * <p>Output order is part of the contract for callers that must preserve it:</p>
 * <ul>
 *     <li>Roots are visited in the iteration order of {@code roots}; a root reached earlier is skipped.</li>
 *     <li>Successors are requested once, when a node is entered, and are followed in their iteration order.</li>
 *     <li>Components are returned in Tarjan completion order, so every component reachable from a component is
 *     returned before it (sinks first).</li>
 *     <li>Members of a component are listed in the order they are popped from Tarjan's stack, so the node entered
 *     last comes first and the root comes last.</li>
 * </ul>
 * <p>Successor lists are not filtered. A caller that wants an induced subgraph must filter its successors.</p>
 */
public final class StronglyConnected {
    private StronglyConnected() {
    }

    private static final class Frame<N> {
        final N node;
        final int index;
        final Iterator<? extends N> successors;
        int lowlink;

        Frame(N node, int index, Iterator<? extends N> successors) {
            this.node = node;
            this.index = index;
            this.successors = successors;
            this.lowlink = index;
        }
    }

    /**
     * @param checkpoint polled before each root and before each step of the search; a throwing implementation
     *                   aborts the search with that exception
     */
    public static <N, E extends Exception> List<List<N>> components(Iterable<? extends N> roots,
            Function<? super N, ? extends Iterable<? extends N>> successors, TopoSort.Checkpoint<E> checkpoint)
            throws E {
        List<List<N>> components = new ArrayList<>();
        Object2IntOpenHashMap<N> index = new Object2IntOpenHashMap<>();
        index.defaultReturnValue(-1);
        Set<N> onStack = new HashSet<>();
        ArrayDeque<N> stack = new ArrayDeque<>();
        ArrayDeque<Frame<N>> calls = new ArrayDeque<>();
        for (N root : roots) {
            checkpoint.check();
            if (index.containsKey(root)) continue;
            calls.push(enter(root, successors, index, onStack, stack));
            while (!calls.isEmpty()) {
                checkpoint.check();
                Frame<N> frame = calls.peek();
                if (frame.successors.hasNext()) {
                    N target = frame.successors.next();
                    int targetIndex = index.getInt(target);
                    if (targetIndex < 0) {
                        calls.push(enter(target, successors, index, onStack, stack));
                    } else if (onStack.contains(target)) {
                        frame.lowlink = Math.min(frame.lowlink, targetIndex);
                    }
                    continue;
                }

                calls.pop();
                if (!calls.isEmpty()) {
                    Frame<N> parent = calls.peek();
                    parent.lowlink = Math.min(parent.lowlink, frame.lowlink);
                }
                if (frame.lowlink == frame.index) {
                    List<N> members = new ArrayList<>();
                    N member;
                    do {
                        member = stack.pop();
                        onStack.remove(member);
                        members.add(member);
                    } while (!member.equals(frame.node));
                    components.add(members);
                }
            }
        }
        return components;
    }

    private static <N> Frame<N> enter(N node, Function<? super N, ? extends Iterable<? extends N>> successors,
            Object2IntOpenHashMap<N> index, Set<N> onStack, ArrayDeque<N> stack) {
        int nodeIndex = index.size();
        index.put(node, nodeIndex);
        stack.push(node);
        onStack.add(node);
        return new Frame<>(node, nodeIndex, successors.apply(node).iterator());
    }
}
