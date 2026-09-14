package cn.dancingsnow.neoecoae.compat.gtl;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.jetbrains.annotations.Nullable;

/** Optional bridge for GTLCore providers that accept multiple processing operations in one push. */
public final class GTLCraftingProviderCompat {
    private static final NativeAutoExpand NATIVE_AUTO_EXPAND = findNativeAutoExpand();
    private static final String GTL_ME_CRAFT_IO_PART = "org.gtlcore.gtlcore.api.machine.trait.AECraft.IMECraftIOPart";
    private static final String GTL_ME_PATTERN_PART =
            "org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine";
    private static final String GTL_CAPACITY_METHOD = "gtlcore$getMaxPatternOperations";

    private static final ClassValue<ProviderType> PROVIDER_TYPES = new ClassValue<>() {
        @Override
        protected ProviderType computeValue(Class<?> type) {
            boolean autoExpand = hasNamedType(type, GTL_ME_CRAFT_IO_PART) || hasNamedType(type, GTL_ME_PATTERN_PART);
            return new ProviderType(autoExpand, autoExpand ? findCapacityMethod(type) : null);
        }
    };

    private GTLCraftingProviderCompat() {}

    public static boolean isAutoExpandProvider(ICraftingProvider provider) {
        if (provider == null) return false;
        if (NATIVE_AUTO_EXPAND != null) return NATIVE_AUTO_EXPAND.canAutoExpand(provider);
        return PROVIDER_TYPES.get(provider.getClass()).autoExpand();
    }

    public static long getMaxOperations(ICraftingProvider provider, IPatternDetails pattern, long requestedOperations) {
        if (provider == null || pattern == null || requestedOperations <= 0L) {
            return 0L;
        }
        if (NATIVE_AUTO_EXPAND != null) {
            return NATIVE_AUTO_EXPAND.getOperations(provider, pattern, requestedOperations);
        }
        ProviderType type = PROVIDER_TYPES.get(provider.getClass());
        if (!type.autoExpand()) {
            return 1L;
        }
        Method capacityMethod = type.capacityMethod();
        if (capacityMethod == null) {
            return requestedOperations;
        }
        try {
            Object result = capacityMethod.invoke(provider, pattern, requestedOperations);
            return result instanceof Number number
                    ? Math.max(1L, Math.min(requestedOperations, number.longValue()))
                    : 1L;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            // A failed capacity negotiation must fall back to an ordinary one-operation push.
            return 1L;
        }
    }

    private static boolean hasNamedType(Class<?> type, String name) {
        if (type == null || type == Object.class) {
            return false;
        }
        if (type.getName().equals(name)) {
            return true;
        }
        for (Class<?> candidate : type.getInterfaces()) {
            if (hasNamedType(candidate, name)) {
                return true;
            }
        }
        return hasNamedType(type.getSuperclass(), name);
    }

    @Nullable private static Method findCapacityMethod(Class<?> type) {
        try {
            return type.getMethod(GTL_CAPACITY_METHOD, IPatternDetails.class, long.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private record ProviderType(boolean autoExpand, @Nullable Method capacityMethod) {}

    @Nullable private static NativeAutoExpand findNativeAutoExpand() {
        try {
            Class<?> helper = Class.forName(
                    "org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternAutoExpand",
                    false,
                    GTLCraftingProviderCompat.class.getClassLoader());
            return NativeAutoExpand.resolve(helper);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Older GTLCore versions only expose the machine interfaces handled above.
            return null;
        }
    }

    /** Resolve the API once, but query settings and downstream capacity for every dispatch. */
    record NativeAutoExpand(Method canExpand, Method operations) {
        static NativeAutoExpand resolve(Class<?> helper) throws NoSuchMethodException {
            return new NativeAutoExpand(
                    helper.getMethod("canAutoExpand", boolean.class, ICraftingProvider.class),
                    helper.getMethod(
                            "getOperations",
                            boolean.class,
                            ICraftingProvider.class,
                            IPatternDetails.class,
                            long.class));
        }

        boolean canAutoExpand(ICraftingProvider provider) {
            try {
                // Only processing patterns reach prepareGtlAutoExpand in the executor.
                return Boolean.TRUE.equals(canExpand.invoke(null, true, provider));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                return false;
            }
        }

        long getOperations(ICraftingProvider provider, IPatternDetails pattern, long requested) {
            try {
                Object result = operations.invoke(null, true, provider, pattern, requested);
                return result instanceof Number number ? Math.max(1L, Math.min(requested, number.longValue())) : 1L;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
                // Do not fall back to an unlimited batch when native negotiation fails.
                return 1L;
            }
        }
    }
}
