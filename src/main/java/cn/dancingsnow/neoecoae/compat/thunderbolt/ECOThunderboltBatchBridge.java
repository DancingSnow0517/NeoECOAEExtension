package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Optional reflection boundary for Thunderbolt's {@code IBatchCraftingProvider}. */
public final class ECOThunderboltBatchBridge {
    private static final ReflectionApi API = ReflectionApi.load();

    private ECOThunderboltBatchBridge() {
    }

    public static boolean supports(ICraftingProvider provider) {
        return API != null && provider != null && API.batchProviderType.isInstance(provider);
    }

    public static long capacity(ICraftingProvider provider, IPatternDetails pattern) {
        if (!supports(provider)) return 0L;
        try {
            return (long) API.getBatchCapacity.invoke(provider, pattern);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access Thunderbolt batch capacity", failure);
        } catch (InvocationTargetException failure) {
            throw propagate(failure.getCause());
        }
    }

    public static long pushBatch(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] oneCopyTemplate, long maxCraft, Level level, @Nullable UUID craftingId) {
        if (!supports(provider)) return maxCraft;
        try {
            Object context = API.contextConstructor.newInstance(
                pattern, oneCopyTemplate, maxCraft, level, craftingId);
            return (long) API.pushBatch.invoke(provider, context);
        } catch (InvocationTargetException failure) {
            throw propagate(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot invoke Thunderbolt batch provider", failure);
        }
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof RuntimeException runtime) return runtime;
        if (failure instanceof Error error) throw error;
        return new IllegalStateException("Thunderbolt batch provider failed", failure);
    }

    private record ReflectionApi(
            Class<?> batchProviderType,
            Method getBatchCapacity,
            Method pushBatch,
            Constructor<?> contextConstructor) {
        @Nullable
        private static ReflectionApi load() {
            try {
                ClassLoader loader = ECOThunderboltBatchBridge.class.getClassLoader();
                Class<?> provider = Class.forName(
                    "com.moakiee.thunderbolt.ae2.api.crafting.IBatchCraftingProvider", false, loader);
                Class<?> context = Class.forName(
                    "com.moakiee.thunderbolt.ae2.api.crafting.BatchDispatchContext", false, loader);
                return new ReflectionApi(
                    provider,
                    provider.getMethod("getBatchCapacity", IPatternDetails.class),
                    provider.getMethod("pushBatch", context),
                    context.getConstructor(IPatternDetails.class, KeyCounter[].class, long.class,
                        Level.class, UUID.class));
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
