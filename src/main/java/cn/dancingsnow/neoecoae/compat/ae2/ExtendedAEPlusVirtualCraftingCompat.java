package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.networking.crafting.ICraftingProvider;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ExtendedAEPlusVirtualCraftingCompat {
    private static final String IS_VIRTUAL_CRAFTING_ENABLED = "eap$compatIsVirtualCraftingEnabled";
    private static final Map<Class<?>, Optional<Method>> IS_VIRTUAL_METHOD_CACHE = new ConcurrentHashMap<>();

    private ExtendedAEPlusVirtualCraftingCompat() {}

    public static boolean isVirtualCraftingProvider(ICraftingProvider provider) {
        Method method = IS_VIRTUAL_METHOD_CACHE
                .computeIfAbsent(provider.getClass(), ExtendedAEPlusVirtualCraftingCompat::findVirtualCraftingMethod)
                .orElse(null);
        if (method == null) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(method.invoke(provider));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Optional<Method> findVirtualCraftingMethod(Class<?> providerClass) {
        try {
            Method method = providerClass.getMethod(IS_VIRTUAL_CRAFTING_ENABLED);
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class || returnType == Boolean.class) {
                return Optional.of(method);
            }
        } catch (NoSuchMethodException e) {
            // ExtendedAE Plus is optional; providers without its bridge are normal providers.
        }
        return Optional.empty();
    }
}
