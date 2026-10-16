package cn.dancingsnow.neoecoae.api.me.pattern;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;

/** Optional semantics beyond AE2's ordinary {@link IPatternDetails} contract. */
public interface ECOPatternSemanticAdapter {
    boolean supports(IPatternDetails pattern);

    /**
     * Refines the ordinary AE2 analysis. The result must retain the supplied physical pattern instance.
     * A matching adapter must explicitly return unsupported semantics when its contract cannot be analyzed.
     */
    ECOPatternSemantics analyze(IPatternDetails pattern, ECOPatternSemantics ae2Semantics);

    default boolean ignoresComponents(IPatternDetails pattern, int inputSlot) {
        return false;
    }

    default AEKey preferredInputKey(IPatternDetails pattern, int inputSlot, AEKey encodedKey,
            ICraftingService craftingService) {
        return encodedKey;
    }

    default String name() {
        return getClass().getSimpleName();
    }
}
