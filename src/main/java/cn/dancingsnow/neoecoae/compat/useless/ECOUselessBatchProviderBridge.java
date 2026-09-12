package cn.dancingsnow.neoecoae.compat.useless;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional native alloy-furnace batching; older Useless builds retain the ordinary CPU push invocation. */
public final class ECOUselessBatchProviderBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final ReflectionApi API = ReflectionApi.load();
    private static final boolean SCALED_API_AVAILABLE = hasScaledApi();

    private ECOUselessBatchProviderBridge() {}

    public static boolean supports(ICraftingProvider provider) {
        if (SCALED_API_AVAILABLE) return ECOUselessScaledBatchDispatch.supports(provider)
            && ECOUselessDynamicOutputBridge.isAvailable();
        if (API == null || !ECOUselessDynamicOutputBridge.isAvailable()) return false;
        try {
            return (boolean) API.supports.invoke(null, provider);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            LOGGER.debug("Useless batch provider lookup unavailable", unavailable);
            return false;
        }
    }

    @Nullable
    public static ECOFastPathDispatchProvider adapt(ICraftingProvider provider) {
        if (!supports(provider)) return null;
        if (SCALED_API_AVAILABLE) return new ECOUselessScaledBatchDispatch(provider);
        try {
            Object dispatcher = API.forProvider.invoke(null, provider);
            return dispatcher == null ? null : new Adapter(API, dispatcher);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            LOGGER.debug("Useless batch provider adapter unavailable", unavailable);
            return null;
        }
    }

    private static boolean hasScaledApi() {
        ClassLoader loader = ECOUselessBatchProviderBridge.class.getClassLoader();
        Class<?> patterns;
        try {
            patterns = Class.forName(
                "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns",
                false, loader);
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
        try {
            validateScaledApi(patterns, Class.forName(
                "com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe", false, loader));
            return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            LOGGER.warn("Useless scaled batch API is incompatible; using legacy/ordinary dispatch. "
                + "Use Useless 1.21.1-2.3.4 for scaled batching", unavailable);
            return false;
        }
    }

    /** Probe without linking the adapter: older releases have scale() but lack manual operation resolution. */
    static void validateScaledApi(Class<?> patterns, Class<?> recipe) throws NoSuchMethodException {
        requireStaticMethod(patterns, "scale", null, IPatternDetails.class, long.class);
        if (!IPatternDetails.class.isAssignableFrom(
                patterns.getMethod("scale", IPatternDetails.class, long.class).getReturnType())) {
            throw new NoSuchMethodException("scale must return a pattern");
        }
        requireStaticMethod(patterns, "manualOperationsPerPattern", long.class, recipe, IPatternDetails.class);
        requireStaticMethod(patterns, "maximumSafeMultiplier", long.class, IPatternDetails.class);
        requireStaticMethod(patterns, "resolve", null, IPatternDetails.class);
        Class<?> resolved = patterns.getMethod("resolve", IPatternDetails.class).getReturnType();
        if (resolved.getMethod("pattern").getReturnType() != IPatternDetails.class
                || resolved.getMethod("operationsPerPush").getReturnType() != long.class) {
            throw new NoSuchMethodException("Incompatible smart-doubling resolved pattern");
        }
    }

    private static void requireStaticMethod(Class<?> owner, String name, @Nullable Class<?> result,
            Class<?>... parameters) throws NoSuchMethodException {
        Method method = owner.getMethod(name, parameters);
        if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())
                || (result != null && method.getReturnType() != result)) {
            throw new NoSuchMethodException("Incompatible " + owner.getName() + "." + name);
        }
    }

    record Adapter(ReflectionApi api, Object dispatcher) implements ECOFastPathDispatchProvider {
        @Override
        public @Nullable Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
            long capacity = (long) invoke(api.availableCount, dispatcher,
                context.pattern(), context.inputCounters(), Long.MAX_VALUE);
            if (capacity <= 0L) return null;
            return new Preparation(capacity, null, false, batch -> (boolean) invoke(api.dispatch, dispatcher,
                context.pattern(), context.inputCounters(), batch.craftCount()));
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access Useless batch dispatch", failure);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Useless batch dispatch failed", cause);
        }
    }

    record ReflectionApi(Method supports, Method forProvider, Method availableCount, Method dispatch) {
        static ReflectionApi resolve(Class<?> dispatcher) throws NoSuchMethodException {
            return new ReflectionApi(
                dispatcher.getMethod("supports", ICraftingProvider.class),
                dispatcher.getMethod("forProvider", ICraftingProvider.class),
                dispatcher.getMethod("availableCount", IPatternDetails.class, KeyCounter[].class, long.class),
                dispatcher.getMethod("dispatch", IPatternDetails.class, KeyCounter[].class, long.class));
        }

        @Nullable
        private static ReflectionApi load() {
            try {
                return resolve(Class.forName(
                    "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AlloyFurnaceBatchDispatch",
                    false, ECOUselessBatchProviderBridge.class.getClassLoader()));
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
