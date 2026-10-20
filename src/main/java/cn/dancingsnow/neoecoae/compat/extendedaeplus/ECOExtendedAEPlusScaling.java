package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import appeng.api.crafting.IPatternDetails;
import java.lang.reflect.Method;
import org.jetbrains.annotations.Nullable;

/** Optional EAEP execution wrapper factory. ECO alone chooses the multiplier. */
public final class ECOExtendedAEPlusScaling {
    private static final ReflectionApi API = ReflectionApi.load();

    private ECOExtendedAEPlusScaling() {}

    /** Expresses an ECO-selected batch in EAEP's wrapper format, independently of provider settings. */
    public static IPatternDetails scale(IPatternDetails pattern, long multiplier) {
        if (API == null || multiplier <= 1) return null;
        try {
            return (IPatternDetails) API.scale.invoke(null, pattern, multiplier);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /** Compatibility shim: foreign smart-doubling settings do not cap ECO requests. */
    @Deprecated
    public static long cap(IPatternDetails pattern, long requested) {
        return requested;
    }

    private record ReflectionApi(Method scale) {
        @Nullable static ReflectionApi load() {
            try {
                ClassLoader loader = ECOExtendedAEPlusScaling.class.getClassLoader();
                Class<?> scaler = Class.forName(
                    "com.extendedae_plus.util.smartDoubling.PatternScaler", false, loader);
                Method scale = scaler.getMethod("createScaled", IPatternDetails.class, long.class);
                return new ReflectionApi(scale);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
