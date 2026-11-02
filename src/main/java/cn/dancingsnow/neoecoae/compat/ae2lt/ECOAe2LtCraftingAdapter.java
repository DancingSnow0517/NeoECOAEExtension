package cn.dancingsnow.neoecoae.compat.ae2lt;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.UUID;

/** Optional public AE2LT output API; exact signatures are resolved once. */
public final class ECOAe2LtCraftingAdapter {
    private static final String API = "com.moakiee.ae2lt.api.crafting.Ae2LtCraftingIntegration";
    private static final Method INSERT = method("insertCpuOutput", long.class);
    private static final Method EXACT = method("insertExactCpuOutput", BigInteger.class);
    private ECOAe2LtCraftingAdapter() {}
    public static long insertCpuOutput(ICraftingCPU cpu, UUID job, AEKey key, long amount, Actionable mode) {
        return INSERT == null ? 0L : (long) invoke(INSERT, cpu, job, key, amount, mode);
    }

    public static BigInteger insertExactCpuOutput(ICraftingCPU cpu, UUID job, AEKey key,
            BigInteger amount, Actionable mode) {
        return EXACT == null ? BigInteger.ZERO : (BigInteger) invoke(EXACT, cpu, job, key, amount, mode);
    }

    private static Method method(String name, Class<?> amountType) {
        try {
            Class<?> api = Class.forName(API, false, ECOAe2LtCraftingAdapter.class.getClassLoader());
            return api.getMethod(name, ICraftingCPU.class, UUID.class, AEKey.class, amountType, Actionable.class);
        } catch (ReflectiveOperationException | LinkageError absent) { return null; }
    }

    private static Object invoke(Method method, Object... args) {
        try { return method.invoke(null, args); }
        catch (InvocationTargetException failed) {
            if (failed.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failed.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("AE2LT output transfer failed", failed.getCause());
        } catch (ReflectiveOperationException failed) {
            throw new IllegalStateException("Cannot determine AE2LT output transfer", failed);
        }
    }
}
