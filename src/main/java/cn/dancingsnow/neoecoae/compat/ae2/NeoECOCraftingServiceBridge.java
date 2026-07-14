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
            for (ECOCraftingCPU cpu : cluster.getActiveCPUs()) {
                var maybeLink = cpu.getLogic().getLastLink();
                if (maybeLink instanceof CraftingLink link) {
                    service.addLink(link);
                }
            }
        }
    }

    public static boolean tickComputationCpus(
            CraftingService service, IGrid grid, IEnergyService energyGrid, Set<AEKey> currentlyCrafting) {
        boolean changed = false;
        for (NEComputationCluster cluster : getComputationClusters(grid)) {
            for (ECOCraftingCPU cpu : cluster.getActiveCPUs()) {
                boolean wasBusy = cpu.isBusy();
                boolean hadRemainingItems = cpu.hasRemainingItems();

                cpu.getLogic().tickCraftingLogic(energyGrid, service);

                boolean isBusy = cpu.isBusy();
                boolean hasRemainingItems = cpu.hasRemainingItems();
                if (wasBusy != isBusy || hadRemainingItems != hasRemainingItems) {
                    changed = true;
                }

                cpu.getLogic().getAllWaitingFor(currentlyCrafting);
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
            cpus.addAll(cluster.getActiveCPUs());
            if (cluster.isActive() && cluster.hasFreeThread()) {
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
        return result.successful() ? result : null;
    }

    public static long insertIntoCpus(IGrid grid, AEKey what, long amount, Actionable type, long inserted) {
        for (NEComputationCluster cluster : getComputationClusters(grid)) {
            for (ECOCraftingCPU cpu : cluster.getActiveCPUs()) {
                inserted += cpu.getLogic().insert(what, amount - inserted, type);
            }
        }
        return inserted;
    }

    public static long getRequestedAmount(IGrid grid, AEKey what, long requested) {
        for (NEComputationCluster cluster : getComputationClusters(grid)) {
            for (ECOCraftingCPU cpu : cluster.getActiveCPUs()) {
                requested += cpu.getLogic().getWaitingFor(what);
            }
        }
        return requested;
    }

    public static boolean hasCpu(IGrid grid, ICraftingCPU cpu) {
        for (NEComputationCluster cluster : getComputationClusters(grid)) {
            if (cluster.hasFreeThread() && cluster.getFakeCPU() == cpu) {
                return true;
            }
            for (ECOCraftingCPU activeCpu : cluster.getActiveCPUs()) {
                if (activeCpu == cpu) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<NEComputationCluster> getComputationClusters(IGrid grid) {
        Set<NEComputationCluster> clusters = Collections.newSetFromMap(new IdentityHashMap<>());

        for (var node : grid.getNodes()) {
            Object owner = node.getOwner();
            if (!(owner instanceof IECOComputationHost host)) {
                continue;
            }
            ECOComputationSystemBlockEntity blockEntity = host.getComputationHost();
            NEComputationCluster cluster = blockEntity.getCluster();
            if (cluster != null && blockEntity.isFormed()) {
                clusters.add(cluster);
            }
        }
        return new ArrayList<>(clusters);
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
