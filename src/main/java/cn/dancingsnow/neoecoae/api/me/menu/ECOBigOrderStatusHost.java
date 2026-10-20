package cn.dancingsnow.neoecoae.api.me.menu;

import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress;
import org.jetbrains.annotations.Nullable;

/** Cache belongs to one menu instance and is discarded with that menu. */
public interface ECOBigOrderStatusHost {
    void neoecoae$setBigOrderProgress(int cpuSerial, @Nullable ECOBigOrderProgress progress);
    @Nullable ECOBigOrderProgress neoecoae$getBigOrderProgress();
    void neoecoae$clearBigOrderProgress();
    default void neoecoae$applyExactAmounts(int serial, boolean full,
            cn.dancingsnow.neoecoae.network.MapDelta<appeng.api.stacks.AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> stored,
            cn.dancingsnow.neoecoae.network.MapDelta<appeng.api.stacks.AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> active,
            cn.dancingsnow.neoecoae.network.MapDelta<appeng.api.stacks.AEKey, cn.dancingsnow.neoecoae.crafting.amount.ExactAmount> pending) {}
    default java.math.BigInteger neoecoae$getExactPending(appeng.api.stacks.AEKey key) { return null; }
    default java.math.BigInteger neoecoae$getExactStored(appeng.api.stacks.AEKey key) { return null; }
    default java.math.BigInteger neoecoae$getExactActive(appeng.api.stacks.AEKey key) { return null; }
}
