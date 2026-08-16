package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.crafting.IPatternDetails;
import java.lang.reflect.Method;

/**
 * Reads UselessMod's dynamic-component pattern contract without introducing a hard dependency.
 */
public final class UselessModPatternCompatibility {
    private static final ClassValue<Bindings> BINDINGS = new ClassValue<>() {
        @Override
        protected Bindings computeValue(Class<?> type) {
            try {
                Method identity = type.getMethod("dynamicPatternIdentity");
                Method itemIdInput = type.getMethod("isItemIdInput", int.class);
                Method itemIdOutput = type.getMethod("isItemIdOutput", int.class);
                Method dynamicOutputs = type.getMethod("usesDynamicOutputs");
                if (identity.getReturnType() != String.class
                    || itemIdInput.getReturnType() != boolean.class
                    || itemIdOutput.getReturnType() != boolean.class
                    || dynamicOutputs.getReturnType() != boolean.class) {
                    return Bindings.NONE;
                }
                return new Bindings(
                    identity,
                    itemIdInput,
                    optionalBooleanMethod(type, "isTagInput"),
                    optionalBooleanMethod(type, "isFluidTagInput"),
                    itemIdOutput,
                    dynamicOutputs
                );
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return Bindings.NONE;
            }
        }
    };

    private UselessModPatternCompatibility() {
    }

    /** Verifies the complete dynamic-pattern view before ECO opts the pattern into planning. */
    public static boolean isCompatible(IPatternDetails details, int inputCount, int outputCount) {
        if (details == null || inputCount < 0 || outputCount < 0) {
            return false;
        }
        Bindings bindings = BINDINGS.get(details.getClass());
        if (bindings == Bindings.NONE) {
            return false;
        }
        try {
            Object identity = invoke(bindings.identity(), details);
            if (!(identity instanceof String value) || value.isBlank()) {
                return false;
            }
            for (int slot = 0; slot < inputCount; slot++) {
                invokeBoolean(bindings.itemIdInput(), details, slot);
                invokeOptionalBoolean(bindings.tagInput(), details, slot);
                invokeOptionalBoolean(bindings.fluidTagInput(), details, slot);
            }
            for (int slot = 0; slot < outputCount; slot++) {
                invokeBoolean(bindings.itemIdOutput(), details, slot);
            }
            invokeBoolean(bindings.dynamicOutputs(), details);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** Dynamic item-id and tag slots may consume stored component variants of each candidate. */
    public static boolean ignoresComponents(IPatternDetails details, int slot) {
        if (details == null || slot < 0) {
            return false;
        }
        Bindings bindings = BINDINGS.get(details.getClass());
        if (bindings == Bindings.NONE) {
            return false;
        }
        try {
            return invokeBoolean(bindings.itemIdInput(), details, slot)
                || invokeOptionalBoolean(bindings.tagInput(), details, slot)
                || invokeOptionalBoolean(bindings.fluidTagInput(), details, slot);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /** Dynamic item-id outputs may satisfy any component variant of the same item. */
    public static boolean ignoresComponentsOutput(IPatternDetails details, int slot) {
        if (details == null || slot < 0) {
            return false;
        }
        Bindings bindings = BINDINGS.get(details.getClass());
        if (bindings == Bindings.NONE) {
            return false;
        }
        try {
            return invokeBoolean(bindings.itemIdOutput(), details, slot);
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

    private static Method optionalBooleanMethod(Class<?> type, String name)
        throws ReflectiveOperationException {
        try {
            Method method = type.getMethod(name, int.class);
            return method.getReturnType() == boolean.class ? method : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static boolean invokeOptionalBoolean(Method method, Object target, Object... args)
        throws ReflectiveOperationException {
        return method != null && invokeBoolean(method, target, args);
    }

    private record Bindings(
        Method identity,
        Method itemIdInput,
        Method tagInput,
        Method fluidTagInput,
        Method itemIdOutput,
        Method dynamicOutputs
    ) {
        private static final Bindings NONE = new Bindings(null, null, null, null, null, null);
    }
}
