package cn.dancingsnow.neoecoae.compat.ae2lt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider;
import java.util.function.BooleanSupplier;

/** Preserves job identity on released time-wheel engines without replacing ordinary provider calls. */
public final class ECOAe2LtJobDispatch {
    private ECOAe2LtJobDispatch() {}

    public static boolean push(Object logic, ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, BooleanSupplier ordinary) {
        if (provider instanceof ECOJobAwareCraftingProvider aware) {
            var job = ECOAe2LtCancellationBridge.craftingId(logic);
            if (job != null) return aware.pushPattern(pattern, inputs, job);
        }
        return ordinary.getAsBoolean();
    }
}
