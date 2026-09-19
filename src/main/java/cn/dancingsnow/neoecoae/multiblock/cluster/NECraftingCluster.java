package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class NECraftingCluster extends NECluster<NECraftingCluster> {
    private final cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache fastPathCache =
            new cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache();

    public cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache getFastPathCache() {
        return networkCluster == null ? fastPathCache : networkCluster.getFastPathCache();
    }

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
    @Nullable private NECraftingNetworkCluster networkCluster;

    public NECraftingCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    @Override
    public boolean shouldCasingHide(NEBlockEntity<NECraftingCluster, ?> blockEntity) {
        if (blockEntity instanceof ECOMachineCasingBlockEntity) {
            Vec3 casingPos = blockEntity.getBlockPos().getCenter();
            Vec3 controllerPos = controller.getBlockPos().getCenter();
            return casingPos.distanceToSqr(controllerPos) <= 3;
        }
        return false;
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

    public void setNetworkCluster(@Nullable NECraftingNetworkCluster networkCluster) {
        if (this.networkCluster == networkCluster) {
            return;
        }
        this.networkCluster = networkCluster;
        if (networkCluster == null && controller != null) {
            controller.onNetworkStateChanged();
        }
    }

    protected boolean hasLinkedNetworkPeers() {
        return networkCluster != null && networkCluster.getMemberCount() > 1;
    }

    @Override
    public int getConfiguredNetworkMultiplier() {
        if (!isNetworkMode()) {
            return 1;
        }
        return isHighEnergyNetworkMode() ? 8 : 2;
    }

    @Override
    public int getNetworkMultiplier() {
        return hasLinkedNetworkPeers() ? networkCluster.getCombinedSwitchMultiplier() : 1;
    }

    @Override
    public int getNetworkPowerMultiplier() {
        return 1;
    }

    static int resolveNetworkMultiplier(
            boolean networkMode, boolean highEnergyNetworkMode, int memberCount, boolean coolingAvailable) {
        if (!networkMode || memberCount <= 1) {
            return 1;
        }
        return memberCount * (highEnergyNetworkMode ? 8 : 2);
    }
}
