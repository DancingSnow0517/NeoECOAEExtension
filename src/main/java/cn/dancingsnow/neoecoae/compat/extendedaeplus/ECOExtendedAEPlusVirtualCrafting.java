package cn.dancingsnow.neoecoae.compat.extendedaeplus;

import java.lang.reflect.Method;

/** Reads EAEP's live upgrade state without linking an optional mod into the CPU. */
public final class ECOExtendedAEPlusVirtualCrafting {
    private static final Method ENABLED = findBridge();

    private ECOExtendedAEPlusVirtualCrafting() {}

    public static boolean isEnabled(Object provider) {
        if (ENABLED == null || !ENABLED.getDeclaringClass().isInstance(provider)) return false;
        try {
            return Boolean.TRUE.equals(ENABLED.invoke(provider));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    private static Method findBridge() {
        try {
            return Class.forName("com.extendedae_plus.compat.PatternProviderLogicVirtualCompatBridge",
                    false, ECOExtendedAEPlusVirtualCrafting.class.getClassLoader())
                    .getMethod("eap$compatIsVirtualCraftingEnabled");
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return null;
        }
    }
}
