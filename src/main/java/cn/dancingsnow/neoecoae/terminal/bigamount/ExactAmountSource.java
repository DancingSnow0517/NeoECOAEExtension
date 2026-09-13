package cn.dancingsnow.neoecoae.terminal.bigamount;

import appeng.api.stacks.AEKey;
import java.util.function.BiConsumer;

/** Optional side-channel implemented only by ECO storages that can exceed AE2's long amount API. */
public interface ExactAmountSource {
    void neoecoae$visitExactAmounts(BiConsumer<AEKey, ExactAmount> visitor);
}
