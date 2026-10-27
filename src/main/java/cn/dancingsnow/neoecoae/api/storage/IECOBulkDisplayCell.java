package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.stacks.AEItemKey;

import java.util.List;

/** Optional bulk-cell display control, independent of marker selection. */
public interface IECOBulkDisplayCell {
    AEItemKey cycleCompressionCutoff(AEItemKey chainItem, int delta);

    default AEItemKey cycleCompressionCutoff(AEItemKey chainItem) {
        return cycleCompressionCutoff(chainItem, 1);
    }

    /**
     * Every denomination of the compression chain containing {@code item}, ordered from the atom upwards.
     * Returns a single-element list when the item has no compression chain. Callers use this to group
     * markers that are equivalent forms of the same chain.
     */
    default List<AEItemKey> getCompressionChain(AEItemKey item) {
        return List.of(item);
    }

    /**
     * The cutoff actually in effect for the chain containing {@code item}. Implementations that keep a
     * separate cutoff should return it here, so a display never shows a marker the cell will ignore.
     */
    default AEItemKey getCompressionCutoff(AEItemKey item) {
        return item;
    }
}
