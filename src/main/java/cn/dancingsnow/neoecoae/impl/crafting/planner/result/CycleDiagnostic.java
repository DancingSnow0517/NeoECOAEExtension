package cn.dancingsnow.neoecoae.impl.crafting.planner.result;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CycleDiagnostic(
    List<AEKey> keys,
    List<IPatternDetails> patterns,
    Map<AEKey, Long> netOutputs,
    Map<AEKey, Long> availableAmounts
) {
    public CycleDiagnostic {
        keys = List.copyOf(keys);
        patterns = List.copyOf(patterns);
        netOutputs = Map.copyOf(netOutputs);
        availableAmounts = Map.copyOf(availableAmounts);
    }

    public CycleDiagnostic withAvailableAmounts(KeyCounter inventory) {
        Map<AEKey, Long> available = new LinkedHashMap<>();
        for (AEKey key : keys) {
            available.putIfAbsent(key, Math.max(0L, inventory.get(key)));
        }
        return new CycleDiagnostic(keys, patterns, netOutputs, available);
    }
}
