package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import java.lang.reflect.Method;

/**
 * Reads AE2 Lightning Tech's overload input-match mode without creating a hard dependency on
 * either AE2LT or Thunderbolt Core.
 */
public final class AE2LTOverloadPatternCompatibility {
    private static final ClassValue<Bindings> BINDINGS = new ClassValue<>() {
        @Override
        protected Bindings computeValue(Class<?> type) {
            try {
                Method fuzzyInput = type.getMethod("isFuzzyInput", int.class);
                if (fuzzyInput.getReturnType() == boolean.class
                        && hasOverloadIdentityMethods(type)) {
                    return new Bindings(fuzzyInput, null, null, null);
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Fall through to AE2LT's current inputMode API.
            }
            try {
                Method details = type.getMethod("overloadPatternDetailsView");
                Method inputMode = details.getReturnType().getMethod("inputMode", int.class);
                Method ignoresComponents = inputMode.getReturnType().getMethod("ignoresComponents");
                if (details.getReturnType() == void.class
                        || inputMode.getReturnType() == void.class
                        || ignoresComponents.getReturnType() != boolean.class) {
                    return Bindings.NONE;
                }
                return new Bindings(null, details, inputMode, ignoresComponents);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return Bindings.NONE;
            }
        }

        private static boolean hasOverloadIdentityMethods(Class<?> type)
            throws ReflectiveOperationException {
            Method identity = type.getMethod("overloadPatternIdentity");
            Method details = type.getMethod("overloadPatternDetailsView");
            Method hostKind = type.getMethod("requiredHostKind");
            return identity.getReturnType() == String.class
                && details.getReturnType() != void.class
                && hostKind.getReturnType() != void.class;
        }
    };

    private AE2LTOverloadPatternCompatibility() {
    }

    /** True only when an overload pattern explicitly marks this input as ignore-components. */
    public static boolean ignoresComponents(IPatternDetails details, int slot) {
        if (details == null || slot < 0) {
            return false;
        }
        Bindings bindings = BINDINGS.get(details.getClass());
        if (bindings == Bindings.NONE) {
            return false;
        }
        try {
            if (bindings.fuzzyInput() != null) {
                return invokeBoolean(bindings.fuzzyInput(), details, slot);
            }
            Object detailsView = invoke(bindings.detailsView(), details);
            Object inputMode = invoke(bindings.inputMode(), detailsView, slot);
            return invokeBoolean(bindings.ignoresComponents(), inputMode);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static Object invoke(Method method, Object target, Object... args)
        throws ReflectiveOperationException {
        if (!method.canAccess(target) && !method.trySetAccessible()) {
            throw new IllegalAccessException("Unable to access " + method);
        }
        return method.invoke(target, args);
    }

    private static boolean invokeBoolean(Method method, Object target, Object... args)
        throws ReflectiveOperationException {
        return Boolean.TRUE.equals(invoke(method, target, args));
    }

    private record Bindings(
        Method fuzzyInput,
        Method detailsView,
        Method inputMode,
        Method ignoresComponents
    ) {
        private static final Bindings NONE = new Bindings(null, null, null, null);
    }
}
