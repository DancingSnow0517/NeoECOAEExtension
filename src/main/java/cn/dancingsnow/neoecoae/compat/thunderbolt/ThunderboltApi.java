package cn.dancingsnow.neoecoae.compat.thunderbolt;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Resolves the relocated Thunderbolt contracts without linking against either version. */
public final class ThunderboltApi {
    public static final Class<?> OVERLOAD = findType(
            "com.moakiee.thunderbolt.core.crafting.overload.OverloadedPatternDetails",
            "com.moakiee.thunderbolt.ae2.overload.pattern.OverloadedProviderOnlyPatternDetails");
    public static final Class<?> WRAPPER = findType(
            "com.moakiee.thunderbolt.core.crafting.pattern.IWrappedPatternDetails",
            "com.moakiee.thunderbolt.ae2.overload.pattern.WrappedPatternDetails");
    public static final Class<?> BATCH_PROVIDER = findType(
            "com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider",
            "com.moakiee.thunderbolt.ae2.api.crafting.IBatchCraftingProvider");

    private ThunderboltApi() {}

    public static boolean isInstance(Class<?> contract, Object value) {
        return contract != null && contract.isInstance(value);
    }

    public static Method method(Class<?> contract, String name, Class<?>... parameters) {
        if (contract == null) return null;
        try {
            return contract.getMethod(name, parameters);
        } catch (NoSuchMethodException failure) {
            throw new IllegalStateException("Unsupported Thunderbolt contract: " + contract.getName(), failure);
        }
    }

    public static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("Thunderbolt invocation failed", failure.getCause());
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access Thunderbolt contract", failure);
        }
    }

    private static Class<?> findType(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name, false, ThunderboltApi.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError unavailable) {
                // Try the previous package layout, or leave this optional integration disabled.
            }
        }
        return null;
    }
}
