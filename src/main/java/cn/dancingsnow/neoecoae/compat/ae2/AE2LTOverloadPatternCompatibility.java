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
                Method identity = type.getMethod("overloadPatternIdentity");
                Method details = type.getMethod("overloadPatternDetailsView");
                Method hostKind = type.getMethod("requiredHostKind");
                if (fuzzyInput.getReturnType() != boolean.class
                        || identity.getReturnType() != String.class
                        || details.getReturnType() == void.class
                        || hostKind.getReturnType() == void.class) {
                    return Bindings.NONE;
                }
                return new Bindings(fuzzyInput);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return Bindings.NONE;
            }
        }
    };

    private AE2LTOverloadPatternCompatibility() {
    }

    /** True only when an overload pattern explicitly marks this input as ignore-components. */
    public static boolean ignoresComponents(IPatternDetails details, int slot) {
        if (details == null || slot < 0) {
            return false;
        }
        Method fuzzyInput = BINDINGS.get(details.getClass()).fuzzyInput();
        if (fuzzyInput == null) {
            return false;
        }
        try {
            if (!fuzzyInput.canAccess(details) && !fuzzyInput.trySetAccessible()) {
                return false;
            }
            return Boolean.TRUE.equals(fuzzyInput.invoke(details, slot));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private record Bindings(Method fuzzyInput) {
        private static final Bindings NONE = new Bindings(null);
    }
}
