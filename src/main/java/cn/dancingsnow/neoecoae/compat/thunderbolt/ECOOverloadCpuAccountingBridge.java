package cn.dancingsnow.neoecoae.compat.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Reflection-only AE2LT overload output registration. It intentionally never touches seed/time-wheel APIs. */
public final class ECOOverloadCpuAccountingBridge {
    private ECOOverloadCpuAccountingBridge() {}

    public static Prepared prepare(Object cpuLogic, IPatternDetails originalPattern, UUID jobId,
            @Nullable AEKey finalOutput) {
        Object overload = findOverloadPattern(originalPattern);
        if (overload == null) return Prepared.ordinary();
        try {
            Method detailsView = overload.getClass().getMethod("overloadPatternDetailsView");
            Method identityView = overload.getClass().getMethod("overloadPatternIdentity");
            Object details = detailsView.invoke(overload);
            String identity = (String) identityView.invoke(overload);
            boolean idOnly = hasIdOnlyOutput(details);
            Api api = Api.INSTANCE;
            if (api == null) return idOnly ? Prepared.blocked() : Prepared.ordinary();
            Object source = details.getClass().getMethod("sourcePattern").invoke(details);
            Constructor<?> constructor = api.referenceType.getConstructor(String.class, source.getClass());
            Object reference = constructor.newInstance(identity, source);
            Method ambiguous = api.managerType.getMethod("hasAmbiguousOutputRegistration", Object.class,
                    api.referenceType, details.getClass());
            if (Boolean.TRUE.equals(ambiguous.invoke(api.manager, cpuLogic, reference, details))) {
                return Prepared.blocked();
            }
            Method register = api.managerType.getMethod("registerExpectedOutputs", Object.class, UUID.class,
                    api.referenceType, details.getClass(), List.class, AEKey.class, long.class, Map.class);
            return new Prepared(true, api.manager, register, cpuLogic, jobId, reference, details,
                    originalPattern.getOutputs(), finalOutput);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return hasAdvertisedIdOnlyOutput(overload) ? Prepared.blocked() : Prepared.ordinary();
        }
    }

    @Nullable
    private static Object findOverloadPattern(IPatternDetails original) {
        Object current = original;
        java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (current != null && seen.add(current)) {
            if (method(current.getClass(), "overloadPatternDetailsView") != null) return current;
            Method lookup = method(current.getClass(), "providerLookupPattern");
            Method wrapped = firstMethod(current.getClass(), "wrappedPatternDetails", "getWrappedPatternDetails",
                    "wrappedPattern", "delegate");
            try {
                Object next = lookup != null ? lookup.invoke(current) : wrapped != null ? wrapped.invoke(current) : null;
                if (next == current && wrapped != null) next = wrapped.invoke(current);
                current = next;
            } catch (ReflectiveOperationException | RuntimeException failure) {
                return null;
            }
        }
        return null;
    }

    @Nullable private static Method method(Class<?> type, String name, Class<?>... args) {
        try { return type.getMethod(name, args); } catch (NoSuchMethodException unavailable) { return null; }
    }
    @Nullable private static Method firstMethod(Class<?> type, String... names) {
        for (String name : names) { Method found = method(type, name); if (found != null) return found; }
        return null;
    }

    private static boolean hasIdOnlyOutput(Object details) throws ReflectiveOperationException {
        Object value = details.getClass().getMethod("outputs").invoke(details);
        if (!(value instanceof Iterable<?> outputs)) return false;
        for (Object output : outputs) {
            Object mode = output.getClass().getMethod("matchMode").invoke(output);
            if (mode instanceof Enum<?> enumeration && enumeration.name().equals("ID_ONLY")) return true;
        }
        return false;
    }

    private static boolean hasAdvertisedIdOnlyOutput(Object overload) {
        Method fuzzy = method(overload.getClass(), "isFuzzyOutput", int.class);
        if (fuzzy == null || !(overload instanceof IPatternDetails pattern)) return true;
        try {
            for (int slot = 0; slot < pattern.getOutputs().size(); slot++) {
                if (Boolean.TRUE.equals(fuzzy.invoke(overload, slot))) return true;
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return true;
        }
    }

    public record Prepared(boolean canDispatch, @Nullable Object manager, @Nullable Method register,
            @Nullable Object cpuLogic, @Nullable UUID jobId, @Nullable Object reference,
            @Nullable Object details, @Nullable List<?> outputs, @Nullable AEKey finalOutput) {
        static Prepared ordinary() { return new Prepared(true, null, null, null, null, null, null, null, null); }
        static Prepared blocked() { return new Prepared(false, null, null, null, null, null, null, null, null); }
        public boolean registerAccepted(long acceptedCrafts) {
            if (!canDispatch || acceptedCrafts <= 0) return false;
            if (register == null) return true;
            try {
                register.invoke(manager, cpuLogic, jobId, reference, details, outputs, finalOutput,
                        acceptedCrafts, Map.of());
                return true;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                throw new IllegalStateException("Unable to register accepted AE2LT overload outputs", failure);
            }
        }
    }

    private record Api(Class<?> managerType, Object manager, Class<?> referenceType) {
        private static final Api INSTANCE = resolve();
        @Nullable private static Api resolve() {
            try {
                ClassLoader loader = ECOOverloadCpuAccountingBridge.class.getClassLoader();
                Class<?> manager = Class.forName("com.moakiee.thunderbolt.ae2.overload.cpu.OverloadCpuStateManager", false, loader);
                Class<?> reference = Class.forName("com.moakiee.thunderbolt.ae2.overload.cpu.OverloadPatternReference", false, loader);
                return new Api(manager, manager.getField("INSTANCE").get(null), reference);
            } catch (ReflectiveOperationException | LinkageError unavailable) { return null; }
        }
    }
}
