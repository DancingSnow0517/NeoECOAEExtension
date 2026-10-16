package cn.dancingsnow.neoecoae.crafting.adapter.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.CraftingLink;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.grid.NEMachineLookup;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.commons.lang3.mutable.MutableObject;

/** Non-ticking cluster traversal used by the AE2 service boundary. */
public final class ECOCraftingServiceClusters {
    private ECOCraftingServiceClusters() {}
    private static final Comparator<NEComputationCluster> NE_FAST_FIRST_COMPARATOR = Comparator.comparingInt(
                    NEComputationCluster::getPooledParallelism)
            .reversed()
            .thenComparingLong(NEComputationCluster::getAvailableStorage);

    public static String describe(Set<? extends ICraftingCPU> advertised, Set<NEComputationCluster> clusters,
            ICraftingPlan plan, IActionSource source) {
        StringBuilder details = new StringBuilder();

        details.append("advertisedCpus=").append(advertised.size());
        int index = 0;
        for (ICraftingCPU cpu : advertised) {
            long storage = cpu.getAvailableStorage();
            boolean busy = cpu.isBusy();
            details.append("\n  cpu[").append(index++).append("] type=")
                    .append(cpu.getClass().getName())
                    .append(", name=").append(cpu.getName() == null ? "<unnamed>" : cpu.getName().getString())
                    .append(", storage=").append(storage)
                    .append(", required=").append(plan.bytes())
                    .append(", fits=").append(storage >= plan.bytes())
                    .append(", busy=").append(busy)
                    .append(", coprocessors=").append(cpu.getCoProcessors())
                    .append(", selectionMode=").append(cpu.getSelectionMode())
                    .append(", nativeMenuSelectable=").append(!busy && storage >= plan.bytes())
                    .append(", rejection=").append(busy ? "busy" : storage < plan.bytes() ? "too_small"
                            : cpu.getSelectionMode() == appeng.api.config.CpuSelectionMode.MACHINE_ONLY
                            ? "player_auto_selection_excluded" : "none");
        }

        details.append("\nregisteredEcoClusters=").append(clusters.size());
        index = 0;
        for (NEComputationCluster cluster : clusters) {
            boolean representative = cluster.isNetworkRepresentative();
            boolean active = cluster.isActive();
            long storage = cluster.getAvailableStorage();
            boolean threadAvailable = cluster.getActiveCPUCount() < cluster.getMaxThreads();
            boolean allowed = cluster.canBeAutoSelectedFor(source);
            String rejection = !representative ? "not_network_representative"
                    : !active ? "offline"
                    : !threadAvailable ? "all_threads_busy"
                    : storage < plan.bytes() ? "too_small"
                    : !allowed ? "selection_mode_excluded"
                    : "none";
            details.append("\n  ecoCluster[").append(index++).append("] representative=")
                    .append(representative)
                    .append(", active=").append(active)
                    .append(", storage=").append(storage)
                    .append(", required=").append(plan.bytes())
                    .append(", activeThreads=").append(cluster.getActiveCPUCount())
                    .append(", maxThreads=").append(cluster.getMaxThreads())
                    .append(", parallelism=").append(cluster.getPooledParallelism())
                    .append(", selectionMode=").append(cluster.getSelectionMode())
                    .append(", sourceAllowed=").append(allowed)
                    .append(", rejection=").append(rejection);
        }
        return details.toString();
    }

    public static void refresh(IGrid grid, Set<NEComputationCluster> clusters, Consumer<CraftingLink> links) {
        clusters.clear();

        for (ECOComputationSystemBlockEntity blockEntity : NEMachineLookup.getMachines(grid, ECOComputationSystemBlockEntity.class)) {
            NEComputationCluster cluster = blockEntity.getCluster();
            if (cluster != null) {
                clusters.add(cluster);
                for (ECOCraftingCPU cpu : cluster.getActiveCPUs()) {
                    ICraftingLink maybeLink = cpu.getLogic().getLastLink();
                    if (maybeLink != null) {
                        links.accept((CraftingLink) maybeLink);
                    }
                }
            }
        }

    }

    public static NEComputationCluster findSuitable(Set<NEComputationCluster> clusters, ICraftingPlan job,
            IActionSource src, MutableObject<UnsuitableCpus> unsuitableCpusResult) {
        var validCpusClusters = new ArrayList<NEComputationCluster>(clusters.size());
        int offline = 0;
        int busy = 0;
        int tooSmall = 0;
        int excluded = 0;

        for (var cluster : clusters) {
            // A network group is a single pooled unit: only its representative member is considered
            // as a candidate, since submitJob() on any member routes to the same shared network cluster.
            if (!cluster.isNetworkRepresentative()) {
                continue;
            }
            if (!cluster.isActive()) {
                offline++;
                continue;
            }
            if (cluster.getActiveCPUCount() >= cluster.getMaxThreads()) {
                busy++;
                continue;
            }
            if (cluster.getAvailableStorage() < job.bytes()) {
                tooSmall++;
                continue;
            }
            if (!cluster.canBeAutoSelectedFor(src)) {
                excluded++;
                continue;
            }
            validCpusClusters.add(cluster);
        }

        if (validCpusClusters.isEmpty()) {
            if (offline > 0 || busy > 0 || tooSmall > 0 || excluded > 0) {
                var previous = unsuitableCpusResult.getValue();
                unsuitableCpusResult.setValue(previous == null
                        ? new UnsuitableCpus(offline, busy, tooSmall, excluded)
                        : new UnsuitableCpus(previous.offline() + offline, previous.busy() + busy,
                        previous.tooSmall() + tooSmall, previous.excluded() + excluded));
            }
            return null;
        }

        validCpusClusters.sort((a, b) -> {
            // Prioritize sorting by selected mode
            var firstPreferred = a.isPreferredFor(src);
            var secondPreferred = b.isPreferredFor(src);
            if (firstPreferred != secondPreferred) {
                // Sort such that preferred comes first, not preferred second
                return Boolean.compare(secondPreferred, firstPreferred);
            }

            return NE_FAST_FIRST_COMPARATOR.compare(a, b);
        });

        return validCpusClusters.getFirst();
    }

    public static void addCpus(Set<NEComputationCluster> clusters, Consumer<ICraftingCPU> cpus) {
        for (var cluster : clusters) {
            List<ECOCraftingCPU> ecoCpus = cluster.getActiveCPUs();
            for (var cpu : ecoCpus) {
                cpus.accept(cpu);
            }
            // Every member of a network group reports the same pooled numbers via its own fake CPU, so
            // only the representative advertises one to avoid inflating the reported free capacity.
            if (cluster.isNetworkRepresentative() && cluster.getActiveCPUCount() < cluster.getMaxThreads()) {
                cpus.accept(cluster.getFakeCPU());
            }
        }
    }

    public static boolean hasCpu(Set<NEComputationCluster> clusters, ICraftingCPU cpu) {
        for (var cluster : clusters) {
            for (var activeCpu : cluster.getActiveCPUs()) {
                if (activeCpu == cpu) {
                    return true;
                }
            }
            // getCpus() also advertises the placeholder CPU of a cluster with a free thread, so hasCpu
            // has to recognise it or that entry can never be selected as a crafting target.
            if (cluster.isNetworkRepresentative()
                    && cluster.getActiveCPUCount() < cluster.getMaxThreads()
                    && cluster.getFakeCPU() == cpu) {
                return true;
            }
        }
        return false;
    }
}
