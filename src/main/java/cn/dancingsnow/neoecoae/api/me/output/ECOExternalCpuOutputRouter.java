package cn.dancingsnow.neoecoae.api.me.output;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtCraftingAdapter;
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
        return ECOAe2LtCraftingAdapter.insertCpuOutput(cpu, jobId, key, amount, mode);
    }
}
