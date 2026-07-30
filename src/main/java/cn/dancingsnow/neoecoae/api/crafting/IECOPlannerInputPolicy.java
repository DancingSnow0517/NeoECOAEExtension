package cn.dancingsnow.neoecoae.api.crafting;

import appeng.api.crafting.IPatternDetails;

/**
 * Optional contract for patterns that deliberately relax an input slot's component matching.
 * Patterns remain strict unless they implement this interface and explicitly opt a slot in.
 */
public interface IECOPlannerInputPolicy {
    default MatchMode getPlannerInputMatchMode(int slot, IPatternDetails.IInput input) {
        return MatchMode.STRICT;
    }

    enum MatchMode {
        /** The concrete AEKey, including item data components, must match exactly. */
        STRICT,
        /** Any inventory component variant of the same item is an eligible planner candidate. */
        ITEM_ONLY
    }
}
