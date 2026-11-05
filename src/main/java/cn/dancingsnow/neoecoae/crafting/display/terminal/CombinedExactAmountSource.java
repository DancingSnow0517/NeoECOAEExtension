package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import java.util.function.BiConsumer;

/** Emits the full visible contribution and its exact values in one traversal, on the server thread. */
public interface CombinedExactAmountSource extends ExactAmountSource {
    void neoecoae$listWithExactAmounts(KeyCounter out, BiConsumer<AEKey, ExactAmount> visitor);

    /** Sources with faithful long projections can avoid constructing ExactAmount for ordinary stock. */
    default void neoecoae$listWithExactAmounts(KeyCounter out, java.util.function.ObjLongConsumer<AEKey> ordinary,
            BiConsumer<AEKey, ExactAmount> exact) {
        // Preserve existing sources, including those whose ordinary API clamps below Long.MAX_VALUE.
        neoecoae$listWithExactAmounts(out, exact);
    }
}
