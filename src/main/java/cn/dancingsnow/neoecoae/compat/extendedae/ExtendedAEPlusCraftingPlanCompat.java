package cn.dancingsnow.neoecoae.compat.extendedae;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Optional protocol used by ExtendedAE Plus without making NeoECO depend on it.
 */
public final class ExtendedAEPlusCraftingPlanCompat {
    private static final String MANUAL_MISSING_METHOD = "eap$getManualMissingItems";
    private static final String DELEGATE_FIELD = "delegate";

    private ExtendedAEPlusCraftingPlanCompat() {
    }

    /**
     * Returns the original plan when EAP wrapped it for a forced craft.
     *
     * <p>ECO planner metadata is keyed by plan identity. Losing that identity makes forced
     * crafts fall back to a different input selection and can leave the CPU with an incorrect
     * execution ledger.</p>
     */
    public static ICraftingPlan unwrap(ICraftingPlan plan) {
        ICraftingPlan current = plan;
        Map<Object, Boolean> visited = Collections.synchronizedMap(new IdentityHashMap<>());

        while (current != null && visited.put(current, Boolean.TRUE) == null
                && findManualMissingMethod(current.getClass()) != null) {
            Object delegate = readDelegate(current);
            if (!(delegate instanceof ICraftingPlan delegatePlan)) {
                break;
            }
            current = delegatePlan;
        }

        return current;
    }

    /**
     * Reads EAP's forced-plan missing-item snapshot when that optional mod is present.
     */
    @Nullable
    public static KeyCounter getManualMissingItems(ICraftingPlan plan) {
        Method method = findManualMissingMethod(plan == null ? null : plan.getClass());
        if (method == null) {
            return null;
        }

        try {
            if (!method.canAccess(plan)) {
                method.trySetAccessible();
            }
            Object value = method.invoke(plan);
            if (!(value instanceof KeyCounter source)) {
                return null;
            }
            KeyCounter copy = new KeyCounter();
            for (var entry : source) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (key != null && amount > 0L) {
                    copy.add(key, amount);
                }
            }
            return copy;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static Method findManualMissingMethod(@Nullable Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(MANUAL_MISSING_METHOD);
            } catch (NoSuchMethodException ignored) {
                // Continue through the class hierarchy for optional wrappers.
            }
        }
        return null;
    }

    @Nullable
    private static Object readDelegate(ICraftingPlan plan) {
        for (Class<?> current = plan.getClass(); current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(DELEGATE_FIELD);
                if (!field.canAccess(plan)) {
                    field.trySetAccessible();
                }
                return field.get(plan);
            } catch (NoSuchFieldException ignored) {
                // Continue through the class hierarchy for optional wrappers.
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}
