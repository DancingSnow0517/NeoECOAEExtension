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

    private ECOProviderPatternIntrospection() {
    }

    public static List<Object> wrapperChain(Object original) {
        List<Object> result = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Object current = original;
        while (current != null && seen.add(current)) {
            result.add(current);
            Object next = invoke(current, "providerLookupPattern");
            if (next == null || next == current) next = invokeFirst(current, WRAPPER_METHODS);
            current = next;
        }
        return List.copyOf(result);
    }

    @Nullable
    public static IPatternDetails unwrap(IPatternDetails original) {
        List<Object> chain = wrapperChain(original);
        if (chain.isEmpty()) return null;
        Object last = chain.getLast();
        return last instanceof IPatternDetails pattern ? pattern : null;
    }

    @Nullable
    private static Object invokeFirst(Object target, String[] names) {
        for (String name : names) {
            Object result = invoke(target, name);
            if (result != null) return result;
        }
        return null;
    }

    @Nullable
    private static Object invoke(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }
}
