package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.stacks.AEItemKey;

/** Optional bulk-cell display control, independent of marker selection. */
public interface IECOBulkDisplayCell {
    AEItemKey cycleCompressionCutoff(AEItemKey chainItem, int delta);

    default AEItemKey cycleCompressionCutoff(AEItemKey chainItem) {
        return cycleCompressionCutoff(chainItem, 1);
    }
}
