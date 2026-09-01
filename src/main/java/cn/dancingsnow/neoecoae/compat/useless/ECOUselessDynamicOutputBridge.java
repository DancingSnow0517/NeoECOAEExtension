package cn.dancingsnow.neoecoae.compat.useless;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Preserves Useless Mod's ECO dynamic-output registration when a counted adapter owns the physical push call. */
public final class ECOUselessDynamicOutputBridge {
    private static final ReflectionApi API = ReflectionApi.load();

    private ECOUselessDynamicOutputBridge() {
    }

    /** Returns a no-op registration for ordinary patterns, or {@code null} when Useless reports ambiguity. */
    @Nullable
    public static Registration prepare(Object cpuLogic, IPatternDetails pattern, long logicalCrafts) {
        if (API == null) return Registration.NOOP;
        try {
            Object resolved = API.resolve.invoke(null, pattern);
            if (resolved == null) return Registration.NOOP;
            Object dynamicPattern = API.resolvedPattern.invoke(resolved);
            if ((boolean) API.hasAmbiguous.invoke(API.manager, cpuLogic, dynamicPattern)) return null;
            long copies = Math.multiplyExact((long) API.resolvedCopies.invoke(resolved), logicalCrafts);
            return new Registration(API, cpuLogic, dynamicPattern, copies);
        } catch (ReflectiveOperationException failure) {
            throw reflectionFailure("prepare Useless dynamic output registration", failure);
        }
    }

    public static final class Registration {
        private static final Registration NOOP = new Registration(null, null, null, 0L);
        @Nullable private final ReflectionApi api;
        @Nullable private final Object cpuLogic;
        @Nullable private final Object dynamicPattern;
        private final long copies;

        private Registration(@Nullable ReflectionApi api, @Nullable Object cpuLogic,
                @Nullable Object dynamicPattern, long copies) {
            this.api = api;
            this.cpuLogic = cpuLogic;
            this.dynamicPattern = dynamicPattern;
            this.copies = copies;
        }

        public void commit(UUID craftingId, @Nullable AEKey finalOutput) {
            if (api == null) return;
            try {
                api.registerExpected.invoke(api.manager, cpuLogic, craftingId, dynamicPattern, finalOutput, copies);
            } catch (ReflectiveOperationException failure) {
                throw reflectionFailure("commit Useless dynamic output registration", failure);
            }
        }
    }

    private static IllegalStateException reflectionFailure(String operation, ReflectiveOperationException failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation && invocation.getCause() != null
            ? invocation.getCause() : failure;
        return new IllegalStateException("Could not " + operation, cause);
    }

    private record ReflectionApi(
            Object manager,
            Method resolve,
            Method resolvedPattern,
            Method resolvedCopies,
            Method hasAmbiguous,
            Method registerExpected) {
        @Nullable
        private static ReflectionApi load() {
            try {
                ClassLoader loader = ECOUselessDynamicOutputBridge.class.getClassLoader();
                Class<?> execution = Class.forName(
                    "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternExecution",
                    false, loader);
                Class<?> resolved = Class.forName(
                    "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternExecution$Resolved",
                    false, loader);
                Class<?> dynamicPattern = Class.forName(
                    "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern",
                    false, loader);
                Class<?> managerType = Class.forName(
                    "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicPatternCpuStateManager",
                    false, loader);
                Field instance = managerType.getField("INSTANCE");
                Object manager = instance.get(null);
                return new ReflectionApi(
                    manager,
                    execution.getMethod("resolve", IPatternDetails.class),
                    resolved.getMethod("pattern"),
                    resolved.getMethod("copies"),
                    managerType.getMethod("hasAmbiguousOutputRegistration", Object.class, dynamicPattern),
                    managerType.getMethod("registerExpectedOutputs", Object.class, UUID.class,
                        dynamicPattern, AEKey.class, long.class));
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
