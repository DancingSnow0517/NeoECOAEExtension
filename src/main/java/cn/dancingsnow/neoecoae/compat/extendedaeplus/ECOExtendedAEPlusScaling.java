package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import appeng.api.crafting.IPatternDetails;
import java.lang.reflect.Method;
import org.jetbrains.annotations.Nullable;

/** Optional EAEP smart-doubling boundary for processing patterns. */
public final class ECOExtendedAEPlusScaling {
    private static final ReflectionApi API = ReflectionApi.load();

    private ECOExtendedAEPlusScaling() {}

    /** Applies EAEP's own scaled wrapper when the encoded pattern opted into smart doubling. */
    public static IPatternDetails scale(IPatternDetails pattern, long multiplier) {
        if (API == null || multiplier <= 1 || !API.aware.isInstance(pattern)) return null;
        try {
            if (!(boolean) API.allow.invoke(pattern)) return null;
            int limit = (int) API.limit.invoke(pattern);
            if (limit > 0 && multiplier > limit) return null;
            return (IPatternDetails) API.scale.invoke(null, pattern, multiplier);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /** Returns the provider-configured smart-doubling cap, or {@code requested} when absent. */
    public static long cap(IPatternDetails pattern, long requested) {
        if (API == null || requested <= 0 || !API.aware.isInstance(pattern)) return requested;
        try {
            if (!(boolean) API.allow.invoke(pattern)) return 1;
            int limit = (int) API.limit.invoke(pattern);
            return limit > 0 ? Math.min(requested, limit) : requested;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return requested;
        }
    }

    private record ReflectionApi(Class<?> aware, Method allow, Method limit, Method scale) {
        @Nullable static ReflectionApi load() {
            try {
                ClassLoader loader = ECOExtendedAEPlusScaling.class.getClassLoader();
                Class<?> aware = Class.forName(
                    "com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern", false, loader);
                Class<?> scaler = Class.forName(
                    "com.extendedae_plus.util.smartDoubling.PatternScaler", false, loader);
                Method scale = scaler.getMethod("createScaled", IPatternDetails.class, long.class);
                return new ReflectionApi(aware, aware.getMethod("eap$allowScaling"),
                    aware.getMethod("eap$getMultiplierLimit"), scale);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
