package cn.dancingsnow.neoecoae.impl.crafting.planner.solve;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Advances the nearest producer that can explain a failed leaf, without global route enumeration. */
public final class CandidateResolver {
    public boolean advanceAfterFailure(CompiledNetwork network, SolveState failed, Map<AEKey, Integer> choices) {
        ArrayDeque<AEKey> queue = new ArrayDeque<>();
        Set<AEKey> seen = new HashSet<>();
        for (var missing : failed.missing) {
            queue.add(missing.getKey());
            seen.add(missing.getKey());
        }
        while (!queue.isEmpty()) {
            AEKey failedKey = queue.removeFirst();
            for (AEKey parent : failed.parents.getOrDefault(failedKey, Set.of())) {
                List<CompiledPattern> candidates = network.producersOf(parent).stream()
                    .filter(CompiledPattern::fastSupported).toList();
                int current = choices.getOrDefault(parent, 0);
                if (current + 1 < candidates.size()) {
                    choices.put(parent, current + 1);
                    return true;
                }
                if (seen.add(parent)) queue.addLast(parent);
            }
        }
        return false;
    }
}
