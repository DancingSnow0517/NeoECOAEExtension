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
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class NECraftingCluster extends NECluster<NECraftingCluster> {
    @Getter
    private final ECOCraftingFastPathCache fastPathCache = new ECOCraftingFastPathCache();

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

    @Nullable private NECraftingNetworkCluster networkCluster;

    public NECraftingCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    @Override
    public boolean shouldCasingHide(NEBlockEntity<NECraftingCluster, ?> blockEntity) {
        if (blockEntity instanceof ECOMachineCasingBlockEntity && controller != null) {
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
        if (controller != null) {
            controller.markStructureStatsDirty();
        }
    }

    @Override
    public void updateFormed(boolean formed) {
        super.updateFormed(formed);
        if (controller != null) {
            controller.markStructureStatsDirty();
        }
    }

    public void setNetworkCluster(@Nullable NECraftingNetworkCluster networkCluster) {
        if (this.networkCluster == networkCluster) {
            return;
        }
        this.networkCluster = networkCluster;
        if (controller != null) {
            controller.markStructureStatsDirty();
        }
    }

    @Nullable public NECraftingNetworkCluster getNetworkCluster() {
        return networkCluster;
    }

    @Override
    public int getConfiguredNetworkMultiplier() {
        if (networkCluster == null || networkCluster.getMemberCount() <= 1) {
            return 1;
        }
        return isHighEnergyNetworkMode() ? 8 : isNetworkMode() ? 2 : 1;
    }

    @Override
    public int getNetworkMultiplier() {
        int configuredMultiplier = getConfiguredNetworkMultiplier();
        if (configuredMultiplier <= 1 || networkCluster == null) {
            return 1;
        }
        return networkCluster.hasCoolingForNetworkMultiplier(configuredMultiplier) ? configuredMultiplier : 1;
    }

    /** Network batch capacity scales linearly; energy and coolant have a higher operating cost. */
    public int getNetworkPowerMultiplier() {
        if (networkCluster == null || networkCluster.getMemberCount() <= 1) {
            return 1;
        }
        return isHighEnergyNetworkMode() ? 16 : isNetworkMode() ? 4 : 1;
    }
}
