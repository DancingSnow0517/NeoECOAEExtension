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
        if (jobId == null || key == null || amount <= 0L) return 0L;
        if (cpu instanceof CraftingCPUCluster cluster) {
            var logic = cluster.craftingLogic;
            var link = logic.getLastLink();
            if (link == null || !jobId.equals(link.getCraftingID())) return 0;
            long inserted = logic.insert(key, amount, mode);
            if (inserted < 0L || inserted > amount) {
                throw new IllegalStateException("Invalid native CPU insertion amount: " + inserted + " for " + amount);
            }
            if (mode == Actionable.MODULATE && inserted < amount) {
                // Directed surplus belongs to this CPU too. AE2 may also retire final output
                // even when the requester rejects it, so retain every physical remainder locally.
                logic.getInventory().insert(key, amount - inserted, mode);
                cluster.markDirty();
            }
            return amount;
        }
        return ECOAe2LtCraftingAdapter.insertCpuOutput(cpu, jobId, key, amount, mode);
    }
}
