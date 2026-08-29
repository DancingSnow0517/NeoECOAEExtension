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
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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
        this.networkCluster = networkCluster;
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
        for (ECOCraftingWorkerBlockEntity worker : workers) {
            if (worker.isRemoved() || worker.getCluster() != this) {
                continue;
            }
            if (worker.getAvailableThreadSlots() > 0) {
                return true;
            }
        }
        return false;
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
    public void addBlockEntity(NEBlockEntity<NECraftingCluster, ?> blockEntity) {
        super.addBlockEntity(blockEntity);
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
