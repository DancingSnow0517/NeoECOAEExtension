package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternDispatch;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import cn.dancingsnow.neoecoae.grid.crafting.execution.fastpath.verify.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class NECraftingCluster extends NECluster<NECraftingCluster> {
    @Getter
    private final List<ECOCraftingParallelCoreBlockEntity> parallelCores = new ArrayList<>();
    @Getter
    private final List<ECOCraftingWorkerBlockEntity> workers = new ArrayList<>();
    @Getter
    private final List<ECOCraftingPatternBusBlockEntity> patternBuses = new ArrayList<>();
    @Getter
    private ECOCraftingSystemBlockEntity controller = null;
    @Getter
    private ECOFluidInputHatchBlockEntity inputHatch = null;
    @Getter
    private ECOFluidOutputHatchBlockEntity outputHatch = null;
    @Getter
    @Nullable
    private NECraftingNetworkCluster networkCluster;

    private final ECOCraftingFastPathCache localFastPathCache = new ECOCraftingFastPathCache();
    private long localPatternDomainGeneration;
    private final ReferenceSet<ECOCraftingWorkerBlockEntity> workerMembership = new ReferenceOpenHashSet<>();
    private final Map<ECOCraftingWorkerBlockEntity, Integer> busyWorkerBatches = new IdentityHashMap<>();
    private long runningBatches;
    private long dispatchGeneration;
    private @Nullable ECOCraftingPatternDispatch localAvailableWorkers;

    public NECraftingCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    @Override
    protected BlockPos getCasingHideOrigin() {
        return controller == null ? null : controller.getBlockPos();
    }

    @Override
    public boolean isNetworkMode() {
        return controller != null && controller.hasNetworkSwitch();
    }

    @Override
    public int getNetworkMultiplier() {
        if (controller == null) {
            return 1;
        }
        if (controller.hasHighEnergyNetworkSwitch()) {
            return 8;
        }
        if (controller.hasNormalNetworkSwitch()) {
            return 2;
        }
        return 1;
    }

    public void setNetworkCluster(@Nullable NECraftingNetworkCluster networkCluster) {
        if (this.networkCluster == networkCluster) return;
        if (this.networkCluster != null) {
            this.networkCluster.invalidateDispatchTopology();
        }
        this.networkCluster = networkCluster;
        invalidateDispatchTopology();
        recountWorkerBatches();
    }

    /**
     * Fast-path knowledge for this host. While the host is grouped by a Network Switch, the group's shared
     * cache supersedes the local one, so a recipe verified on any member is immediately usable by every other
     * member. The cache lives and dies with the cluster, which is what makes multiblock rebuild, block removal
     * and chunk/world unload implicit invalidation points.
     */
    public ECOCraftingFastPathCache getFastPathCache() {
        NECraftingNetworkCluster network = this.networkCluster;
        return network != null ? network.getFastPathCache() : localFastPathCache;
    }

    /**
     * Workers this host may dispatch to: the whole Network Switch group when one is formed, otherwise its own
     * workers. Meant for topology rebuilds and displays, not dispatch.
     */
    public List<ECOCraftingWorkerBlockEntity> collectDispatchCandidateWorkers() {
        NECraftingNetworkCluster network = this.networkCluster;
        if (network != null) {
            return network.collectCandidateWorkers();
        }
        List<ECOCraftingWorkerBlockEntity> candidates = new ArrayList<>(workers.size());
        for (ECOCraftingWorkerBlockEntity worker : workers) {
            if (!ownsDispatchWorker(worker)) {
                continue;
            }
            candidates.add(worker);
        }
        return candidates;
    }

    /** Allocation-free reachability check used by busy reporting. */
    public boolean hasAvailableDispatchCandidate() {
        return getDispatchCandidates().best() != null;
    }

    /**
     * Identity of the pattern area visible to this host.  A host without a formed switch domain
     * owns its own area; only the manager-created network cluster makes several hosts share one.
     */
    public Object getPatternDomain() {
        return networkCluster != null ? networkCluster : this;
    }

    /** Buses visible from this host's crafting interface. */
    public List<ECOCraftingPatternBusBlockEntity> getPatternDomainBuses() {
        return networkCluster == null ? patternBuses : networkCluster.getPatternBuses();
    }

    public long getPatternDomainGeneration() {
        return networkCluster == null ? localPatternDomainGeneration : networkCluster.getPatternDomainGeneration();
    }

    public void onWorkerAvailabilityChanged(ECOCraftingWorkerBlockEntity worker) {
        if (isDestroyed() || !workerMembership.contains(worker)) return;
        int count = ownsDispatchWorker(worker) ? worker.getRunningBatchCount() : 0;
        int previous = busyWorkerBatches.getOrDefault(worker, 0);
        if (previous != count) {
            if (count == 0) busyWorkerBatches.remove(worker);
            else busyWorkerBatches.put(worker, count);
            runningBatches += count - previous;
            if (controller != null) controller.onWorkerBatchCountsChanged(this);
        }
        if (localAvailableWorkers != null) localAvailableWorkers.update(worker);
        if (networkCluster != null) networkCluster.onWorkerAvailabilityChanged(worker);
    }

    public void recountWorkerBatches() {
        busyWorkerBatches.clear();
        runningBatches = 0;
        for (ECOCraftingWorkerBlockEntity worker : workers) {
            if (ownsDispatchWorker(worker)) {
                int count = worker.recountRunningBatches();
                if (count > 0) busyWorkerBatches.put(worker, count);
                runningBatches += count;
            }
        }
        if (controller != null) controller.onWorkerBatchCountsChanged(this);
    }

    public int getRunningBatchCount() {
        return (int) Math.min(Integer.MAX_VALUE, runningBatches);
    }

    public int getActiveWorkerCount() {
        return busyWorkerBatches.size();
    }

    public ECOCraftingPatternDispatch getDispatchCandidates() {
        if (networkCluster != null) return networkCluster.getDispatchCandidates();
        if (localAvailableWorkers == null) {
            localAvailableWorkers = new ECOCraftingPatternDispatch(collectDispatchCandidateWorkers());
        }
        return localAvailableWorkers;
    }

    public void invalidateDispatchAvailability() {
        localAvailableWorkers = null;
        if (networkCluster != null) networkCluster.invalidateDispatchAvailability();
    }

    public void invalidateDispatchTopology() {
        dispatchGeneration++;
        invalidateDispatchAvailability();
        if (networkCluster != null) networkCluster.invalidateDispatchTopology();
    }

    public long getDispatchGeneration() {
        return dispatchGeneration;
    }

    private boolean ownsDispatchWorker(ECOCraftingWorkerBlockEntity worker) {
        return !isDestroyed() && controller != null && workerMembership.contains(worker)
            && worker.isLoadedForDispatch() && worker.getCluster() == this;
    }

    /** True when {@code worker} is still reachable for dispatch from this host. */
    public boolean isDispatchCandidate(ECOCraftingWorkerBlockEntity worker) {
        if (isDestroyed() || controller == null || controller.isRemoved() || controller.getCluster() != this) {
            return false;
        }
        NECraftingCluster owner = worker.getCluster();
        if (owner == null || !owner.ownsDispatchWorker(worker) || owner.controller.isRemoved()
                || owner.controller.getCluster() != owner) {
            return false;
        }
        if (owner == this) {
            return true;
        }
        NECraftingNetworkCluster network = this.networkCluster;
        return network != null && network == owner.getNetworkCluster()
            && controller.hasNetworkSwitch() && owner.controller.hasNetworkSwitch()
            && controller.getNetworkFrequency() == owner.controller.getNetworkFrequency()
            && controller.getMainNode().isOnline() && owner.controller.getMainNode().isOnline()
            && controller.getMainNode().getGrid() == owner.controller.getMainNode().getGrid();
    }

    @Override
    public void destroy() {
        invalidateDispatchTopology();
        NELogicalNetworkManager.detachBeforeDestroy(this);
        super.destroy();
        busyWorkerBatches.clear();
        runningBatches = 0;
    }

    @Override
    public void breakCluster() {
        if (isDestroyed()) return;
        for (ECOCraftingWorkerBlockEntity worker : workers) {
            worker.terminateRunningJobs();
        }
        destroy();
    }

    @Override
    public void addBlockEntity(NEBlockEntity<NECraftingCluster, ?> blockEntity) {
        super.addBlockEntity(blockEntity);
        invalidateDispatchAvailability();
        if (blockEntity instanceof ECOCraftingParallelCoreBlockEntity parallelCore) {
            parallelCores.add(parallelCore);
        }
        if (blockEntity instanceof ECOCraftingWorkerBlockEntity workerBlockEntity) {
            workers.add(workerBlockEntity);
            workerMembership.add(workerBlockEntity);
        }
        if (blockEntity instanceof ECOCraftingPatternBusBlockEntity patternBusBlockEntity) {
            patternBuses.add(patternBusBlockEntity);
            localPatternDomainGeneration++;
            if (networkCluster != null) {
                networkCluster.invalidatePatternDomain();
            }
        }
        if (blockEntity instanceof ECOCraftingSystemBlockEntity controller) {
            this.controller = controller;
        }
        if (blockEntity instanceof ECOFluidInputHatchBlockEntity inputHatchBlockEntity) {
            this.inputHatch = inputHatchBlockEntity;
        }
        if (blockEntity instanceof ECOFluidOutputHatchBlockEntity outputHatchBlockEntity) {
            this.outputHatch = outputHatchBlockEntity;
        }
    }
}
