package cn.dancingsnow.neoecoae.core.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * Kahn's topological sort over an explicit node collection.
 *
 * <p>Semantics relied on by callers that must preserve output order:</p>
 * <ul>
 *     <li>Nodes are identified by {@code equals}/{@code hashCode}. Null nodes are not supported.</li>
 *     <li>Duplicate entries in {@code nodes} collapse to one node; the first occurrence fixes its position
 *     in the initial ready order and in {@link Result#remaining()}.</li>
 *     <li>Only edges whose target is in {@code nodes} count. Targets outside {@code nodes} are ignored.</li>
 *     <li>Each occurrence returned by {@code successors} counts once toward the target's indegree, so a target
 *     listed twice needs two decrements before it becomes ready. Callers that want duplicate edges ignored
 *     should pass sets.</li>
 *     <li>{@link #kahnFifo} uses a FIFO ready queue. Initially ready nodes are enqueued in {@code nodes} order.
 *     Nodes that become ready are appended in the order they become ready, while iterating the successors of the
 *     node just emitted in their iteration order.</li>
 *     <li>{@link #kahnOrdered} requires a non-null {@code readyOrder}; the ready queue is a {@link PriorityQueue}
 *     ordered by that comparator. The heap is not stable, so comparators that report ties leave the tie order
 *     unspecified.</li>
 *     <li>{@link Result#order()} lists the emitted nodes. {@link Result#remaining()} lists nodes never emitted,
 *     meaning nodes on or downstream of a cycle, in {@code nodes} order. Both lists are unmodifiable.</li>
 * </ul>
 */
public final class TopoSort {
    private TopoSort() {
    }

    public record Result<T>(List<T> order, List<T> remaining) {
        public boolean acyclic() {
            return remaining.isEmpty();
        }
    }

    /** Polled before each emitted node; a throwing implementation aborts the sort with that exception. */
    @FunctionalInterface
    public interface Checkpoint<E extends Exception> {
        void check() throws E;
    }

    /** FIFO ready order; see the class documentation. */
    public static <T> Result<T> kahnFifo(Collection<T> nodes,
                                         Function<? super T, ? extends Iterable<? extends T>> successors) {
        return run(nodes, successors, null, () -> {});
    }

    /** Same semantics as {@link #kahnFifo(Collection, Function)}, with {@code checkpoint} polled before each emitted node. */
    public static <T, E extends Exception> Result<T> kahnFifo(Collection<T> nodes,
                                                              Function<? super T, ? extends Iterable<? extends T>> successors,
                                                              Checkpoint<E> checkpoint) throws E {
        return run(nodes, successors, null, checkpoint);
    }

    /** Ready nodes are emitted in {@code readyOrder}; see the class documentation for tie behaviour. */
    public static <T> Result<T> kahnOrdered(Collection<T> nodes,
                                            Function<? super T, ? extends Iterable<? extends T>> successors,
                                            Comparator<? super T> readyOrder) {
        return run(nodes, successors, Objects.requireNonNull(readyOrder, "readyOrder"), () -> {});
    }

    private static <T, E extends Exception> Result<T> run(Collection<T> nodes,
                                                          Function<? super T, ? extends Iterable<? extends T>> successors,
                                                          @Nullable Comparator<? super T> readyOrder,
                                                          Checkpoint<E> checkpoint) throws E {
        Set<T> unique = new LinkedHashSet<>(nodes.size());
        for (T node : nodes) {
            unique.add(Objects.requireNonNull(node, "node"));
        }

        Map<T, Integer> indegree = new HashMap<>(unique.size() * 2);
        for (T node : unique) {
            indegree.put(node, 0);
        }

        Map<T, List<T>> outgoing = new HashMap<>(unique.size() * 2);
        for (T node : unique) {
            List<T> targets = new ArrayList<>();
            for (T next : successors.apply(node)) {
                Integer count = indegree.get(next);
                if (count != null) {
                    targets.add(next);
                    indegree.put(next, count + 1);
                }
            }
            outgoing.put(node, targets);
        }

        Queue<T> ready = readyOrder == null ? new ArrayDeque<>() : new PriorityQueue<>(readyOrder);
        for (T node : unique) {
            if (indegree.get(node) == 0) {
                ready.add(node);
            }
        }

        List<T> order = new ArrayList<>(unique.size());
        while (!ready.isEmpty()) {
            checkpoint.check();
            T node = ready.poll();
            order.add(node);
            for (T next : outgoing.get(node)) {
                int count = indegree.get(next) - 1;
                indegree.put(next, count);
                if (count == 0) {
                    ready.add(next);
                }
            }
        }

        Set<T> emitted = new HashSet<>(order);
        List<T> remaining = new ArrayList<>();
        for (T node : unique) {
            if (!emitted.contains(node)) {
                remaining.add(node);
            }
        }
        return new Result<>(List.copyOf(order), List.copyOf(remaining));
    }
}
