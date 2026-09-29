package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import java.math.BigInteger;

/** Side channel implemented by ECO infinite storage, never inferred from a saturated long. */
public interface ExactAmountSource {
    BigInteger neoecoae$getExactAmount(AEKey key);

    /** Override for creative or fixed-resource sources whose displayed amount is unbounded. */
    default ExactAmount neoecoae$getDisplayAmount(AEKey key) {
        BigInteger amount = neoecoae$getExactAmount(key);
        return amount == null ? null : ExactAmount.finite(amount);
    }

    Object neoecoae$exactInventoryIdentity();
}
