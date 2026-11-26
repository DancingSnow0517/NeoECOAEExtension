package cn.dancingsnow.neoecoae.integration.ae2lt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.integration.common.processing.ProcessingReflection;
import cn.dancingsnow.neoecoae.integration.thunderbolt.ThunderboltApi;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchAdmission;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Forge AE2LT 2.1.4 routes both normal and wireless targets through OverloadedPatternProviderLogic.pushBatch. */
public final class ECOAe2LtNativeBatch {
    private static final String LOGIC = "com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic";
    private static final @Nullable Access ACCESS = Access.find();

    private ECOAe2LtNativeBatch() {}

    public static boolean isProvider(Object provider) {
        return logic(provider) != null;
    }

    @Nullable
    public static Session open(Object provider) {
        if (ACCESS == null) return null;
        Object logic = logic(provider);
        return ThunderboltApi.isInstance(ThunderboltApi.BATCH_PROVIDER, logic) ? new Session(logic, ACCESS) : null;
    }

    @Nullable
    private static Object logic(Object provider) {
        return logic(provider, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @Nullable
    private static Object logic(Object provider, Set<Object> visited) {
        if (provider == null || !visited.add(provider)) return null;
        if (ProcessingReflection.is(provider, LOGIC)) return provider;
        try {
            Method accessor = ProcessingReflection.publicNoArgMethod(provider.getClass(), "getLogic");
            return accessor == null ? null : logic(accessor.invoke(provider), visited);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    public static final class Session {
        private final Object logic;
        private final Access access;

        private Session(Object logic, Access access) {
            this.logic = logic;
            this.access = access;
        }

        public long capacity(IPatternDetails pattern) {
            try {
                return (long) access.capacity().invoke(logic, pattern);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return 0L;
            }
        }

        @Nullable
        public PowerBudget powerBudget(@Nullable IGrid grid, KeyCounter[] oneCopyInputs) {
            if (grid == null) return null;
            try {
                double perCraft = (double) access.transferPower().invoke(null, (Object) oneCopyInputs);
                double idleReserve = perCraft > 0.0 ? (double) access.idleReserve().invoke(null, grid) : 0.0;
                return new PowerBudget(perCraft, idleReserve);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }

        public ECOBatchAdmission submit(IPatternDetails pattern, KeyCounter[] oneCopyInputs, long offered) {
            try {
                long leftover = (long) access.push().invoke(logic, pattern, oneCopyInputs, offered);
                if (leftover < 0L || leftover > offered) return ECOBatchAdmission.indeterminate();
                return leftover == offered ? ECOBatchAdmission.rejected()
                        : ECOBatchAdmission.accepted(offered - leftover, false);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException("Cannot determine AE2LT batch ownership", failure);
            }
        }
    }

    public record PowerBudget(double perCraft, double idleReserve) {}

    private record Access(Method capacity, Method push, Method transferPower, Method idleReserve) {
        @Nullable
        private static Access find() {
            Class<?> contract = ThunderboltApi.BATCH_PROVIDER;
            if (contract == null) return null;
            try {
                Method capacity = contract.getMethod("getBatchCapacity", IPatternDetails.class);
                Method push = contract.getMethod("pushBatch", IPatternDetails.class, KeyCounter[].class, long.class);
                var power = Class.forName("com.moakiee.ae2lt.logic.energy.PowerCostUtil", false,
                        ECOAe2LtNativeBatch.class.getClassLoader());
                Method transferPower = power.getMethod("totalCost", KeyCounter[].class);
                Method idleReserve = power.getMethod("idleReserve", IGrid.class);
                return capacity.getReturnType() == long.class && push.getReturnType() == long.class
                        && transferPower.getReturnType() == double.class && idleReserve.getReturnType() == double.class
                        ? new Access(capacity, push, transferPower, idleReserve) : null;
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
