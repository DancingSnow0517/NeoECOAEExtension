package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IActionSource;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationParallelCore;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class NEComputationCluster extends NECluster<NEComputationCluster> {

    @Getter
    private List<ECOComputationDriveBlockEntity> upperDrives = new ArrayList<>();
    @Getter
    private List<ECOComputationDriveBlockEntity> lowerDrives = new ArrayList<>();
    @Getter
    private List<ECOComputationThreadingCoreBlockEntity> threadingCores = new ArrayList<>();
    @Getter
    @Nullable
    private ECOComputationSystemBlockEntity controller;
    @Getter
    @Nullable
    private IActionSource actionSource;
    private int accelerators = 0;
    @Getter
    private long availableStorage = 0;
    private CpuSelectionMode selectionMode = CpuSelectionMode.ANY;

    public NEComputationCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    @Override
    public void addBlockEntity(NEBlockEntity<NEComputationCluster, ?> blockEntity) {
        super.addBlockEntity(blockEntity);
        if (blockEntity instanceof ECOComputationDriveBlockEntity driveBlockEntity) {
            Level level = driveBlockEntity.getLevel();
            BlockState bottomBlock = level.getBlockState(driveBlockEntity.getBlockPos().relative(Direction.DOWN));
            if (bottomBlock.is(NEBlocks.COMPUTATION_TRANSMITTER)) {
                upperDrives.add(driveBlockEntity);
            } else {
                driveBlockEntity.setLowerDrive(true);
                lowerDrives.add(driveBlockEntity);
            }
        }
        if (blockEntity instanceof ECOComputationThreadingCoreBlockEntity threadingCore) {
            threadingCores.add(threadingCore);
        }
        if (blockEntity instanceof ECOComputationSystemBlockEntity system) {
            controller = system;
            actionSource = IActionSource.ofMachine(system);
        }
    }

    @Override
    public void updateFormed(boolean formed) {
        super.updateFormed(formed);
        if (formed) {
            this.accelerators = blockEntities.stream()
                .filter(it -> it instanceof ECOComputationParallelCoreBlockEntity)
                .mapToInt(it -> ((ECOComputationParallelCoreBlockEntity) it).getTier().getCPUAccelerators())
                .sum();
            this.availableStorage = collectStorage(upperDrives) + collectStorage(lowerDrives);
        } else {
            accelerators = 0;
            availableStorage = 0;
        }
    }

    private long collectStorage(List<ECOComputationDriveBlockEntity> driveBlockEntities) {
        long ret = 0;
        for (ECOComputationDriveBlockEntity driveBlockEntity : driveBlockEntities) {
            ItemStack itemStack = driveBlockEntity.getCellStack();
            if (itemStack != null && itemStack.isEmpty()) {
                if (itemStack.getItem() instanceof ECOComputationCellItem cellItem) {
                    ret += cellItem.getTier().getCPUTotalBytes();
                }
            }
        }
        return ret;
    }

    public int getCPUAccelerators() {
        return accelerators;
    }

    public boolean canBeAutoSelectedFor(IActionSource actionSource) {
        return switch (selectionMode) {
            case ANY -> true;
            case PLAYER_ONLY -> actionSource.player().isPresent();
            case MACHINE_ONLY -> actionSource.player().isEmpty();
        };
    }

    public @Nullable IGridNode getNode() {
        return controller != null ? controller.getActionableNode() : null;
    }

    public boolean isActive() {
        IGridNode node = this.getNode();
        return node != null && node.isActive();
    }

    public ICraftingSubmitResult submitJob(IGrid grid, ICraftingPlan job, IActionSource src, ICraftingRequester requestingMachine) {
        return null;
    }
}
