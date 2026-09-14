package cn.dancingsnow.neoecoae.api.me.output;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.lang.reflect.Method;
import java.util.UUID;

/** Job-directed output routing for AE2/Omni CPUs and the optional AE2LT time-wheel engine. */
public final class ECOExternalCpuOutputRouter {
    private ECOExternalCpuOutputRouter() {}

    public static long insert(ICraftingCPU cpu, UUID jobId, AEKey key, long amount, Actionable mode) {
        if (cpu instanceof CraftingCPUCluster cluster) {
            var logic = cluster.craftingLogic;
            var link = logic.getLastLink();
            if (link == null || !jobId.equals(link.getCraftingID())) return 0;
            long offered = Math.min(amount, logic.getWaitingFor(key));
            if (offered <= 0) return 0;
            long inserted = logic.insert(key, offered, mode);
            if (mode == Actionable.MODULATE && inserted < offered) {
                // AE2 retires final output even when the requester rejects it. Keep that remainder locally.
                logic.getInventory().insert(key, offered - inserted, mode);
                cluster.markDirty();
                return offered;
            }
            return inserted;
        }
        var api = TIME_WHEEL;
        if (api == null || !api.logic.getDeclaringClass().isInstance(cpu)) return 0;
        try {
            Object logic = api.logic.invoke(cpu);
            var link = (ICraftingLink) api.link.invoke(logic);
            if (link == null || !jobId.equals(link.getCraftingID())) return 0;
            long before = (long) api.waiting.invoke(logic, key);
            long offered = Math.min(amount, before);
            if (offered <= 0) return 0;
            long inserted = (long) api.insert.invoke(logic, key, offered, mode);
            if (mode == Actionable.MODULATE && inserted < offered
                    && before - (long) api.waiting.invoke(logic, key) >= offered) {
                ((ListCraftingInventory) api.inventory.invoke(logic)).insert(key, offered - inserted, mode);
                return offered;
            }
            return inserted;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("AE2LT output routing failed", failure);
        }
    }

    private static final TimeWheelApi TIME_WHEEL = TimeWheelApi.load();

    private record TimeWheelApi(Method logic, Method link, Method waiting, Method insert, Method inventory) {
        private static TimeWheelApi load() {
            for (String name : new String[]{
                    "com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU",
                    "com.moakiee.thunderbolt.ae2.timewheel.TimeWheelCraftingCPU"}) {
                try {
                    var cpu = Class.forName(name,
                        false, ECOExternalCpuOutputRouter.class.getClassLoader());
                    var getter = cpu.getMethod("getCraftingLogic");
                    var logic = getter.getReturnType();
                    return new TimeWheelApi(getter, logic.getMethod("getLastLink"),
                        logic.getMethod("getWaitingFor", AEKey.class),
                        logic.getMethod("insert", AEKey.class, long.class, Actionable.class),
                        logic.getMethod("getInventory"));
                } catch (ReflectiveOperationException | LinkageError unavailable) {
                    // Try the other supported package layout.
                }
            }
            return null;
        }
    }
}
