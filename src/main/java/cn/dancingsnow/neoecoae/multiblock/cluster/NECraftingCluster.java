package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingParallelCore;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingPatternBus;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingSystem;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingWorker;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOFluidInputHatchBlock;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOFluidOutputHatchBlock;
import cn.dancingsnow.neoecoae.blocks.entity.MachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class NECraftingCluster extends NECluster<NECraftingCluster> {
    private final List<ECOCraftingParallelCoreBlockEntity> parallelCores = new ArrayList<>();
    private final List<ECOCraftingWorkerBlockEntity> workers = new ArrayList<>();
    private final List<ECOCraftingPatternBusBlockEntity> patternBuses = new ArrayList<>();
    private ECOCraftingSystemBlockEntity controller = null;
    private ECOFluidInputHatchBlockEntity inputHatch = null;
    private ECOFluidOutputHatchBlockEntity outputHatch = null;

    public NECraftingCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return false;
    }

    public boolean isBusy() {
        return false;
    }

    @Override
    public boolean shouldCasingHide(NEBlockEntity<NECraftingCluster, ?> blockEntity) {
        if (blockEntity instanceof MachineCasingBlockEntity) {
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

    @Override
    public void updateStatus(boolean updateGrid) {

    }

    @Override
    public void destroy() {
    }

    @Override
    public boolean isDestroyed() {
        return false;
    }
}
