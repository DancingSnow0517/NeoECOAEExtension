package cn.dancingsnow.neoecoae.multiblock.cluster;

import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

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
    private ECOFluidInputHatchBlockEntity inputHatch = null;
    private ECOFluidOutputHatchBlockEntity outputHatch = null;

    @Getter
    private boolean destroyed = false;

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
}
