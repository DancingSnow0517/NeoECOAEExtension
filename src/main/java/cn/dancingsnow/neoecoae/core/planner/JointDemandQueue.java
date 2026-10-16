package cn.dancingsnow.neoecoae.core.planner;

import cn.dancingsnow.neoecoae.core.concurrent.Cancellation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Orders existing demands so another requested branch can supply its coproduct before that coproduct is crafted. */
public final class JointDemandQueue<K> {
    private final Map<K, Integer> rank = new LinkedHashMap<>();
    private final Map<K, Set<K>> dependents;
    private final Map<K, Set<K>> jointSources;
    private final Set<K> queued = new LinkedHashSet<>();
    private final PriorityQueue<K> ready = new PriorityQueue<>(Comparator.comparingInt(rank::get));

    /** Maps contain the already-filtered structural dependencies; no recipe is inspected by this scheduler. */
    public JointDemandQueue(List<K> order, Map<K, Set<K>> dependents, Map<K, Set<K>> jointSources) {
        for (K key : order) rank.putIfAbsent(key, rank.size());
        this.dependents = dependents;
        this.jointSources = jointSources;
    }

    public void add(K key) {
        rank.putIfAbsent(key, rank.size());
        if (queued.add(key)) ready.add(key);
    }

    public boolean isEmpty() { return ready.isEmpty(); }

    public K poll(Cancellation cancellation) throws InterruptedException {
        List<K> waiting = new ArrayList<>();
        K chosen = null;
        while (!ready.isEmpty()) {
            cancellation.checkpoint();
            K key = ready.remove();
            if (!mayReceiveJointOutput(key, cancellation)) {
                chosen = key;
                break;
            }
            waiting.add(key);
        }
        // Mutual waiting is only a lookahead heuristic. Preserve original demand order as the fallback.
        if (chosen == null) chosen = waiting.removeFirst();
        ready.addAll(waiting);
        queued.remove(chosen);
        return chosen;
    }

    private boolean mayReceiveJointOutput(K key, Cancellation cancellation) throws InterruptedException {
        Set<K> sources = jointSources.get(key);
        if (sources == null) return false;
        ArrayDeque<K> discover = new ArrayDeque<>();
        Set<K> seen = new HashSet<>();
        seen.add(key);
        // A demand cannot activate its own source by walking a cycle back through itself.
        for (K source : sources) if (seen.add(source)) discover.add(source);
        while (!discover.isEmpty()) {
            cancellation.checkpoint();
            K source = discover.removeFirst();
            if (queued.contains(source)) return true;
            for (K consumer : dependents.getOrDefault(source, Set.of()))
                if (seen.add(consumer)) discover.addLast(consumer);
        }
        return false;
    }
}
