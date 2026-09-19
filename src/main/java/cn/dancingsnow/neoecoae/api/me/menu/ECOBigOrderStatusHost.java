package cn.dancingsnow.neoecoae.api.me.menu;

import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress;
import org.jetbrains.annotations.Nullable;

/** Cache belongs to one menu instance and is discarded with that menu. */
public interface ECOBigOrderStatusHost {
    void neoecoae$setBigOrderProgress(int cpuSerial, @Nullable ECOBigOrderProgress progress);
    @Nullable ECOBigOrderProgress neoecoae$getBigOrderProgress();
    void neoecoae$clearBigOrderProgress();
    default java.math.BigInteger neoecoae$getExactPending(appeng.api.stacks.AEKey key) { return null; }
    default java.math.BigInteger neoecoae$getExactStored(appeng.api.stacks.AEKey key) { return null; }
    default java.math.BigInteger neoecoae$getExactActive(appeng.api.stacks.AEKey key) { return null; }
}
