package cn.dancingsnow.neoecoae.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.IECOComputationHost;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.integration.advancedae.AdvancedAECraftingCompat;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public final class NeoECOCraftingServiceBridge {
    private static final Comparator<NEComputationCluster> FAST_FIRST = Comparator.comparingInt(
                    NEComputationCluster::getCPUAccelerators)
            .reversed()
            .thenComparingLong(NEComputationCluster::getAvailableStorage);

    private NeoECOCraftingServiceBridge() {}

    public static boolean isComputationClusterNode(IGridNode gridNode) {
        return gridNode.getOwner() instanceof NEBlockEntity<?, ?> blockEntity
                && blockEntity.getCluster() instanceof NEComputationCluster;
    }

    public static void addRestoredLinks(CraftingService service, IGrid grid) {
        for (NEComputationCluster cluster : getComputationClusters(grid)) {
            for (ECOCraftingCPU cpu : cluster.getActiveCPUs(grid)) {
                var maybeLink = cpu.getLogic().getLastLink();
                if (maybeLink instanceof CraftingLink link) {
                    service.addLink(link);
                }
            }
        }
    }

    public static boolean tickComputationCpus(
            CraftingService service, IGrid grid, IEnergyService energyGrid, Set<AEKey> computationCrafting) {
        boolean changed = false;
        for (NEComputationCluster cluster : getComputationClusters(grid)) {
            for (ECOCraftingCPU cpu : cluster.getActiveCPUs(grid)) {
                boolean wasBusy = cpu.isBusy();
                boolean hadRemainingItems = cpu.hasRemainingItems();

                cpu.getLogic().tickCraftingLogic(energyGrid, service);

                boolean isBusy = cpu.isBusy();
                boolean hasRemainingItems = cpu.hasRemainingItems();
                if (wasBusy != isBusy || hadRemainingItems != hasRemainingItems) {
                    changed = true;
                }

                cpu.getLogic().getAllWaitingFor(computationCrafting);
            }
        }
        return changed;
    }

    public static ImmutableSet<ICraftingCPU> getCpus(IGrid grid, @Nullable ImmutableSet<ICraftingCPU> vanillaCpus) {
        ImmutableSet.Builder<ICraftingCPU> cpus = ImmutableSet.builder();
        if (vanillaCpus != null) {
            cpus.addAll(vanillaCpus);
        }

        for (NEComputationCluster cluster : getComputationClusters(grid)) {
            cpus.addAll(cluster.getActiveCPUs(grid));
            if (cluster.isActive() && cluster.hasFreeThread() && isClusterOnGrid(cluster, grid)) {
                cpus.add(cluster.getFakeCPU());
            }
        }
        AdvancedAECraftingCompat.addCpus(grid, cpus);
        return cpus.build();
    }

    @Nullable public static ICraftingSubmitResult submitJob(
            IGrid grid,
            ICraftingPlan job,
            ICraftingRequester requestingMachine,
            @Nullable ICraftingCPU target,
            IActionSource src) {
        // This bridge is called from a HEAD injection, before AE2's native
        // submitJob implementation rejects incomplete (missing-material) plans.
        if (job.simulation()) {
            return CraftingSubmitResult.INCOMPLETE_PLAN;
        }

        if (target instanceof ECOCraftingCPU ecoCpu) {
            return ecoCpu.isAllocationProxy()
                    ? ecoCpu.getCluster().submitJob(grid, job, src, requestingMachine)
                    : CraftingSubmitResult.CPU_BUSY;
        }

        if (target != null) {
            return null;
        }

        NEComputationCluster cluster = findSuitableComputationCluster(grid, job, src);
        if (cluster == null) {
            return null;
        }

        ICraftingSubmitResult result = cluster.submitJob(grid, job, src, requestingMachine);
        // Return unsuccessful results too. The ECO CPU may have a precise
        // reason such as a missing ingredient; dropping it makes CraftingService
        // fall through to vanilla CPU selection and report NO_CPU_FOUND.
        return result;
    }

    public static long insertIntoCpus(IGrid grid, AEKey what, long amount, Actionable type, long inserted) {
        if (inserted >= amount) {
            return inserted;
        }

        for (NEComputationCluster cluster : getComputationClusters(grid)) {
            for (ECOCraftingCPU cpu : cluster.getActiveCPUs(grid)) {
                inserted += cpu.getLogic().insert(what, amount - inserted, type);
                if (inserted >= amount) {
                    return inserted;
                }
            }
        }
        return inserted;
    }

    public static long getRequestedAmount(IGrid grid, AEKey what, long requested) {
        for (NEComputationCluster cluster : getComputationClusters(grid)) {
            for (ECOCraftingCPU cpu : cluster.getActiveCPUs(grid)) {
                requested += cpu.getLogic().getWaitingFor(what);
            }
        }
        return requested;
    }

    public static boolean hasCpu(IGrid grid, ICraftingCPU cpu) {
        for (NEComputationCluster cluster : getComputationClusters(grid)) {
            if (cluster.hasFreeThread() && isClusterOnGrid(cluster, grid) && cluster.getFakeCPU() == cpu) {
                return true;
            }
            for (ECOCraftingCPU activeCpu : cluster.getActiveCPUs(grid)) {
                if (activeCpu == cpu) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isClusterOnGrid(NEComputationCluster cluster, IGrid grid) {
        NEComputationNetworkCluster network = cluster.getNetworkCluster();
        if (network != null) {
            return network.hasActiveHostOnGrid(grid);
        }
        IGridNode node = cluster.getNode();
        if (node == null) {
            return false;
        }
        try {
            return node.isOnline() && node.getGrid() == grid;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static List<NEComputationCluster> getComputationClusters(IGrid grid) {
        Set<NEComputationCluster> discovered = Collections.newSetFromMap(new IdentityHashMap<>());

        // Query the concrete controller owner first. AE2 1.20.1 indexes grid
        // machines by their concrete owner class, so asking for the parent
        // service interface alone can return an empty set even though the
        // controller is present on the grid. This is also the lookup used by
        // the 1.21.1 integration and keeps CPUs visible after a late grid join.
        for (ECOComputationSystemBlockEntity blockEntity : grid.getMachines(ECOComputationSystemBlockEntity.class)) {
            addComputationCluster(grid, discovered, blockEntity);
        }

        // Keep the interface lookup as a compatibility fallback for grids
        // populated by older AE2/compatibility providers.
        for (IECOComputationHost host : grid.getMachines(IECOComputationHost.class)) {
            if (host != null) {
                addComputationCluster(grid, discovered, host.getComputationHost());
            }
        }

        // A linked host exposes the aggregate CPU list through every physical
        // member. Keep one representative per logical network so AE2 does not
        // tick the same CPU multiple times or display duplicate allocation
        // proxies. Standalone hosts remain one entry each.
        Set<NEComputationNetworkCluster> seenNetworks = Collections.newSetFromMap(new IdentityHashMap<>());
        List<NEComputationCluster> result = new ArrayList<>(discovered.size());
        for (NEComputationCluster cluster : discovered) {
            NEComputationNetworkCluster network = cluster.getNetworkCluster();
            if (network == null || seenNetworks.add(network)) {
                result.add(cluster);
            }
        }
        return result;
    }

    private static void addComputationCluster(
            IGrid grid, Set<NEComputationCluster> clusters, @Nullable ECOComputationSystemBlockEntity blockEntity) {
        if (blockEntity == null || !blockEntity.isFormed()) {
            return;
        }
        try {
            if (!blockEntity.getMainNode().isOnline()
                    || blockEntity.getMainNode().getGrid() != grid) {
                return;
            }
        } catch (RuntimeException ignored) {
            return;
        }
        NEComputationCluster cluster = blockEntity.getCluster();
        if (cluster != null && !cluster.isDestroyed()) {
            clusters.add(cluster);
        }
    }

    @Nullable private static NEComputationCluster findSuitableComputationCluster(
            IGrid grid, ICraftingPlan job, IActionSource src) {
        List<NEComputationCluster> candidates = new ArrayList<>();
        for (NEComputationCluster cluster : getComputationClusters(grid)) {
            if (cluster.isActive()
                    && cluster.hasFreeThread()
                    && cluster.getAvailableStorage() >= job.bytes()
                    && cluster.canBeAutoSelectedFor(src)) {
                candidates.add(cluster);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(FAST_FIRST);
        return candidates.get(0);
    }
}
