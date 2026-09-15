package cn.dancingsnow.neoecoae.impl.crafting.planner.semantic;

import appeng.api.crafting.IPatternDetails;

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

    default String name() {
        return getClass().getSimpleName();
    }
}
