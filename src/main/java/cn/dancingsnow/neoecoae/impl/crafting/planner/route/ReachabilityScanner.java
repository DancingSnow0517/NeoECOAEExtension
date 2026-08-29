package cn.dancingsnow.neoecoae.impl.crafting.planner.route;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledNetwork;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledPattern;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ReachabilityScanner {
    public Set<AEKey> scan(CompiledNetwork network, ECOCancellation cancellation) throws InterruptedException {
        Set<AEKey> reachable = new LinkedHashSet<>();
        ArrayDeque<AEKey> queue = new ArrayDeque<>();
        queue.add(network.goal());
        reachable.add(network.goal());
        while (!queue.isEmpty()) {
            cancellation.checkpoint();
            AEKey key = queue.removeFirst();
            for (CompiledPattern pattern : network.producersOf(key)) {
                for (CompiledInput input : pattern.inputs()) {
                    if (reachable.add(input.key())) queue.addLast(input.key());
                }
            }
        }
        return reachable;
    }
}
