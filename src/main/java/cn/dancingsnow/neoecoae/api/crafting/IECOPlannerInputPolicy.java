package cn.dancingsnow.neoecoae.api.crafting;

import appeng.api.crafting.IPatternDetails;

/**
 * Optional contract for patterns that deliberately relax an input slot's component matching.
 * Patterns remain strict unless they implement this interface and explicitly opt a slot in.
 * The selected behavior must be deterministic, finite, and independent of the provider that
 * eventually executes the pattern.
 */
public interface IECOPlannerInputPolicy extends IECOPlannerCompatiblePattern {
    @Override
    default InputSemantics getECOPlannerInputSemantics() {
        return InputSemantics.UNIFORM_ALTERNATIVES;
    }

    default MatchMode getPlannerInputMatchMode(int slot, IPatternDetails.IInput input) {
        return MatchMode.STRICT;
    }

    enum MatchMode {
        /** The concrete AEKey, including item data components, must match exactly. */
        STRICT,
        /**
         * Any exact component variant of the same item already present in the captured inventory
         * is eligible. This mode does not permit fuzzy, provider-scoped, random, or unbounded
         * matching behavior.
         */
        ITEM_ONLY
    }
}
