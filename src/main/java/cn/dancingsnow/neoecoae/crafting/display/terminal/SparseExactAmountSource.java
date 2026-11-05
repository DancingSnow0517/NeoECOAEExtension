package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.Nullable;

/**
 * A source whose ordinary long listing is authoritative except for explicitly reported keys.
 * All methods observe the same visibility and access rules as the ordinary listing.
 */
public interface SparseExactAmountSource extends ExactAmountSource {
    default boolean neoecoae$hasSparseExactAmounts() { return true; }

    /** False lets an empty exceptional set be reused without querying individual changed keys. */
    default boolean neoecoae$mayHaveExceptionalAmounts() { return true; }

    void neoecoae$visitExceptionalAmounts(BiConsumer<AEKey, ExactAmount> visitor);

    /** Null means the ordinary listed amount is exact, or the key is not visible. */
    @Nullable ExactAmount neoecoae$exceptionalAmount(AEKey key);
}
