package cn.dancingsnow.neoecoae.crafting.planner.cycle;

import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.BoundedCycleSolver.BatchFiring;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Orders one fixed firing vector against captured stock. Constant resource requirements are checked once;
 * only resources whose requirements depend on order connect transitions into a search component.
 *
 * <p>The startup-frontier factorization follows the idea in NeoECO Integer Planner test.16. It operates on
 * our normalized physical transitions and exact amounts; route selection and final replay stay with the caller.
 * Failure proves nothing about other firing vectors. A resource limit always returns UNKNOWN.
 */
final class CycleStartupSolver {
    private static final int MAX_STATES = 4096;
    private static final int MAX_ENTRIES = 20_000;

    enum Status { FEASIBLE, INFEASIBLE_FIXED_VECTOR, UNKNOWN }

    record Result(Status status, List<BatchFiring> witness, int components, int orderingKeys, int states) {
        Result { witness = List.copyOf(witness); }
    }

    private record Trace(int transition, Trace tail) {}
    private record Node(PlannerAmount[] required, Trace trace) {}
    private record Ring(int[] transitions, int[] sourceKeys) {}
    private static final class Group {
        final List<Integer> transitions = new ArrayList<>();
        final List<Integer> keys = new ArrayList<>();
    }
    private static final class Limit extends RuntimeException {
        Limit() { super(null, null, false, false); }
    }

    private final long[][] consumed;
    private final long[][] produced;
    private final boolean[] suppliable;
    private final PlannerAmount[] counts;
    private final PlannerAmount[] stock;
    private final ECOCancellation cancellation;
    private final CycleMemoryBudget memory;
    private int components, orderingKeys, states, entries;

    private CycleStartupSolver(long[][] consumed, long[][] produced, boolean[] suppliable,
            PlannerAmount[] counts, PlannerAmount[] stock, ECOCancellation cancellation, CycleMemoryBudget memory) {
        this.consumed = consumed;
        this.produced = produced;
        this.suppliable = suppliable;
        this.counts = counts;
        this.stock = stock;
        this.cancellation = cancellation;
        this.memory = memory;
    }

    static Result solve(long[][] consumed, long[][] produced, boolean[] suppliable, PlannerAmount[] counts,
            PlannerAmount[] stock, ECOCancellation cancellation, CycleMemoryBudget memory) throws InterruptedException {
        var solver = new CycleStartupSolver(consumed, produced, suppliable, counts, stock, cancellation, memory);
        long before = memory.retainedBytes();
        Result result;
        try {
            result = solver.solve();
        } catch (Limit | CycleMemoryBudget.Exhausted exhausted) {
            result = solver.result(Status.UNKNOWN, List.of());
        } finally {
            // Tables and traces are request-local. An interrupted optional search must leave the shared
            // budget usable by the original cycle search, including when cancellation unwinds this scope.
            memory.release(memory.retainedBytes() - before);
        }
        if (result.status() == Status.FEASIBLE) {
            long bytes = 32L + 8L * result.witness().size();
            for (BatchFiring run : result.witness()) {
                bytes += 64L + CycleMemoryBudget.integerBytes(run.exactCount().toBigInteger())
                    + CycleMemoryBudget.integerBytes(run.repetitions().toBigInteger());
            }
            try {
                memory.retain(bytes);
            } catch (CycleMemoryBudget.Exhausted exhausted) {
                return solver.result(Status.UNKNOWN, List.of());
            }
        }
        return result;
    }

    private Result solve() throws InterruptedException {
        cancellation.checkpoint();
        memory.retain(512L + 128L * counts.length + 96L * stock.length);
        int[] parent = new int[counts.length], participants = new int[counts.length];
        int[] keyOwner = new int[stock.length];
        Arrays.fill(keyOwner, -1);
        for (int t = 0; t < counts.length; t++) {
            if (counts[t].signum() < 0) throw new IllegalArgumentException("Negative fixed firing count");
            parent[t] = t;
        }
        for (int key = 0; key < stock.length; key++) {
            cancellation.checkpoint();
            if (suppliable[key]) continue; // Unlimited boundary inputs are still charged by physical replay.
            int size = 0;
            boolean neutral = true, sink = true, source = true;
            long maximumInput = 0;
            for (int t = 0; t < counts.length; t++) {
                if ((t & 31) == 0) cancellation.checkpoint();
                if (counts[t].isZero() || consumed[t][key] == 0 && produced[t][key] == 0) continue;
                participants[size++] = t;
                neutral &= consumed[t][key] == produced[t][key];
                sink &= produced[t][key] == 0;
                source &= consumed[t][key] == 0;
                maximumInput = Math.max(maximumInput, consumed[t][key]);
            }
            if (size == 0) continue;
            PlannerAmount fixed = null;
            if (size == 1) {
                int t = participants[0];
                fixed = PlannerAmount.of(consumed[t][key]).add(counts[t].subtract(PlannerAmount.ONE)
                    .multiply(Math.max(0L, consumed[t][key] - produced[t][key])));
            } else if (neutral) {
                fixed = PlannerAmount.of(maximumInput);
            } else if (source) {
                fixed = PlannerAmount.ZERO;
            } else if (sink) {
                fixed = PlannerAmount.ZERO;
                for (int i = 0; i < size; i++) {
                    cancellation.checkpoint();
                    int t = participants[i];
                    fixed = fixed.add(counts[t].multiply(consumed[t][key]));
                }
            }
            if (fixed != null) {
                if (stock[key].compareTo(fixed) < 0) return result(Status.INFEASIBLE_FIXED_VECTOR, List.of());
            } else {
                orderingKeys++;
                keyOwner[key] = participants[0];
                for (int i = 1; i < size; i++) {
                    cancellation.checkpoint();
                    parent[root(parent, participants[i])] = root(parent, participants[0]);
                }
            }
        }

        var groups = new LinkedHashMap<Integer, Group>();
        for (int t = 0; t < counts.length; t++) {
            cancellation.checkpoint();
            if (!counts[t].isZero()) groups.computeIfAbsent(root(parent, t), ignored -> new Group()).transitions.add(t);
        }
        for (int key = 0; key < keyOwner.length; key++) {
            cancellation.checkpoint();
            if (keyOwner[key] >= 0) groups.get(root(parent, keyOwner[key])).keys.add(key);
        }
        components = groups.size();
        List<BatchFiring> witness = new ArrayList<>();
        for (Group group : groups.values()) {
            cancellation.checkpoint();
            List<BatchFiring> part = solve(group);
            if (part == null) return result(Status.INFEASIBLE_FIXED_VECTOR, List.of());
            witness.addAll(part);
        }
        return result(Status.FEASIBLE, witness);
    }

    private List<BatchFiring> solve(Group group) throws InterruptedException {
        if (states >= MAX_STATES) throw new Limit();
        int size = group.transitions.size();
        if (size == 1) {
            states++;
            int t = group.transitions.getFirst();
            return List.of(new BatchFiring(t, counts[t]));
        }
        Ring ring = unitRing(group);
        if (ring != null) {
            states++;
            for (int start = 0; start < size; start++) {
                cancellation.checkpoint();
                if (stock[ring.sourceKeys()[start]].signum() <= 0) continue;
                List<BatchFiring> period = new ArrayList<>();
                PlannerAmount repetitions = counts[ring.transitions()[0]];
                for (int step = 0; step < size; step++) {
                    cancellation.checkpoint();
                    int t = ring.transitions()[(start + step) % size];
                    period.add(step == size - 1 && repetitions.compareTo(PlannerAmount.ONE) > 0
                        ? new BatchFiring(t, PlannerAmount.ONE, size, repetitions)
                        : new BatchFiring(t, PlannerAmount.ONE));
                }
                return period;
            }
            return null;
        }

        int[] radix = new int[size], stride = new int[size];
        int lattice = 1;
        for (int i = 0; i < size; i++) {
            cancellation.checkpoint();
            PlannerAmount side = counts[group.transitions.get(i)].add(PlannerAmount.ONE);
            if (side.compareTo(PlannerAmount.of((MAX_STATES - states) / lattice)) > 0) throw new Limit();
            stride[i] = lattice;
            radix[i] = (int) side.longValueExact();
            lattice *= radix[i];
        }
        memory.retain(64L + 16L * size + 8L * lattice);
        @SuppressWarnings("unchecked") List<Node>[] memo = (List<Node>[]) new List<?>[lattice];
        PlannerAmount[] zero = new PlannerAmount[group.keys.size()];
        Arrays.fill(zero, PlannerAmount.ZERO);
        memo[0] = List.of(node(zero, null));
        states++;
        for (int state = 1; state < lattice; state++) {
            cancellation.checkpoint();
            List<Node> frontier = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                if (state / stride[i] % radix[i] == 0) continue;
                int t = group.transitions.get(i);
                for (Node tail : memo[state - stride[i]]) {
                    cancellation.checkpoint();
                    PlannerAmount[] needed = new PlannerAmount[group.keys.size()];
                    for (int k = 0; k < needed.length; k++) {
                        if ((k & 31) == 0) cancellation.checkpoint();
                        int key = group.keys.get(k);
                        needed[k] = tail.required()[k].subtract(produced[t][key] - consumed[t][key])
                            .max(PlannerAmount.of(consumed[t][key]));
                    }
                    memory.check(CycleMemoryBudget.markingBytes(needed));
                    boolean dominated = false;
                    for (var iterator = frontier.iterator(); iterator.hasNext();) {
                        cancellation.checkpoint();
                        Node existing = iterator.next();
                        if (dominates(existing.required(), needed)) { dominated = true; break; }
                        if (dominates(needed, existing.required())) iterator.remove();
                    }
                    if (!dominated) frontier.add(node(needed, new Trace(t, tail.trace())));
                }
            }
            memo[state] = frontier;
            states++;
        }
        for (Node candidate : memo[lattice - 1]) {
            cancellation.checkpoint();
            boolean covered = true;
            for (int k = 0; k < group.keys.size(); k++) {
                if ((k & 31) == 0) cancellation.checkpoint();
                if (stock[group.keys.get(k)].compareTo(candidate.required()[k]) < 0) { covered = false; break; }
            }
            if (!covered) continue;
            List<BatchFiring> witness = new ArrayList<>();
            for (Trace step = candidate.trace(); step != null; step = step.tail()) {
                cancellation.checkpoint();
                if (!witness.isEmpty() && witness.getLast().transition() == step.transition()) {
                    var previous = witness.removeLast();
                    witness.add(new BatchFiring(step.transition(), previous.exactCount().add(PlannerAmount.ONE)));
                } else witness.add(new BatchFiring(step.transition(), PlannerAmount.ONE));
            }
            return witness;
        }
        return null;
    }

    /** A single unit token can traverse an equal-count ring an arbitrary exact number of times. */
    private Ring unitRing(Group group) throws InterruptedException {
        int size = group.transitions.size();
        if (group.keys.size() != size) return null;
        memory.retain(128L + 32L * size);
        int[] outgoing = new int[size], destination = new int[size];
        Arrays.fill(outgoing, -1);
        for (int i = 0; i < size; i++) {
            cancellation.checkpoint();
            int t = group.transitions.get(i), source = -1, target = -1;
            if (!counts[t].equals(counts[group.transitions.getFirst()])) return null;
            for (int k = 0; k < size; k++) {
                if ((k & 31) == 0) cancellation.checkpoint();
                int key = group.keys.get(k);
                if (consumed[t][key] > 0) {
                    if (consumed[t][key] != 1 || source >= 0) return null;
                    source = k;
                }
                if (produced[t][key] > 0) {
                    if (produced[t][key] != 1 || target >= 0) return null;
                    target = k;
                }
            }
            if (source < 0 || target < 0 || source == target || outgoing[source] >= 0) return null;
            outgoing[source] = i;
            destination[i] = target;
        }
        int[] order = new int[size], sourceKeys = new int[size];
        boolean[] visited = new boolean[size];
        int cursor = 0;
        for (int step = 0; step < size; step++) {
            cancellation.checkpoint();
            int i = outgoing[cursor];
            if (i < 0 || visited[i]) return null;
            visited[i] = true;
            order[step] = group.transitions.get(i);
            sourceKeys[step] = group.keys.get(cursor);
            cursor = destination[i];
        }
        return cursor == 0 ? new Ring(order, sourceKeys) : null;
    }

    private Node node(PlannerAmount[] required, Trace trace) {
        if (++entries > MAX_ENTRIES) throw new Limit();
        memory.retain(64L + CycleMemoryBudget.markingBytes(required));
        return new Node(required, trace);
    }

    private boolean dominates(PlannerAmount[] left, PlannerAmount[] right) throws InterruptedException {
        for (int k = 0; k < left.length; k++) {
            if ((k & 31) == 0) cancellation.checkpoint();
            if (left[k].compareTo(right[k]) > 0) return false;
        }
        return true;
    }

    private Result result(Status status, List<BatchFiring> witness) {
        return new Result(status, witness, components, orderingKeys, states);
    }

    private static int root(int[] parent, int t) {
        while (parent[t] != t) { parent[t] = parent[parent[t]]; t = parent[t]; }
        return t;
    }
}
