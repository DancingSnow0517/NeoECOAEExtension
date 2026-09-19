package cn.dancingsnow.neoecoae.crafting.display.terminal;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;

/** Side channel implemented by ECO infinite storage, never inferred from a saturated long. */
public interface ExactAmountSource {
    BigInteger neoecoae$getExactAmount(AEKey key);

    Object neoecoae$exactInventoryIdentity();
}
