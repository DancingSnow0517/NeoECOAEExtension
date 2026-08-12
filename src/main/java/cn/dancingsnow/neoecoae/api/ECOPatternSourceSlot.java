package cn.dancingsnow.neoecoae.api;

import appeng.helpers.patternprovider.PatternContainer;

/** A candidate source slot maintained by the grid-wide pattern migration index. */
public record ECOPatternSourceSlot(PatternContainer source, int slot) {
}
