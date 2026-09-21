package cn.dancingsnow.neoecoae.compat.ae2lt;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import java.lang.reflect.Method;
import java.util.UUID;

/** Reflection-only AE2LT 2.0.9 output bridge; safe when AE2LT is absent. */
public final class ECOAe2LtCraftingAdapter {
    private static final String API = "com.moakiee.ae2lt.api.crafting.Ae2LtCraftingIntegration";
    private ECOAe2LtCraftingAdapter() {}
    public static long insertCpuOutput(ICraftingCPU cpu, UUID job, AEKey key, long amount, Actionable mode) {
        try {
            Class<?> api = Class.forName(API, false, ECOAe2LtCraftingAdapter.class.getClassLoader());
            for (Method m : api.getMethods()) if (m.getName().equals("insertCpuOutput") && m.getParameterCount() == 5)
                return ((Number)m.invoke(null, cpu, job, key, amount, mode)).longValue();
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) { }
        return 0L;
    }
}
