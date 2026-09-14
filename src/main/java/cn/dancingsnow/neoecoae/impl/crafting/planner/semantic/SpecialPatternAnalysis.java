package cn.dancingsnow.neoecoae.impl.crafting.planner.semantic;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.impl.crafting.planner.compile.CompiledInput;
import java.util.List;

/** Precomputed special-input contract. Empty is the allocation-free normal-pattern fast path. */
public record SpecialPatternAnalysis(List<Requirement> requirements) {
    public static final SpecialPatternAnalysis NONE = new SpecialPatternAnalysis(List.of());

    public SpecialPatternAnalysis {
        requirements = List.copyOf(requirements);
    }

    public boolean special() {
        return !requirements.isEmpty();
    }

    public Requirement requirementFor(CompiledInput input) {
        for (Requirement requirement : requirements) {
            if (requirement.input() == input) return requirement;
        }
        return null;
    }

    public boolean excludesFromCycleGraph(CompiledInput input) {
        return requirementFor(input) != null;
    }

    public enum Type {
        DURABILITY,
        CONTAINER,
        REUSABLE,
        CATALYST
    }

    public record Requirement(
        CompiledInput input,
        AEKey returnedKey,
        Type type,
        int damagePerUse,
        int maxDamage
    ) {
    }
}
