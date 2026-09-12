package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import cn.dancingsnow.neoecoae.impl.crafting.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import lombok.Getter;
import appeng.hooks.ticking.TickHandler;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

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
    private final Set<ECOCraftingWorkerBlockEntity> localAvailableWorkers =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private long localAvailabilityTick = Long.MIN_VALUE;

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
        if (this.networkCluster != null) {
            this.networkCluster.invalidateDispatchAvailability();
        }
        this.networkCluster = networkCluster;
        localAvailabilityTick = Long.MIN_VALUE;
        localAvailableWorkers.clear();
        if (networkCluster != null) {
            networkCluster.invalidateDispatchAvailability();
        }
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
     * workers. Rebuilt per dispatch so no stale block-entity reference is ever retained.
     */
    public List<ECOCraftingWorkerBlockEntity> collectDispatchCandidateWorkers() {
        NECraftingNetworkCluster network = this.networkCluster;
        if (network != null) {
            return network.collectCandidateWorkers();
        }
        List<ECOCraftingWorkerBlockEntity> candidates = new ArrayList<>(workers.size());
        for (ECOCraftingWorkerBlockEntity worker : workers) {
            if (worker.isRemoved() || worker.getCluster() != this) {
                continue;
            }
            candidates.add(worker);
        }
        return candidates;
    }

    /** Allocation-free reachability check used by busy reporting. */
    public boolean hasAvailableDispatchCandidate() {
        NECraftingNetworkCluster network = this.networkCluster;
        if (network != null) {
            return network.hasAvailableCandidateWorker();
        }
        refreshLocalDispatchAvailability();
        return !localAvailableWorkers.isEmpty();
    }

    /** Keeps the tick-local busy snapshot exact when a worker starts or stops owning work. */
    public void onWorkerAvailabilityChanged(ECOCraftingWorkerBlockEntity worker) {
        NECraftingNetworkCluster network = this.networkCluster;
        if (network != null) {
            network.onWorkerAvailabilityChanged(worker);
            return;
        }
        long currentTick = TickHandler.instance().getCurrentTick();
        if (localAvailabilityTick != currentTick) {
            return;
        }
        if (isLocalDispatchCandidate(worker) && worker.getAvailableThreadSlots() > 0) {
            localAvailableWorkers.add(worker);
        } else {
            localAvailableWorkers.remove(worker);
        }
    }

    private void refreshLocalDispatchAvailability() {
        long currentTick = TickHandler.instance().getCurrentTick();
        if (localAvailabilityTick == currentTick) {
            return;
        }
        localAvailableWorkers.clear();
        for (ECOCraftingWorkerBlockEntity worker : workers) {
            if (isLocalDispatchCandidate(worker) && worker.getAvailableThreadSlots() > 0) {
                localAvailableWorkers.add(worker);
            }
        }
        localAvailabilityTick = currentTick;
    }

    private boolean isLocalDispatchCandidate(ECOCraftingWorkerBlockEntity worker) {
        return !isDestroyed() && controller != null
            && !worker.isRemoved() && worker.getCluster() == this;
    }

    /** True when {@code worker} is still reachable for dispatch from this host. */
    public boolean isDispatchCandidate(ECOCraftingWorkerBlockEntity worker) {
        if (worker.isRemoved()) {
            return false;
        }
        NECraftingCluster owner = worker.getCluster();
        if (owner == null || owner.isDestroyed() || !owner.getWorkers().contains(worker)) {
            return false;
        }
        if (owner == this) {
            return true;
        }
        NECraftingNetworkCluster network = this.networkCluster;
        return network != null && network == owner.getNetworkCluster();
    }

    @Override
    public void destroy() {
        NELogicalNetworkManager.detachBeforeDestroy(this);
        super.destroy();
    }

    @Override
    public void breakCluster() {
        if (isDestroyed()) return;
        for (ECOCraftingWorkerBlockEntity worker : List.copyOf(workers)) {
            worker.terminateRunningJobs();
        }
        destroy();
    }

    @Override
    public void addBlockEntity(NEBlockEntity<NECraftingCluster, ?> blockEntity) {
        super.addBlockEntity(blockEntity);
        localAvailabilityTick = Long.MIN_VALUE;
        localAvailableWorkers.clear();
        if (networkCluster != null) {
            networkCluster.invalidateDispatchAvailability();
        }
        if (blockEntity instanceof ECOCraftingParallelCoreBlockEntity parallelCore) {
            parallelCores.add(parallelCore);
        }
        if (blockEntity instanceof ECOCraftingWorkerBlockEntity workerBlockEntity) {
            workers.add(workerBlockEntity);
        }
        if (blockEntity instanceof ECOCraftingPatternBusBlockEntity patternBusBlockEntity) {
            patternBuses.add(patternBusBlockEntity);
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
