package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import cn.dancingsnow.neoecoae.core.planner.JointDemandQueue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Demand ordering only: this never creates a producer or starts a recipe for its secondary output. */
final class JointOutputDemandQueue {
    private final JointDemandQueue<AEKey> queue;

    JointOutputDemandQueue(List<AEKey> order, Map<AEKey, CompiledPattern> selected,
            Set<IPatternDetails> deferredPatterns, Set<AEKey> stockLeaves) {
        Map<AEKey, Set<AEKey>> dependents = new HashMap<>();
        Map<AEKey, Set<AEKey>> jointSources = new HashMap<>();
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
        queue = new JointDemandQueue<>(order, dependents, jointSources);
    }

    void add(AEKey key) { queue.add(key); }

    boolean isEmpty() { return queue.isEmpty(); }

    AEKey poll(ECOCancellation cancellation) throws InterruptedException { return queue.poll(cancellation); }
}
