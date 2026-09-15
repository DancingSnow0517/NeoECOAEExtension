package cn.dancingsnow.neoecoae.api;

import net.minecraft.world.item.ItemStack;

/**
 * Outcome of routing one encoded pattern into the network's pattern storage.
 *
 * <p>{@link ECOPatternInsertionResult} on its own cannot say <em>where</em> the pattern ended up, and the
 * difference matters to any caller that replaces the source. A pattern that lands in a bus slot was stored
 * as an item, so the network already holds that exact stack and the source is cleared without anything
 * returned - handing a blank back there would mint one for a pattern that was merely moved. A pattern that a
 * container absorbs - a pattern disk stores only the recipe - consumes the item, so the caller has to hand a
 * blank back or the player silently loses one. Callers that never remove a source pattern can keep using the
 * plain result.</p>
 *
 * @param result                the plain insertion result
 * @param absorbedByContainer   whether a container took the recipe instead of a slot, consuming the item
 * @param blankReplacement      what to return for a consumed pattern; empty when nothing should be returned
 */
public record ECOPatternInsertion(ECOPatternInsertionResult result, boolean absorbedByContainer,
                                  ItemStack blankReplacement) {
    public ECOPatternInsertion {
        blankReplacement = blankReplacement == null ? ItemStack.EMPTY : blankReplacement;
    }

    /** An outcome that a slot produced, or one nothing consumed a pattern for. */
    public static ECOPatternInsertion of(ECOPatternInsertionResult result) {
        return new ECOPatternInsertion(result, false, ItemStack.EMPTY);
    }

    /**
     * @return whether a container took the recipe. When true the source pattern's item is gone and the
     *         caller has to return {@link #blankReplacement()} instead - see the contract on
     *         {@code IECOPatternStorageService#blankPatternReplacementFor}.
     */
    public boolean consumedSource() {
        return absorbedByContainer;
    }
}
