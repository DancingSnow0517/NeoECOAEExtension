package cn.dancingsnow.neoecoae.impl.crafting.planner.semantic;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.compat.extendedaeplus.ExtendedAEPlusPatternSemanticAdapter;
import cn.dancingsnow.neoecoae.compat.thunderbolt.ThunderPatternSemanticAdapter;
import cn.dancingsnow.neoecoae.compat.useless.UselessPatternSemanticAdapter;
import java.util.ArrayList;
import java.util.List;

/** Ordered adapter registry. Integrations are checked before the generic AE2 contract. */
public final class PatternSemanticAdapters {
    private PatternSemanticAdapters() {
    }

    public static List<PatternSemanticAdapter> defaults() {
        return List.of(new ThunderPatternSemanticAdapter(), new UselessPatternSemanticAdapter(),
            new ExtendedAEPlusPatternSemanticAdapter(), new AE2PatternSemanticAdapter());
    }

    public static PatternSemanticAdapter find(List<PatternSemanticAdapter> adapters, IPatternDetails pattern) {
        for (PatternSemanticAdapter adapter : adapters) if (adapter.supports(pattern)) return adapter;
        return null;
    }

    public static List<PatternSemanticAdapter> copy(List<PatternSemanticAdapter> adapters) {
        List<PatternSemanticAdapter> result = new ArrayList<>(adapters);
        if (result.isEmpty()) throw new IllegalArgumentException("At least one pattern semantic adapter is required");
        return List.copyOf(result);
    }
}
