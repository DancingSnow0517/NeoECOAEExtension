package cn.dancingsnow.neoecoae.impl.crafting.planner.semantic;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.compat.extendedaeplus.ExtendedAEPlusPatternSemanticAdapter;
import java.util.ArrayList;
import java.util.List;

/** Ordered adapter registry. Integrations are checked before the generic AE2 contract. */
public final class PatternSemanticAdapters {
    private PatternSemanticAdapters() {
    }

    public static List<PatternSemanticAdapter> defaults() {
        List<PatternSemanticAdapter> adapters = new ArrayList<>();
        if (classPresent("com.glodblock.github.extendedae_plus.ExtendedAEPlus")) {
            adapters.add(new ExtendedAEPlusPatternSemanticAdapter());
        }
        adapters.add(new AE2PatternSemanticAdapter());
        return List.copyOf(adapters);
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

    private static boolean classPresent(String name) {
        try {
            Class.forName(name, false, PatternSemanticAdapters.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
