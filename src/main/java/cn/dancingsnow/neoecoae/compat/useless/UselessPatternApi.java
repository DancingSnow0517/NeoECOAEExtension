package cn.dancingsnow.neoecoae.compat.useless;

import appeng.api.crafting.IPatternDetails;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Reads Useless's public pattern contracts without requiring the optional mod or applied mixins. */
final class UselessPatternApi {
    private static final Class<?> DYNAMIC = optionalType("DynamicComponentPattern");
    private static final Class<?> SCALED = optionalType("ScaledPattern");
    private static final Method ITEM_ID_INPUT = method(DYNAMIC, "isItemIdInput", int.class);
    private static final Method TAG_INPUT = method(DYNAMIC, "isTagInput", int.class);
    private static final Method FLUID_TAG_INPUT = method(DYNAMIC, "isFluidTagInput", int.class);
    private static final Method DYNAMIC_OUTPUTS = method(DYNAMIC, "usesDynamicOutputs");
    private static final Method ORIGINAL = method(SCALED, "getOriginal");

    private UselessPatternApi() {}

    @Nullable
    static UselessDynamicPatternView dynamicView(IPatternDetails pattern) {
        Set<IPatternDetails> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        IPatternDetails current = pattern;
        while (current != null && visited.add(current)) {
            if (current instanceof UselessDynamicPatternView view) return view;
            if (DYNAMIC != null && DYNAMIC.isInstance(current)) return new PublicDynamicView(current);
            if (current instanceof UselessScaledPatternView view) {
                current = view.neoecoae$getOriginal();
            } else if (SCALED != null && SCALED.isInstance(current)) {
                current = (IPatternDetails) invoke(ORIGINAL, current);
            } else {
                return null;
            }
        }
        return null;
    }

    private record PublicDynamicView(IPatternDetails pattern) implements UselessDynamicPatternView {
        @Override public boolean neoecoae$isItemIdInput(int slot) {
            return (boolean) invoke(ITEM_ID_INPUT, pattern, slot);
        }

        @Override public boolean neoecoae$isTagInput(int slot) {
            return TAG_INPUT != null && (boolean) invoke(TAG_INPUT, pattern, slot);
        }

        @Override public boolean neoecoae$isFluidTagInput(int slot) {
            return FLUID_TAG_INPUT != null && (boolean) invoke(FLUID_TAG_INPUT, pattern, slot);
        }

        @Override public boolean neoecoae$usesDynamicOutputs() {
            return (boolean) invoke(DYNAMIC_OUTPUTS, pattern);
        }
    }

    @Nullable
    private static Class<?> optionalType(String name) {
        try {
            return Class.forName("com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae." + name,
                false, UselessPatternApi.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return null;
        }
    }

    @Nullable
    private static Method method(@Nullable Class<?> type, String name, Class<?>... parameters) {
        if (type == null) return null;
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException unavailable) {
            return null;
        }
    }

    private static Object invoke(@Nullable Method method, Object target, Object... arguments) {
        if (method == null) throw new IllegalStateException("Missing Useless pattern contract method");
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Useless pattern contract failed", failure.getCause());
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access Useless pattern contract", failure);
        }
    }
}
