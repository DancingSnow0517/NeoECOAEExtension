package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** AE2LT owns extraction and accounting; the workstation takes one complete accepted slice. */
public final class ECOThunderboltWorkstationBridge {
    private ECOThunderboltWorkstationBridge() {}

    public static long capacity(LargeWorkstationPatternProvider provider) {
        return provider.eco$getAvailableParallelSlots();
    }

    public static long push(LargeWorkstationPatternProvider provider, IPatternDetails pattern,
            KeyCounter[] oneCopy, long requested, @Nullable UUID jobId) {
        if (requested <= 0) return requested;
        long accepted = Math.min(requested, capacity(provider));
        if (accepted <= 0) return requested;
        KeyCounter[] totals = new KeyCounter[oneCopy.length];
        try {
            for (int slot = 0; slot < oneCopy.length; slot++) {
                var total = new KeyCounter();
                for (var entry : oneCopy[slot]) {
                    total.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), accepted));
                }
                totals[slot] = total;
            }
        } catch (ArithmeticException overflow) {
            return requested;
        }
        return provider.eco$pushPatternBatch(pattern, totals, accepted, jobId)
            ? requested - accepted : requested;
    }
}
