package cn.dancingsnow.neoecoae.integration.ae2craftingtime;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.OptionalDouble;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.ModList;

public final class ECOCraftingTimeCompat {
    private static final String MOD_ID = "ae2craftingtime";
    private static final String API = "com.ctux.ae2craftingtime.api.ExternalCpuProfiling";

    private ECOCraftingTimeCompat() { }

    public static boolean available() {
        return Binding.api != null;
    }

    public static void start(ECOCraftingCPU cpu, IGrid grid, ICraftingPlan plan, IActionSource source) {
        var api = Binding.api;
        if (api != null && cpu.getLevel() instanceof ServerLevel level) {
            invoke(api.start, cpu, grid, plan, source, level.getGameTime());
        }
    }

    public static void expect(ECOCraftingCPU cpu, AEKey key, long amount) {
        var api = Binding.api;
        if (api != null) output(api.expect, cpu, key, amount);
    }

    public static void accept(ECOCraftingCPU cpu, AEKey key, long amount) {
        var api = Binding.api;
        if (api != null) output(api.accept, cpu, key, amount);
    }

    private static void output(Method method, ECOCraftingCPU cpu, AEKey key, long amount) {
        if (amount > 0L && cpu.getLevel() instanceof ServerLevel level) {
            var grid = cpu.getGrid();
            if (grid != null) invoke(method, cpu, grid, key, amount, level.getGameTime());
        }
    }

    public static void finish(ECOCraftingCPU cpu, boolean success) {
        var api = Binding.api;
        if (api != null && cpu.getLevel() instanceof ServerLevel level) {
            invoke(api.finish, cpu, success, level.getGameTime());
        }
    }

    public static void setSuspended(ECOCraftingCPU cpu, boolean suspended) {
        var api = Binding.api;
        if (api != null && cpu.getLevel() instanceof ServerLevel level) {
            invoke(api.suspend, cpu, suspended, level.getGameTime());
        }
    }

    public static OptionalDouble remainingMillis(ECOCraftingCPU cpu) {
        var api = Binding.api;
        if (api != null && cpu.getLevel() instanceof ServerLevel
                && invoke(api.estimate, cpu) instanceof OptionalDouble estimate) {
            return estimate;
        }
        return OptionalDouble.empty();
    }

    private static Object invoke(Method method, Object... arguments) {
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException failure) {
            var cause = failure.getCause();
            if (cause instanceof Error error && !(error instanceof LinkageError)) throw error;
            LogUtils.getLogger().warn("Disabling the optional AE2 Crafting Time integration", cause);
        } catch (ReflectiveOperationException | LinkageError failure) {
            LogUtils.getLogger().warn("Disabling the optional AE2 Crafting Time integration", failure);
        }
        Binding.api = null;
        return null;
    }

    private static final class Binding {
        private static volatile Methods api = resolve();

        private static Methods resolve() {
            if (!ModList.get().isLoaded(MOD_ID)) return null;
            try {
                var type = Class.forName(API, false, ECOCraftingTimeCompat.class.getClassLoader());
                return new Methods(
                    type.getMethod("startJob", ICraftingCPU.class, IGrid.class, ICraftingPlan.class,
                        IActionSource.class, long.class),
                    type.getMethod("expectOutput", ICraftingCPU.class, IGrid.class, AEKey.class, long.class, long.class),
                    type.getMethod("acceptOutput", ICraftingCPU.class, IGrid.class, AEKey.class, long.class, long.class),
                    type.getMethod("finishJob", ICraftingCPU.class, boolean.class, long.class),
                    type.getMethod("setSuspended", ICraftingCPU.class, boolean.class, long.class),
                    type.getMethod("remainingMillis", ICraftingCPU.class));
            } catch (ReflectiveOperationException | LinkageError absent) {
                return null;
            }
        }
    }

    private record Methods(Method start, Method expect, Method accept, Method finish, Method suspend, Method estimate) { }
}
