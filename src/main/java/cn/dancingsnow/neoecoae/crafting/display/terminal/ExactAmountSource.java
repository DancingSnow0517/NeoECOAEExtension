package cn.dancingsnow.neoecoae.crafting.display.terminal;

import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;

import appeng.api.stacks.AEKey;
import java.util.function.BiConsumer;

/** Optional side-channel for ECO and adapted addon storages that exceed AE2's long amount API. */
public interface ExactAmountSource {
    void neoecoae$visitExactAmounts(BiConsumer<AEKey, ExactAmount> visitor);

    /**
     * Opt in only when the ordinary listing appends nonnegative, saturating amounts to an
     * existing counter. Transparent use of that listing then needs no intermediate copy.
     */
    default boolean neoecoae$hasSaturatingListing() { return false; }
}
