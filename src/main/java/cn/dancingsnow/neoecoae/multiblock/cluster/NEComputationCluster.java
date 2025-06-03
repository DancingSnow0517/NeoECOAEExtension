package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NEComputationCluster extends NECluster<NEComputationCluster> {

    @Getter
    private final List<ECOComputationDriveBlockEntity> upperDrives = new ArrayList<>();
    @Getter
    private final List<ECOComputationDriveBlockEntity> lowerDrives = new ArrayList<>();
    @Getter
    private final List<ECOComputationThreadingCoreBlockEntity> threadingCores = new ArrayList<>();
    @Getter
    @Nullable
    private ECOComputationSystemBlockEntity controller;
    @Getter
    @Nullable
    private IActionSource actionSource;
    private int accelerators = 0;
    @Getter
    private long availableStorage = 0;
    @Getter
    private CpuSelectionMode selectionMode = CpuSelectionMode.ANY;

    private final HashMap<ICraftingPlan, ECOCraftingCPU> activeCpus = new HashMap();
    private ECOCraftingCPU fakeCpu;

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
            recalculateRemainingStorage();
            this.fakeCpu = new ECOCraftingCPU(this, availableStorage);

            for (ECOComputationThreadingCoreBlockEntity threadingCore : threadingCores) {
                for (ECOCraftingCPU cpu : threadingCore.getCpus()) {
                    if (cpu != null) {
                        activeCpus.put(cpu.getPlan(), cpu);
                    }
                }
            }
        } else {
            accelerators = 0;
            availableStorage = 0;
        }
    }

    private long collectStorage(List<ECOComputationDriveBlockEntity> driveBlockEntities) {
        long ret = 0;
        for (ECOComputationDriveBlockEntity driveBlockEntity : driveBlockEntities) {
            ItemStack itemStack = driveBlockEntity.getCellStack();
            if (itemStack != null && !itemStack.isEmpty()) {
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

    public ICraftingSubmitResult submitJob(
        IGrid grid,
        ICraftingPlan job,
        IActionSource src,
        ICraftingRequester requestingMachine
    ) {
        if (!this.isActive()) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (this.availableStorage < job.bytes()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }
        ECOCraftingCPU cpu = null;
        ICraftingSubmitResult result = null;
        boolean submitted = false;
        for (ECOComputationThreadingCoreBlockEntity threadingCore : threadingCores) {
            cpu = threadingCore.spawn(job);
            result = cpu.getLogic().trySubmitJob(grid, job, src, requestingMachine);
            if (result.successful()) {
                submitted = true;
                break;
            }
            threadingCore.deactivate(cpu);
        }
        if (!submitted) {
            return CraftingSubmitResult.NO_CPU_FOUND;
        }
        this.activeCpus.put(job, cpu);
        this.recalculateRemainingStorage();
        this.updateGridForChangedCpu(this);
        return result;
    }

    public void recalculateRemainingStorage() {
        long totalStorage = collectStorage(upperDrives) + collectStorage(lowerDrives);

        long usedStorage = 0L;

        for (ICraftingPlan plan : this.activeCpus.keySet()) {
            usedStorage += plan.bytes();
        }

        this.availableStorage = totalStorage - usedStorage;
    }

    public List<ECOCraftingCPU> getActiveCPUs() {
        List<ECOCraftingCPU> cpus = new ArrayList<>();
        List<ICraftingPlan> killList = new ArrayList<>();
        for (Map.Entry<ICraftingPlan, ECOCraftingCPU> entry : activeCpus.entrySet()) {
            ECOCraftingCPU cpu = entry.getValue();
            if (cpu.getLogic().hasJob() || cpu.getLogic().isMarkedForDeletion()) {
                cpus.add(cpu);
            } else {
                killList.add(entry.getKey());
            }
        }
        for (ICraftingPlan iCraftingPlan : killList) {
            killCpu(iCraftingPlan, true);
        }
        return cpus;
    }

    public ECOCraftingCPU getFakeCPU() {
        if (this.fakeCpu == null || this.fakeCpu.getAvailableStorage() != this.availableStorage) {
            this.fakeCpu = new ECOCraftingCPU(this, this.availableStorage);
        }
        return fakeCpu;
    }

    public void deactivate(ICraftingPlan plan) {
        ECOCraftingCPU cpu = this.activeCpus.remove(plan);
        this.recalculateRemainingStorage();
        this.updateGridForChangedCpu(this);
        if (cpu != null) {
            cpu.getOwner().deactivate(cpu);
        }
    }

    public void cancelJobs() {
        for (ICraftingPlan plan : this.activeCpus.keySet()) {
            this.killCpu(plan, false);
        }

    }

    public void cancelJob(ICraftingPlan plan) {
        if (this.activeCpus.get(plan) != null) {
            this.killCpu(plan, true);
        }
    }

    private void killCpu(ICraftingPlan plan, boolean update) {
        ECOCraftingCPU cpu = activeCpus.get(plan);
        cpu.getLogic().cancel();
        cpu.getLogic().markForDeletion();
        cpu.getOwner().deactivate(cpu);
        this.recalculateRemainingStorage();
        if (update) {
            updateGridForChangedCpu(this);
        }
    }

    private void updateGridForChangedCpu(NEComputationCluster cluster) {
        boolean posted = false;

        for (var r : this.blockEntities) {
            IGridNode n = r.getActionableNode();
            if (n != null && !posted) {
                n.getGrid().postEvent(new GridCraftingCpuChange(n));
                posted = true;
            }

            r.updateCluster(cluster);
        }

    }
}
