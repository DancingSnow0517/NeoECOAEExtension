package cn.dancingsnow.neoecoae.impl.crafting.fastpath;

import java.util.List;

/** Structural eligibility shared by every concrete resolution of one encoded pattern. */
public record ECOPatternEligibility(boolean supported, List<InputType> inputTypes, String rejectReason) {
    public enum InputType {
        EXACT,
        TAG_OR_SUBSTITUTION
    }

    public ECOPatternEligibility {
        inputTypes = List.copyOf(inputTypes);
        rejectReason = rejectReason == null ? "" : rejectReason;
    }

    public boolean hasSubstitutionInput() {
        return inputTypes.contains(InputType.TAG_OR_SUBSTITUTION);
    }
}
