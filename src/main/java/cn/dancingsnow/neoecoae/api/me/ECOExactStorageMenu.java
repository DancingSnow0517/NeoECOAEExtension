package cn.dancingsnow.neoecoae.api.me;

import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import java.util.Map;

public interface ECOExactStorageMenu {
    Map<AEKey, ExactAmount> neoecoae$getExactAmounts();

    void neoecoae$setExactAmounts(Map<AEKey, ExactAmount> amounts);
}
