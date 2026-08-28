package cn.dancingsnow.neoecoae.gui.crafting;

import java.util.function.Supplier;

/** Reflective bridge to client-only UI classes; falls back on dedicated servers, which never load them. */
public final class ClientUIBridge {
    private static final String CLIENT_UI_CLASS = "cn.dancingsnow.neoecoae.client.CraftingInterfaceClientUI";

    private ClientUIBridge() {
    }

    public static <T> T call(String methodName, Class<?> paramType, Object arg, Class<T> resultType, Supplier<T> fallback) {
        try {
            Object result = Class.forName(CLIENT_UI_CLASS).getMethod(methodName, paramType).invoke(null, arg);
            if (resultType.isInstance(result)) {
                return resultType.cast(result);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Dedicated servers do not load the client-only implementation.
        }
        return fallback.get();
    }
}
