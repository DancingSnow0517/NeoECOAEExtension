package cn.dancingsnow.neoecoae.compat.gtl;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import java.lang.reflect.InvocationTargetException;
import org.jetbrains.annotations.Nullable;

/** Optional bridge for GTLCore's transfinite CPU without a compile-time GTLCore dependency. */
public final class GTLTransfiniteCraftingCompat {
    private static final String CPU_CLASS =
            "org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteCraftingCPU";

    private GTLTransfiniteCraftingCompat() {}

    public static boolean isTransfiniteCpu(@Nullable ICraftingCPU target) {
        return target != null && target.getClass().getName().equals(CPU_CLASS);
    }

    public static boolean isUsableMissingCraftCpu(
            @Nullable ICraftingCPU target,
            @Nullable ICraftingPlan plan,
            IActionSource source,
            boolean automaticSelection) {
        if (!isTransfiniteCpu(target) || !isMissingCraftingEnabled()) return false;
        try {
            boolean capacityView =
                    (boolean) target.getClass().getMethod("isCapacityView").invoke(target);
            boolean active = (boolean) target.getClass().getMethod("isActive").invoke(target);
            Object host = target.getClass().getMethod("getHost").invoke(target);
            boolean selectable = (boolean) host.getClass()
                    .getMethod("canBeAutoSelectedFor", IActionSource.class)
                    .invoke(host, source);
            return capacityView
                    && active
                    && (!automaticSelection || selectable)
                    && (plan == null || target.getAvailableStorage() >= plan.bytes());
        } catch (ReflectiveOperationException failure) {
            return false;
        }
    }

    private static boolean isMissingCraftingEnabled() {
        try {
            Class<?> config = Class.forName("org.gtlcore.gtlcore.config.ConfigHolder");
            Object instance = config.getField("INSTANCE").get(null);
            return config.getField("enableAe2MissingCrafting").getBoolean(instance);
        } catch (ReflectiveOperationException | LinkageError failure) {
            return false;
        }
    }

    @Nullable public static ICraftingSubmitResult submit(
            IGrid grid, ICraftingPlan plan, ICraftingRequester requester, ICraftingCPU target, IActionSource source) {
        if (!isTransfiniteCpu(target)) return null;
        try {
            boolean capacityView =
                    (boolean) target.getClass().getMethod("isCapacityView").invoke(target);
            if (!capacityView) return CraftingSubmitResult.CPU_BUSY;
            Object host = target.getClass().getMethod("getHost").invoke(target);
            Object result = host.getClass()
                    .getMethod(
                            "submitJob",
                            IGrid.class,
                            ICraftingPlan.class,
                            IActionSource.class,
                            ICraftingRequester.class)
                    .invoke(host, grid, plan, source, requester);
            return result instanceof ICraftingSubmitResult submitResult ? submitResult : null;
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation && invocation.getCause() != null
                    ? invocation.getCause()
                    : failure;
            throw new IllegalStateException("Unable to submit to GTLCore transfinite crafting CPU", cause);
        }
    }
}
