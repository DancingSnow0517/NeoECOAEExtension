package cn.dancingsnow.neoecoae.compat.useless;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.ECOBatchCapacityProvider;
import cn.dancingsnow.neoecoae.api.me.ECOBatchDispatchContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional native alloy-furnace batching; older Useless builds retain the ordinary CPU push invocation. */
public final class ECOUselessBatchProviderBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final ReflectionApi API = ReflectionApi.load();

    private ECOUselessBatchProviderBridge() {}

    public static boolean supports(ICraftingProvider provider) {
        if (API == null || !ECOUselessDynamicOutputBridge.isAvailable()) return false;
        try {
            return (boolean) API.supports.invoke(null, provider);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            LOGGER.debug("Useless batch provider lookup unavailable", unavailable);
            return false;
        }
    }

    @Nullable
    public static ECOBatchCapacityProvider adapt(ICraftingProvider provider) {
        if (!supports(provider)) return null;
        try {
            Object dispatcher = API.forProvider.invoke(null, provider);
            return dispatcher == null ? null : new Adapter(API, dispatcher);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            LOGGER.debug("Useless batch provider adapter unavailable", unavailable);
            return null;
        }
    }

    record Adapter(ReflectionApi api, Object dispatcher) implements ECOBatchCapacityProvider {
        @Override
        public long eco$getBatchCapacity(ECOBatchDispatchContext context) {
            return (long) invoke(api.availableCount, dispatcher,
                context.pattern(), context.inputCounters(), Long.MAX_VALUE);
        }

        @Override
        public boolean eco$pushBatch(ECOBatchDispatchContext context, long craftCount) {
            return (boolean) invoke(api.dispatch, dispatcher,
                context.pattern(), context.inputCounters(), craftCount);
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
