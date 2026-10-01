package cn.dancingsnow.neoecoae.crafting.planner.semantic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;

/** Planner-owned boundary for pattern contracts supplied by AE2 or an integration mod. */
public interface PatternSemanticAdapter {
    boolean supports(IPatternDetails pattern);

    PatternSemantics analyze(IPatternDetails pattern);

    /**
     * Returns whether the input at the supplied semantic slot ignores item components during matching.
     * Optional integrations override this at their compatibility boundary so the core compiler does not
     * link against an integration-only pattern interface.
     */
    default boolean ignoresComponents(IPatternDetails pattern, int inputSlot) {
        return false;
    }

    /** Chooses an accepted, equal-amount child template without changing the physical pattern. */
    default AEKey preferredInputKey(IPatternDetails pattern, int inputSlot, AEKey encodedKey,
            ICraftingService craftingService) {
        return encodedKey;
    }

    default String name() {
        return getClass().getSimpleName();
    }
}
