package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import java.util.function.BiConsumer;

/** Emits the full visible contribution and its exact values in one traversal, on the server thread. */
public interface CombinedExactAmountSource extends ExactAmountSource {
    void neoecoae$listWithExactAmounts(KeyCounter out, BiConsumer<AEKey, ExactAmount> visitor);
}
