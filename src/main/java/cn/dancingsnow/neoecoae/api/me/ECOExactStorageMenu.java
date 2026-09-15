package cn.dancingsnow.neoecoae.api.me;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Map;

public interface ECOExactStorageMenu {
    Map<AEKey, BigInteger> neoecoae$getExactAmounts();

    void neoecoae$setExactAmounts(Map<AEKey, BigInteger> amounts);
}
