package cn.dancingsnow.neoecoae.crafting.planner.solve;

import cn.dancingsnow.neoecoae.core.planner.acyclic.CandidateFallback;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork;
import java.util.Map;

/** Advances the nearest producer that can explain a failed leaf, without global route enumeration. */
public final class CandidateResolver {
    public boolean advanceAfterFailure(CompiledNetwork network, SolveState failed, Map<AEKey, Integer> choices) {
        return CandidateFallback.advance(
            failed.missing.asMap().keySet(), failed.unsupported, failed.parents,
            key -> network.fastProducersOf(key).size(), choices);
    }
}
