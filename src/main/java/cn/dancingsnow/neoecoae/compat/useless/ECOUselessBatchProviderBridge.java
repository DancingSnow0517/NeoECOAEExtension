package cn.dancingsnow.neoecoae.compat.useless;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional native alloy-furnace batching; older Useless builds retain the ordinary CPU push invocation. */
public final class ECOUselessBatchProviderBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoECOAE.MOD_ID);
    private static final ReflectionApi API = ReflectionApi.load();
    private static final BigIntegerReflectionApi BIG_INTEGER_API = BigIntegerReflectionApi.load();
    private static final boolean SCALED_API_AVAILABLE = hasScaledApi();

    private ECOUselessBatchProviderBridge() {}

    public static boolean supports(ICraftingProvider provider) {
        if (BIG_INTEGER_API != null && BIG_INTEGER_API.supports(provider)
                && ECOUselessDynamicOutputBridge.isAvailable()) return true;
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
        if (BIG_INTEGER_API != null && BIG_INTEGER_API.supports(provider)) {
            return new ECOFastPathDispatchProvider() {
                @Override public @Nullable ExactPreparation eco$prepareExactFastPath(
                        ECOBatchDispatchContext context, BigInteger requested) {
                    Object target = BIG_INTEGER_API.target(provider);
                    return target == null ? null : new BigIntegerAdapter(BIG_INTEGER_API, target)
                        .eco$prepareExactFastPath(context, requested);
                }
                @Override public @Nullable Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
                // Resolve live targets for this dispatch only, never retain one across ticks.
                Object target = BIG_INTEGER_API.target(provider);
                if (target != null) {
                    var preparation = new BigIntegerAdapter(BIG_INTEGER_API, target).eco$prepareFastPath(context);
                    if (preparation != null) return preparation;
                }
                return SCALED_API_AVAILABLE && ECOUselessScaledBatchDispatch.supports(provider)
                    ? new ECOUselessScaledBatchDispatch(provider).eco$prepareFastPath(context) : null;
                }
            };
        }
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

    /** Uses Useless's BigInteger API for the CPU's checked long-sized execution windows. */
    record BigIntegerAdapter(BigIntegerReflectionApi api, Object target) implements ECOFastPathDispatchProvider {
        @Override
        public @Nullable ExactPreparation eco$prepareExactFastPath(ECOBatchDispatchContext context, BigInteger requested) {
            if (requested.signum() <= 0 || invoke(api.unwrap, null, context.pattern()) != context.pattern()) return null;
            var direct = ECOUselessExactCraftingDispatch.prepare(target, context, requested);
            if (direct != null) return direct;
            KeyCounter[] prototype = context.inputCounters();
            Object capacity = invoke(api.capacity, target, context.pattern(), prototype, requested);
            BigInteger accepted = ((BigInteger) invoke(api.accepted, capacity)).min(requested);
            if (accepted.signum() <= 0) return null;
            Object ticket = invoke(api.admit, target, context.pattern(), prototype, accepted, null);
            if (ticket == null) return null;
            BigInteger count = (BigInteger) invoke(api.count, ticket);
            if (count.signum() <= 0 || count.compareTo(accepted) > 0) return null;
            return new ExactPreparation(count, () -> {
                try {
                    return (boolean) invoke(api.commit, ticket, (Object) prototype);
                } catch (RuntimeException | Error failure) {
                    throw new ECOIndeterminateBatchException("Useless BigInteger commit ownership is uncertain", failure);
                }
            });
        }

        @Override
        public @Nullable Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
            KeyCounter[] prototype = context.inputCounters();
            // The native API counts unwrapped copies. Scaled wrappers retain the legacy path
            // so their multiplied inputs/outputs are never paired with an unscaled recipe.
            if (invoke(api.unwrap, null, context.pattern()) != context.pattern()) return null;
            BigInteger requested = BigInteger.valueOf(Long.MAX_VALUE);
            Object capacity = invoke(api.capacity, target, context.pattern(), prototype, requested);
            BigInteger accepted = (BigInteger) invoke(api.accepted, capacity);
            long count = accepted.max(BigInteger.ZERO).min(requested).longValueExact();
            if (count <= 0) return null;
            return new Preparation(count, null, false, batch -> {
                // ECOBatchCraftingExecutor has already debited ALL copies. Useless consumes
                // only this single-copy receipt; never scale it or debit the CPU a second time.
                BigInteger copies = BigInteger.valueOf(batch.craftCount());
                Object ticket = invoke(api.admit, target, context.pattern(), prototype, copies, null);
                if (ticket == null) return false;
                // Admission can shrink after capacity probing. ECO's boolean transaction is
                // all-or-nothing: reject before commit and let its executor restore the full debit.
                if (!copies.equals(invoke(api.count, ticket))) return false;
                try {
                    return (boolean) invoke(api.commit, ticket, (Object) prototype);
                } catch (RuntimeException | Error failure) {
                    // A throwing commit does not guarantee rejection. Do not refund/replay it.
                    throw new ECOIndeterminateBatchException("Useless BigInteger commit ownership is uncertain", failure);
                }
            }, true);
        }
    }

    record BigIntegerReflectionApi(Method capacity, Method accepted, Method admit,
                                   Method commit, Method count, Method bigIntegerTarget, Method unwrap) {
        static BigIntegerReflectionApi load() {
            try {
                ClassLoader loader = ECOUselessBatchProviderBridge.class.getClassLoader();
                Class<?> provider = Class.forName("com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerProvider", false, loader);
                Class<?> target = Class.forName("com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerTarget", false, loader);
                Class<?> capacity = Class.forName("com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerCapacity", false, loader);
                Class<?> batch = Class.forName("com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerBatch", false, loader);
                Class<?> binding = Class.forName("com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuBinding", false, loader);
                Class<?> patterns = Class.forName("com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns", false, loader);
                return resolve(provider, target, capacity, batch, binding, patterns);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }

        static BigIntegerReflectionApi resolve(Class<?> provider, Class<?> target, Class<?> capacity,
                Class<?> batch, Class<?> binding, Class<?> patterns) throws NoSuchMethodException {
            return new BigIntegerReflectionApi(
                    target.getMethod("capacity", IPatternDetails.class, KeyCounter[].class, BigInteger.class),
                    capacity.getMethod("accepted"),
                    target.getMethod("admit", IPatternDetails.class, KeyCounter[].class, BigInteger.class,
                        binding),
                    batch.getMethod("commit", KeyCounter[].class), batch.getMethod("count"),
                    provider.getMethod("bigIntegerTarget"), patterns.getMethod("unwrap", IPatternDetails.class));
        }

        boolean supports(ICraftingProvider provider) {
            return bigIntegerTarget.getDeclaringClass().isInstance(provider);
        }

        @Nullable Object target(ICraftingProvider provider) {
            if (!supports(provider)) return null;
            return invoke(bigIntegerTarget, provider);
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
