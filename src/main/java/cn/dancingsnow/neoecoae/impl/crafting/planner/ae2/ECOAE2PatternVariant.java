package cn.dancingsnow.neoecoae.impl.crafting.planner.ae2;

import appeng.api.crafting.IPatternDetails;
import java.util.List;
import java.util.Objects;

/** A deterministic input-choice expansion of one AE2 pattern. */
public record ECOAE2PatternVariant(IPatternDetails pattern, int ordinal, List<ECOAE2InputSelection> selectedInputs) {
    public ECOAE2PatternVariant {
        Objects.requireNonNull(pattern, "pattern");
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative");
        }
        selectedInputs = List.copyOf(Objects.requireNonNull(selectedInputs, "selectedInputs"));
    }
}
