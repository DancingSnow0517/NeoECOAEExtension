package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Enumerates alternative producer routes for the fallback search, one route per distinct reachable choice.
 * A producer choice can only affect planning when the route reaches its key: as an input of a chosen pattern,
 * as an output/returned/feedback key of a chosen pattern, or as a key whose own candidate outputs a reached
 * key (choosing it adds a structural edge back into the route). Choices are fixed in breadth-first order from
 * the goal, so a fixed key stays reachable whatever is decided later, and keys the route never reaches keep
 * their baseline producer. Routes differing only outside their reach are planned once instead of once per
 * combination of irrelevant choices. Routes come in ascending number of choices that differ from the baseline
 * (iterative deepening); the baseline itself is never returned.
 */
final class RouteSearch {
    private static final class Frame {
        final AEKey key;
        final int base;
        final int size;
        int step;

        Frame(AEKey key, int base, int size) {
            this.key = key;
            this.base = base;
            this.size = size;
        }

        /** Tries every deviation first and the baseline last, so changes near the goal precede deep ones. */
        int offset() {
            return (step + 1) % size;
        }
    }

    private final CompiledNetwork network;
    /** Keys whose candidate patterns also output (or return) the map key; choosing them touches that key. */
    private final Map<AEKey, Set<AEKey>> sideProducers = new LinkedHashMap<>();
    private final Map<AEKey, Integer> choices;
    private final Set<AEKey> fixed = new HashSet<>();
    private final ArrayDeque<Frame> stack = new ArrayDeque<>();
    private final ArrayDeque<AEKey> queue = new ArrayDeque<>();
    private final Set<AEKey> seen = new HashSet<>();
    private int limit;
    private int deviations;
    private boolean deeper = true;
    private boolean passOpen;
    private long expansions;

    RouteSearch(CompiledNetwork network, Map<AEKey, Integer> baseline) {
        this.network = network;
        this.choices = new LinkedHashMap<>(baseline);
        for (var entry : network.fastProducers().entrySet()) {
            AEKey key = entry.getKey();
            for (CompiledPattern pattern : entry.getValue()) {
                for (var output : pattern.grossOutputs()) {
                    if (output == null || output.what() == null || output.what().equals(key)) continue;
                    sideProducers.computeIfAbsent(output.what(), ignored -> new LinkedHashSet<>()).add(key);
                }
                for (var feedback : pattern.semantics().feedbackEdges()) {
                    if (feedback.returnedKey().equals(key)) continue;
                    sideProducers.computeIfAbsent(feedback.returnedKey(), ignored -> new LinkedHashSet<>()).add(key);
                }
            }
        }
    }

    /** Current iterative-deepening limit: the deviation count of the last returned route. */
    int pass() {
        return limit;
    }

    /** Number of reachability walks performed. */
    long expansions() {
        return expansions;
    }

    /** Next route as a full choice map, or null once every route has been returned. */
    @Nullable Map<AEKey, Integer> next(ECOCancellation cancellation) throws InterruptedException {
        while (true) {
            if (!passOpen) {
                if (!deeper) return null;
                limit++;
                deeper = false;
                passOpen = true;
                if (descend(cancellation)) return new LinkedHashMap<>(choices);
                continue;
            }
            if (stack.isEmpty()) {
                passOpen = false;
                continue;
            }
            Frame top = stack.peek();
            if (top.offset() != 0) deviations--;
            top.step++;
            if (top.step >= top.size) {
                choices.put(top.key, top.base);
                fixed.remove(top.key);
                stack.pop();
                continue;
            }
            apply(top);
            if (descend(cancellation)) return new LinkedHashMap<>(choices);
        }
    }

    /** Returns true when the current state is a route with exactly {@code limit} deviations. */
    private boolean descend(ECOCancellation cancellation) throws InterruptedException {
        while (true) {
            cancellation.checkpoint();
            expansions++;
            AEKey frontier = walk();
            // A node at the limit is itself a route (undecided keys keep their baseline); a deeper pass
            // has work only if it still has an undecided frontier.
            if (deviations == limit) {
                if (frontier != null) deeper = true;
                return true;
            }
            // Fully decided with fewer deviations: returned in an earlier pass, or the baseline itself.
            if (frontier == null) return false;
            int size = network.fastProducersOf(frontier).size();
            Frame frame = new Frame(frontier, choice(frontier), size);
            stack.push(frame);
            fixed.add(frontier);
            apply(frame);
        }
    }

    private void apply(Frame frame) {
        int offset = frame.offset();
        choices.put(frame.key, (frame.base + offset) % frame.size);
        if (offset != 0) deviations++;
    }

    private int choice(AEKey key) {
        int size = network.fastProducersOf(key).size();
        return Math.max(0, Math.min(choices.getOrDefault(key, 0), size - 1));
    }

    /**
     * Breadth-first from the goal; returns the first variable key without a decision. Every key processed
     * before it is fixed or single-candidate, so its discovery never depends on later decisions.
     */
    private @Nullable AEKey walk() {
        queue.clear();
        seen.clear();
        enqueue(network.goal());
        while (!queue.isEmpty()) {
            AEKey key = queue.pollFirst();
            var candidates = network.fastProducersOf(key);
            if (candidates.size() > 1 && !fixed.contains(key)) return key;
            if (!candidates.isEmpty()) visit(candidates.get(choice(key)));
            var side = sideProducers.get(key);
            if (side != null) for (AEKey producer : side) enqueue(producer);
        }
        return null;
    }

    private void visit(CompiledPattern pattern) {
        for (var input : pattern.inputs()) enqueue(input.key());
        for (var requirement : pattern.specialAnalysis().requirements()) {
            var source = requirement.input().source();
            if (source == null) continue;
            try {
                for (var possible : source.getPossibleInputs()) {
                    if (possible != null) enqueue(possible.what());
                }
            } catch (RuntimeException ignored) {
                // Unreadable alternatives were never part of the plan; the resolver skips them as well.
            }
        }
        for (var output : pattern.grossOutputs()) {
            if (output != null) enqueue(output.what());
        }
        for (var feedback : pattern.semantics().feedbackEdges()) {
            enqueue(feedback.returnedKey());
            enqueue(feedback.dependentOutput());
        }
    }

    private void enqueue(@Nullable AEKey key) {
        if (key != null && seen.add(key)) queue.addLast(key);
    }
}
