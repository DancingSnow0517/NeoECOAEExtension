package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Single reflection boundary for traversing provider pattern wrappers from optional integrations. */
public final class ECOProviderPatternIntrospection {
    private static final String[] WRAPPER_METHODS = {
        "wrappedPatternDetails", "getWrappedPatternDetails", "wrappedPattern", "delegate"
    };
    private static final ClassValue<WrapperAccessors> ACCESSORS = new ClassValue<>() {
        @Override
        protected WrapperAccessors computeValue(Class<?> type) {
            return WrapperAccessors.discover(type);
        }
    };

    private ECOProviderPatternIntrospection() {
    }

    public static List<Object> wrapperChain(Object original) {
        List<Object> result = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Object current = original;
        while (current != null && seen.add(current)) {
            result.add(current);
            current = ACCESSORS.get(current.getClass()).next(current);
        }
        return List.copyOf(result);
    }

    @Nullable
    public static IPatternDetails unwrap(IPatternDetails original) {
        if (original == null) return null;
        WrapperAccessors accessors = ACCESSORS.get(original.getClass());
        if (accessors.isEmpty()) return original;

        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Object current = original;
        Object last = null;
        while (current != null && seen.add(current)) {
            last = current;
            current = accessors.next(current);
            if (current != null) accessors = ACCESSORS.get(current.getClass());
        }
        return last instanceof IPatternDetails pattern ? pattern : null;
    }

    @Nullable
    private static Object invoke(Object target, @Nullable Method method) {
        if (method == null) return null;
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    private record WrapperAccessors(@Nullable Method providerLookup, Method[] wrappers) {
        static WrapperAccessors discover(Class<?> type) {
            Method providerLookup = find(type, "providerLookupPattern");
            List<Method> wrappers = new ArrayList<>(WRAPPER_METHODS.length);
            for (String name : WRAPPER_METHODS) {
                Method method = find(type, name);
                if (method != null) wrappers.add(method);
            }
            return new WrapperAccessors(providerLookup, wrappers.toArray(Method[]::new));
        }

        boolean isEmpty() {
            return providerLookup == null && wrappers.length == 0;
        }

        @Nullable
        Object next(Object target) {
            Object next = invoke(target, providerLookup);
            if (next != null && next != target) return next;
            for (Method wrapper : wrappers) {
                Object candidate = invoke(target, wrapper);
                if (candidate != null) return candidate;
            }
            return next;
        }

        @Nullable
        private static Method find(Class<?> type, String name) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException | SecurityException unavailable) {
                return null;
            }
        }
    }
}
