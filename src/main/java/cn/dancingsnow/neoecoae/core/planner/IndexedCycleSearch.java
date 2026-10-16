package cn.dancingsnow.neoecoae.core.planner;

import cn.dancingsnow.neoecoae.core.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Best-first numeric marking search. Platform adapters compile indexed data and project firing receipts. */
public final class IndexedCycleSearch {
    private IndexedCycleSearch() {}

    public record Data(long[][] cons, boolean[] suppliable, boolean[] consumedKeys,
            boolean[] producesRequired, PlannerAmount[] required) {
        public int keyCount() { return required.length; }
        public int transitionCount() { return cons.length; }
    }

    public interface Callbacks<F> {
        long[] batches(PlannerAmount[] marking, int transition);
        PlannerAmount[] fire(PlannerAmount[] marking, int transition, long batch);
        PlannerAmount[] accelerate(PlannerAmount[] marking, List<F> witness) throws InterruptedException;
        F firing(int transition, long batch);
    }

    public interface Memory {
        void retain(long bytes);
        void check(long bytes);
        long markingBytes(PlannerAmount[] marking);
    }

    public static <F> void search(Data model, PlannerAmount[] start, int stateBudget, int maxFirings,
            Cancellation cancellation, Memory memory, Callbacks<F> callbacks, Result<F> outcome)
            throws InterruptedException {
        int n = model.keyCount();
        int transitionCount = model.transitionCount();
        List<Node<F>> nodes = new ArrayList<>();
        Set<Marking> seen = new ObjectOpenHashSet<>();
        var queue = new IntHeapPriorityQueue((left, right) -> {
            int progress = compareProgress(nodes.get(left), nodes.get(right));
            return progress != 0 ? progress : Integer.compare(left, right);
        });

        PlannerAmount[] root = Arrays.copyOf(start, n);
        memory.retain(memory.markingBytes(root));
        nodes.add(new Node<>(root, -1, null, 0, deficitScore(model, root)));
        seen.add(new Marking(model, root));
        if (satisfied(root, model.required)) {
            outcome.kind = Result.Kind.REACHED;
            outcome.witness = List.of();
            outcome.statesVisited = 1;
            return;
        }

        queue.enqueue(0);

        while (!queue.isEmpty()) {
            cancellation.checkpoint();
            int index = queue.dequeueInt();
            Node<F> node = nodes.get(index);
            if (maxFirings > 0 && node.depth >= maxFirings) {
                outcome.firingDepthTruncated = true;
                continue;
            }
            outcome.statesExpanded++;
            // Accelerate the selected path without discarding other reachable markings.
            memory.check(64L * (node.depth + 1L));
            List<F> path = witnessOf(nodes, index);
            PlannerAmount[] accelerated = callbacks.accelerate(node.marking, path);
            if (!Arrays.equals(accelerated, node.marking)) {
                if (satisfied(accelerated, model.required)) {
                    outcome.kind = Result.Kind.REACHED;
                    outcome.witness = path;
                    outcome.statesVisited = seen.size();
                    return;
                }
                Marking acceleratedKey = new Marking(model, accelerated);
                if (!seen.contains(acceleratedKey)) {
                    if (stateBudget > 0 && seen.size() >= stateBudget) {
                        outcome.stateBudgetExhausted = true;
                        break;
                    }
                    memory.retain(memory.markingBytes(accelerated) + 64L * path.size());
                    Node<F> jump = new Node<>(accelerated, -1, null, node.depth + 1, deficitScore(model, accelerated));
                    jump.compactWitness = path;
                    seen.add(acceleratedKey);
                    nodes.add(jump);
                    queue.enqueue(nodes.size() - 1);
                }
            }
            for (int t = 0; t < transitionCount; t++) {
                cancellation.checkpoint();
                long[] batches = callbacks.batches(node.marking, t);
                if (batches.length == 0) {
                    outcome.considerUnblock(model, node.marking, t);
                    continue;
                }
                for (long batch : batches) {
                    cancellation.checkpoint();
                    PlannerAmount[] next = callbacks.fire(node.marking, t, batch);
                    Marking key = new Marking(model, next);
                    if (seen.contains(key)) continue;
                    if (stateBudget > 0 && seen.size() >= stateBudget) {
                        outcome.stateBudgetExhausted = true;
                        break;
                    }
                    memory.retain(memory.markingBytes(next));
                    seen.add(key);
                    int child = nodes.size();
                    nodes.add(new Node<>(next, index, callbacks.firing(t, batch), node.depth + 1,
                        deficitScore(model, next)));
                    if (satisfied(next, model.required)) {
                        outcome.kind = Result.Kind.REACHED;
                        outcome.witness = witnessOf(nodes, child);
                        outcome.statesVisited = seen.size();
                        return;
                    }
                    queue.enqueue(child);
                }
                if (outcome.stateBudgetExhausted) break;
            }
            if (outcome.stateBudgetExhausted) break;
        }

        outcome.statesVisited = seen.size();
        if (outcome.stateBudgetExhausted) outcome.kind = Result.Kind.STATE_BUDGET;
        else if (outcome.firingDepthTruncated) outcome.kind = Result.Kind.DEPTH_TRUNCATED;
        else outcome.kind = Result.Kind.EXHAUSTED;
        return;
    }

    private static <F> int compareProgress(Node<F> left, Node<F> right) {
        int deficit = left.deficit.compareTo(right.deficit);
        if (deficit != 0) return deficit;
        return Integer.compare(left.depth, right.depth);
    }

    private static PlannerAmount deficitScore(Data model, PlannerAmount[] marking) {
        PlannerAmount result = PlannerAmount.ZERO;
        for (int i = 0; i < model.keyCount(); i++) {
            if (model.required[i].compareTo(marking[i]) > 0) {
                result = result.add(normalizedDeficit(model.required[i].subtract(marking[i]), model.required[i]));
            }
        }
        return result;
    }

    /** Dimensionless fixed-point ratios; rounding only affects queue order, never proofs. */
    public static PlannerAmount normalizedDeficit(PlannerAmount missing, PlannerAmount required) {
        return missing.multiply(1_000_000L).ceilDiv(required.max(PlannerAmount.ONE));
    }

    private static <F> List<F> witnessOf(List<Node<F>> nodes, int leaf) {
        ArrayDeque<F> reversed = new ArrayDeque<>();
        int cursor = leaf;
        while (cursor > 0) {
            Node<F> node = nodes.get(cursor);
            if (node.compactWitness != null) {
                List<F> result = new ArrayList<>(node.compactWitness);
                result.addAll(reversed);
                return result;
            }
            reversed.addFirst(node.firing);
            cursor = node.parent;
        }
        return new ArrayList<>(reversed);
    }

    private static boolean satisfied(PlannerAmount[] marking, PlannerAmount[] required) {
        for (int i = 0; i < required.length; i++) {
            if (required[i].signum() > 0 && marking[i].compareTo(required[i]) < 0) return false;
        }
        return true;
    }

    private static final class Node<F> {
        private final PlannerAmount[] marking;
        private final int parent;
        private final F firing;
        private final int depth;
        private final PlannerAmount deficit;
        private List<F> compactWitness;

        private Node(PlannerAmount[] marking, int parent, F firing, int depth, PlannerAmount deficit) {
            this.marking = marking;
            this.parent = parent;
            this.firing = firing;
            this.depth = depth;
            this.deficit = deficit;
        }
    }

    private static final class Marking {
        private final PlannerAmount[] cells;
        private final int hash;

        private Marking(Data model, PlannerAmount[] cells) {
            this.cells = cells.clone();
            for (int i = 0; i < cells.length; i++) {
                if (!model.consumedKeys[i]) this.cells[i] = cells[i].min(model.required[i]);
            }
            this.hash = Arrays.hashCode(this.cells);
        }

        @Override public boolean equals(Object other) {
            return other instanceof Marking marking && Arrays.equals(cells, marking.cells);
        }

        @Override public int hashCode() { return hash; }
    }


    public static class Result<F> {
        public enum Kind { REACHED, EXHAUSTED, STATE_BUDGET, DEPTH_TRUNCATED }

        public Kind kind = Kind.EXHAUSTED;
        public List<F> witness = List.of();
        public long statesVisited;
        public long statesExpanded;
        public long greedyCandidates;
        public long lookaheadNodes;
        public long heuristicMacroSteps;
        public boolean heuristicBudgetExhausted;
        public boolean stateBudgetExhausted;
        public boolean firingDepthTruncated;
        public PlannerAmount[] unblockDeficit;
        private int unblockRank = Integer.MAX_VALUE;
        private PlannerAmount unblockTotal = null;
        private int unblockTransition = Integer.MAX_VALUE;

        /**
         * Records the cheapest way to unblock a disabled transition, preferring one that actually produces a
         * required output. That candidate is a lower-bound proposal when no full-order static certificate is available.
         */
        private void considerUnblock(Data model, PlannerAmount[] marking, int transition) {
            int rank = model.producesRequired[transition] ? 0 : 1;

            long[] cons = model.cons[transition];
            PlannerAmount total = PlannerAmount.ZERO;
            for (int i = 0; i < cons.length; i++) {
                PlannerAmount required = PlannerAmount.of(cons[i]);
                if (cons[i] > 0 && !model.suppliable[i] && marking[i].compareTo(required) < 0) {
                    total = total.add(normalizedDeficit(required.subtract(marking[i]), required));
                }
            }
            if (total.signum() <= 0) return;
            PlannerAmount[] deficit = new PlannerAmount[cons.length];
            for (int i = 0; i < cons.length; i++) {
                PlannerAmount required = PlannerAmount.of(cons[i]);
                deficit[i] = cons[i] > 0 && !model.suppliable[i] && marking[i].compareTo(required) < 0
                    ? required.subtract(marking[i]) : PlannerAmount.ZERO;
            }
            if (rank > unblockRank) return;
            if (rank == unblockRank) {
                if (total.compareTo(unblockTotal) > 0) return;
                if (total.equals(unblockTotal) && transition >= unblockTransition) return;
            }
            unblockRank = rank;
            unblockTotal = total;
            unblockTransition = transition;
            unblockDeficit = deficit;
        }
    }
}
