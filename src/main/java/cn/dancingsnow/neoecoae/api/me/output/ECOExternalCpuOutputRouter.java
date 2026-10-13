package cn.dancingsnow.neoecoae.api.me.output;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.compat.ae2lt.ECOAe2LtCraftingAdapter;
import java.util.HashSet;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

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

    /**
     * Collects the non-null clusters of {@code hosts} into a {@link HashSet}, visits each distinct cluster once and
     * inserts into the first CPU that {@code owns} the job. The owner's result is returned even when it is 0; empty
     * means no CPU owned the job.
     */
    public static <H, C, P> OptionalLong insertIntoOwningCpu(
            Iterable<? extends H> hosts,
            Function<? super H, ? extends C> clusterOf,
            Function<? super C, ? extends Iterable<? extends P>> cpusOf,
            Predicate<? super P> owns,
            ToLongFunction<? super P> insert) {
        Set<C> clusters = new HashSet<>();
        for (H host : hosts) {
            C cluster = clusterOf.apply(host);
            if (cluster != null) {
                clusters.add(cluster);
            }
        }
        for (C cluster : clusters) {
            for (P cpu : cpusOf.apply(cluster)) {
                if (owns.test(cpu)) {
                    return OptionalLong.of(insert.applyAsLong(cpu));
                }
            }
        }
        return OptionalLong.empty();
    }
}
