package cn.dancingsnow.neoecoae.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledPattern;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Presence-only overapproximation: a negative answer proves impossibility, a positive one proves nothing. */
final class RouteAvailabilityProof {
    private RouteAvailabilityProof() {}

    static boolean goalUnreachable(CompiledNetwork network, PlannerInventorySnapshot stock,
            ECOCancellation cancellation) throws InterruptedException {
        Set<AEKey> available = new HashSet<>(network.emittable());
        for (var entry : stock.toKeyCounter()) {
            cancellation.checkpoint();
            if (entry.getLongValue() > 0L) available.add(entry.getKey());
        }
        if (available.contains(network.goal())) return false;

        List<CompiledPattern> patterns = network.producers().values().stream()
                .flatMap(List::stream).distinct().toList();
        int[] missing = new int[patterns.size()];
        Map<AEKey, List<Integer>> waiting = new HashMap<>();
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int index = 0; index < patterns.size(); index++) {
            cancellation.checkpoint();
            CompiledPattern pattern = patterns.get(index);
            // Unknown, fuzzy, substitution and special-input contracts require their real resolver.
            // Decline this proof rather than excluding an alternative that could actually work.
            if (!pattern.fastSupported() || !pattern.semantics().completeForStaticPlanning()
                    || pattern.specialAnalysis().special()) return false;
            Set<AEKey> needed = new HashSet<>();
            for (var input : pattern.inputs()) {
                if (!input.fastSupported() || input.ignoresComponents()) return false;
                if (!available.contains(input.key())) needed.add(input.key());
            }
            missing[index] = needed.size();
            for (AEKey key : needed) waiting.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
            if (needed.isEmpty()) ready.add(index);
        }
        while (!ready.isEmpty()) {
            cancellation.checkpoint();
            CompiledPattern pattern = patterns.get(ready.removeFirst());
            for (var output : pattern.grossOutputs()) {
                if (output.amount() <= 0L || !available.add(output.what())) continue;
                if (output.what().equals(network.goal())) return false;
                for (int dependent : waiting.getOrDefault(output.what(), List.of())) {
                    if (--missing[dependent] == 0) ready.addLast(dependent);
                }
            }
        }
        return true;
    }
}
