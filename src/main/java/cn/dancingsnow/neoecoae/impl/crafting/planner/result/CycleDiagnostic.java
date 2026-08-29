package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.util.List;

public record CycleDiagnostic(List<AEKey> keys, List<IPatternDetails> patterns) {
    public CycleDiagnostic {
        keys = List.copyOf(keys);
        patterns = List.copyOf(patterns);
    }
}
