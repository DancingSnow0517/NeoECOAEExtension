package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Demand ordering only: this never creates a producer or starts a recipe for its secondary output. */
final class JointOutputDemandQueue {
    private final Map<AEKey, Integer> rank = new LinkedHashMap<>();
    private final Map<AEKey, Set<AEKey>> dependents = new HashMap<>();
    private final Map<AEKey, Set<AEKey>> jointSources = new HashMap<>();
    private final Set<AEKey> queued = new LinkedHashSet<>();
    private final PriorityQueue<AEKey> ready = new PriorityQueue<>(Comparator.comparingInt(rank::get));
    private final int initialRanks;

    JointOutputDemandQueue(List<AEKey> order, Map<AEKey, CompiledPattern> selected,
            Set<IPatternDetails> deferredPatterns, Set<AEKey> stockLeaves) {
        for (AEKey key : order) rank.putIfAbsent(key, rank.size());
        initialRanks = rank.size();
        selected.forEach((key, pattern) -> {
            if (stockLeaves.contains(key) || deferredPatterns.contains(pattern.details())) return;
            for (var input : pattern.inputs()) {
                if (!pattern.specialAnalysis().excludesFromCycleGraph(input)) {
                    dependents.computeIfAbsent(input.key(), ignored -> new HashSet<>()).add(key);
                }
            }
            for (var output : pattern.outputs()) {
                if (!output.what().equals(key)) {
                    jointSources.computeIfAbsent(output.what(), ignored -> new HashSet<>()).add(key);
                }
            }
        });
    }

    void add(AEKey key) {
        rank.putIfAbsent(key, rank.size());
        if (queued.add(key)) ready.add(key);
    }

    boolean isEmpty() { return ready.isEmpty(); }

    /**
     * Restore only queue operations, using this attempt's ranks and potential joint-output dependencies.
     * Identical polls and compiled views prove a prefix executes in exactly the same queue order;
     * input additions are replayed only for steps that actually expanded a recipe. No quantities or
     * material allocations are recomputed here. The caller resets the queue if the prefix diverges.
     */
    boolean restorePrefix(List<AEKey> polled, BitSet expanded, Map<AEKey, CompiledPattern> previous,
            Map<AEKey, CompiledPattern> selected, ECOCancellation cancellation)
            throws InterruptedException {
        for (int i = 0; i < polled.size(); i++) {
            AEKey key = polled.get(i);
            CompiledPattern pattern = previous.get(key);
            if (pattern != selected.get(key) || !key.equals(poll(cancellation))) return false;
            if (expanded.get(i)) for (var input : pattern.inputs()) {
                if (!pattern.specialAnalysis().excludesFromCycleGraph(input)) add(input.key());
            }
        }
        return true;
    }

    void reset(Set<AEKey> goals) {
        ready.clear();
        queued.clear();
        if (rank.size() != initialRanks) rank.values().removeIf(value -> value >= initialRanks);
        goals.forEach(this::add);
    }

    AEKey poll(ECOCancellation cancellation) throws InterruptedException {
        List<AEKey> waiting = new ArrayList<>();
        AEKey chosen = null;
        while (!ready.isEmpty()) {
            cancellation.checkpoint();
            AEKey key = ready.remove();
            if (!mayReceiveJointOutput(key, cancellation)) {
                chosen = key;
                break;
            }
            waiting.add(key);
        }
        // A conservative lookahead can find mutually waiting branches. Fall back to the original
        // demand order; missing inputs remain pending and actual supply cycles must still be validated.
        if (chosen == null) chosen = waiting.removeFirst();
        ready.addAll(waiting);
        queued.remove(chosen);
        return chosen;
    }

    private boolean mayReceiveJointOutput(AEKey key, ECOCancellation cancellation) throws InterruptedException {
        Set<AEKey> sources = jointSources.get(key);
        if (sources == null) return false;
        ArrayDeque<AEKey> discover = new ArrayDeque<>();
        Set<AEKey> seen = new HashSet<>();
        seen.add(key);
        // Only another already-requested branch may activate the source. Traversing through this
        // demand would let a request for a byproduct start its own source recipe indirectly.
        for (AEKey source : sources) if (seen.add(source)) discover.add(source);
        while (!discover.isEmpty()) {
            cancellation.checkpoint();
            AEKey source = discover.removeFirst();
            if (queued.contains(source)) return true;
            for (AEKey consumer : dependents.getOrDefault(source, Set.of())) {
                if (seen.add(consumer)) discover.addLast(consumer);
            }
        }
        return false;
    }
}
