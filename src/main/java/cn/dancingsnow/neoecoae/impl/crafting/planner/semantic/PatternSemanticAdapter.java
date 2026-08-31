package cn.dancingsnow.neoecoae.impl.crafting.planner.semantic;

import appeng.api.crafting.IPatternDetails;

/** Planner-owned boundary for pattern contracts supplied by AE2 or an integration mod. */
public interface PatternSemanticAdapter {
    boolean supports(IPatternDetails pattern);

    PatternSemantics analyze(IPatternDetails pattern);

    default String name() {
        return getClass().getSimpleName();
    }
}
