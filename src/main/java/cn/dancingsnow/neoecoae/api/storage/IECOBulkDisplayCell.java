package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.stacks.AEItemKey;

/** Optional bulk-cell display control, independent of marker selection. */
public interface IECOBulkDisplayCell {
    AEItemKey cycleCompressionCutoff(AEItemKey chainItem);
}
