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
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class NEComputationCluster extends NECluster<NEComputationCluster> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Getter
    private final List<ECOComputationDriveBlockEntity> upperDrives = new ArrayList<>();

    @Getter
    private final List<ECOComputationDriveBlockEntity> lowerDrives = new ArrayList<>();

    @Getter
    private final List<ECOComputationThreadingCoreBlockEntity> threadingCores = new ArrayList<>();

    @Getter
    private final List<ECOComputationParallelCoreBlockEntity> parallelCores = new ArrayList<>();

    @Getter
    @Nullable private ECOComputationSystemBlockEntity controller;

    @Getter
    @Nullable private IActionSource actionSource;

    private int accelerators = 0;

    @Getter
    private int maxThreads = 0;

    @Getter
    private long availableStorage = 0;

    @Getter
    private long totalStorageBytes = 0;

    private long activeJobBytes = 0;

    @Getter
    private int activeCpuCount = 0;

    @Getter
    private CpuSelectionMode selectionMode = CpuSelectionMode.ANY;

    private final Map<ECOCraftingCPU, ICraftingPlan> activeCpus = new IdentityHashMap<>();
    private ECOCraftingCPU fakeCpu;

    public NEComputationCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    @Override
    public void onStructureBroken() {
        if (controller == null || controller.getLevel() == null || controller.getLevel().isClientSide) {
            return;
        }
        cancelActiveCpusForStructureBreak();
    }

    @Override
    public void addBlockEntity(NEBlockEntity<NEComputationCluster, ?> blockEntity) {
        super.addBlockEntity(blockEntity);
        if (blockEntity instanceof ECOComputationDriveBlockEntity driveBlockEntity) {
            Level level = driveBlockEntity.getLevel();
            BlockState bottomBlock =
                    level.getBlockState(driveBlockEntity.getBlockPos().relative(Direction.DOWN));
            if (bottomBlock.is(NEBlocks.COMPUTATION_TRANSMITTER.get())) {
                upperDrives.add(driveBlockEntity);
            } else {
                driveBlockEntity.setLowerDrive(true);
                driveBlockEntity.setChanged();
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
        if (blockEntity instanceof ECOComputationParallelCoreBlockEntity parallelCore) {
            parallelCores.add(parallelCore);
        }
    }

    public void pickup(ICraftingPlan plan, ECOCraftingCPU cpu) {
        if (this.activeCpus.put(cpu, plan) == null) {
            this.activeJobBytes += plan.bytes();
            this.activeCpuCount = this.activeCpus.size();
        }
    }

    public void restoreActiveCpusFromThreadingCores() {
        int restored = 0;
        long restoredBytes = 0L;
        for (ECOComputationThreadingCoreBlockEntity core : threadingCores) {
            for (ECOCraftingCPU cpu : core.getCpus()) {
                if (cpu == null || cpu.getPlan() == null || !cpu.getLogic().hasJob()) {
                    continue;
                }
                if (!activeCpus.containsKey(cpu)) {
                    this.activeCpus.put(cpu, cpu.getPlan());
                    this.activeJobBytes += cpu.getPlan().bytes();
                    restored++;
                    restoredBytes += cpu.getPlan().bytes();
                }
            }
        }
        this.activeCpuCount = this.activeCpus.size();
        if (restored > 0) {
            LOGGER.info(
                    "Restored {} ECO CPU(s) with {} job bytes into activeCpus (total active: {})",
                    restored,
                    restoredBytes,
                    activeCpuCount);
        }
    }

    @Override
    public void updateFormed(boolean formed) {
        super.updateFormed(formed);
        if (formed) {
            this.accelerators = blockEntities.stream()
                    .filter(it -> it instanceof ECOComputationParallelCoreBlockEntity)
                    .mapToInt(it -> ((ECOComputationParallelCoreBlockEntity) it)
                            .getTier()
                            .getCPUAccelerators())
                    .sum();
            this.maxThreads = threadingCores.stream()
                    .mapToInt(it -> it.getTier().getCPUThreads())
                    .sum();

            // Step 1: restore CPU NBT from each threading core's deferredInit
            for (ECOComputationThreadingCoreBlockEntity core : threadingCores) {
                core.restoreDeferredCpus(this);
            }
            // Step 2: scan all cores for active CPUs and add to activeCpus map
            restoreActiveCpusFromThreadingCores();
            recalculateRemainingStorage();

            // Step 3: proactively rebind CraftingLinks for all restored CPUs
            IGridNode node = getNode();
            IGrid grid = node != null ? node.getGrid() : null;
            if (grid != null && activeCpuCount > 0) {
                int rebound = 0;
                for (ECOCraftingCPU cpu : activeCpus.keySet()) {
                    if (cpu.getLogic().onRestoredToGrid(grid)) {
                        rebound++;
                    }
                }
                if (rebound > 0) {
                    LOGGER.info("Proactively rebound {} ECO CPU CraftingLink(s) during cluster formation", rebound);
                }
            }

            this.fakeCpu =
                    new ECOCraftingCPU(this, availableStorage, controller != null ? controller.getTier() : ECOTier.L4);
            this.maxThreads = threadingCores.stream()
                    .mapToInt(it -> it.getTier().getCPUThreads())
                    .sum();
            LOGGER.debug(
                    "NE computation cluster formed: controller={} accelerators={} maxThreads={} availableStorage={}",
                    controller != null ? controller.getBlockPos() : null,
                    accelerators,
                    maxThreads,
                    availableStorage);
        } else {
            accelerators = 0;
            activeJobBytes = 0;
            activeCpuCount = 0;
            totalStorageBytes = 0;
            availableStorage = 0;
            maxThreads = 0;
            fakeCpu = null;
            LOGGER.debug(
                    "NE computation cluster unformed: controller={}",
                    controller != null ? controller.getBlockPos() : null);
        }
        updateGridForChangedCpu(this);
    }

    private void cancelActiveCpusForStructureBreak() {
        List<ECOCraftingCPU> cpusToCancel = new ArrayList<>(activeCpus.keySet());
        int deferredCleared = 0;

        for (ECOComputationThreadingCoreBlockEntity threadingCore : threadingCores) {
            deferredCleared += threadingCore.clearDeferredCpuData();
            for (ECOCraftingCPU cpu : threadingCore.getCpus()) {
                if (cpu != null && !cpusToCancel.contains(cpu)) {
                    cpusToCancel.add(cpu);
                }
            }
        }

        if (cpusToCancel.isEmpty() && deferredCleared == 0) {
            return;
        }

        if (!cpusToCancel.isEmpty()) {
            LOGGER.info(
                    "Cancelling {} ECO CPU job(s) because the computation multiblock was broken",
                    cpusToCancel.size());
        }
        if (deferredCleared > 0) {
            LOGGER.info(
                    "Cleared {} deferred ECO CPU restore tag(s) because the computation multiblock was broken",
                    deferredCleared);
        }

        activeCpus.clear();
        activeJobBytes = 0L;
        activeCpuCount = 0;

        for (ECOCraftingCPU cpu : cpusToCancel) {
            cancelCpuForStructureBreak(cpu);
        }

        updateAvailableStorageFromCounters(false);
        updateGridForChangedCpu(this);
    }

    private void cancelCpuForStructureBreak(ECOCraftingCPU cpu) {
        if (cpu == null || cpu.isAllocationProxy()) {
            return;
        }

        if (cpu.getLogic().hasJob()) {
            cpu.getLogic().cancel();
        } else {
            cpu.getLogic().storeItems();
        }

        if (cpu.hasRemainingItems() && cpu.getOwner() != null) {
            cpu.getOwner().dropCpuInventory(cpu);
        }

        cpu.getLogic().markForDeletion();
        if (cpu.getOwner() != null) {
            cpu.getOwner().deactivate(cpu);
        }
    }

    private long collectStorage(List<ECOComputationDriveBlockEntity> driveBlockEntities) {
        long ret = 0;
        for (ECOComputationDriveBlockEntity driveBlockEntity : driveBlockEntities) {
            ItemStack itemStack = driveBlockEntity.getCellStack();
            if (itemStack != null && !itemStack.isEmpty()) {
                if (itemStack.getItem() instanceof ECOComputationCellItem cellItem) {
                    ret += cellItem.getBytes();
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
            IGrid grid, ICraftingPlan job, IActionSource src, ICraftingRequester requestingMachine) {
        if (!this.isActive()) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (!this.hasFreeThread()) {
            return CraftingSubmitResult.CPU_BUSY;
        }
        if (this.availableStorage < job.bytes()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }
        ECOCraftingCPU cpu = null;
        ICraftingSubmitResult result = null;
        boolean submitted = false;
        for (ECOComputationThreadingCoreBlockEntity threadingCore : threadingCores) {
            cpu = threadingCore.spawn(job);
            if (cpu == null) continue;
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
        // Ensure the threading core is marked dirty so the CPU job is saved to disk.
        // (trySubmitJob already calls cpu.markDirty(), but belt-and-suspenders.)
        cpu.markDirty();
        if (this.activeCpus.put(cpu, job) == null) {
            this.activeJobBytes += job.bytes();
            this.activeCpuCount = this.activeCpus.size();
        }
        this.updateAvailableStorageFromCounters(false);
        this.updateGridForChangedCpu(this);
        return result;
    }

    public void recalculateRemainingStorage() {
        long oldAvailableStorage = this.availableStorage;
        this.totalStorageBytes = collectStorage(upperDrives) + collectStorage(lowerDrives);

        this.activeJobBytes = 0L;

        for (ICraftingPlan plan : this.activeCpus.values()) {
            this.activeJobBytes += plan.bytes();
        }
        this.activeCpuCount = this.activeCpus.size();

        this.availableStorage = this.totalStorageBytes - this.activeJobBytes;
        if (this.availableStorage < 0) {
            // Do NOT kill CPUs that are still in NBT-restore grace period.
            // They may have been loaded before drives finished initializing;
            // killing them now would permanently lose the crafting job.
            boolean killedAny = false;
            for (ECOCraftingCPU cpu : new ArrayList<>(this.activeCpus.keySet())) {
                ICraftingPlan plan = this.activeCpus.get(cpu);
                if (plan == null) {
                    continue;
                }
                if (cpu != null && cpu.getLogic().isInRestoreGrace()) {
                    LOGGER.warn(
                            "Skipping kill of restored-in-grace ECO CPU (planBytes={} totalStorage={} activeJobBytes={})",
                            plan.bytes(),
                            totalStorageBytes,
                            activeJobBytes);
                    continue;
                }
                this.killCpu(cpu, false, false);
                killedAny = true;
            }
            if (killedAny) {
                recalculateRemainingStorage();
            } else {
                // All remaining CPUs are in restore grace — allow temporary negative storage.
                // It will be corrected once drives become available.
                LOGGER.warn(
                        "ECO computation storage temporarily negative during restore: available={}, activeJobBytes={}, totalStorageBytes={}",
                        this.availableStorage,
                        this.activeJobBytes,
                        this.totalStorageBytes);
                // Still sync controller stats and notify grid so the UI shows active CPUs
                syncControllerStats();
                postGridCpuChange();
            }
            return;
        }
        syncControllerStats();
        if (oldAvailableStorage != this.availableStorage) {
            postGridCpuChange();
        }
    }

    private void updateAvailableStorageFromCounters(boolean syncController) {
        long oldAvailableStorage = this.availableStorage;
        this.availableStorage = this.totalStorageBytes - this.activeJobBytes;
        if (this.availableStorage < 0) {
            recalculateRemainingStorage();
            return;
        }
        if (syncController) {
            syncControllerStats();
        } else if (controller != null) {
            controller.markComputationStatsDirty();
        }
        if (syncController && oldAvailableStorage != this.availableStorage) {
            postGridCpuChange();
        }
    }

    public List<ECOCraftingCPU> getActiveCPUs() {
        pruneInactiveCpus();
        List<ECOCraftingCPU> cpus = new ArrayList<>();
        for (ECOCraftingCPU cpu : activeCpus.keySet()) {
            cpus.add(cpu);
        }
        return cpus;
    }

    public void pruneInactiveCpus() {
        List<ECOCraftingCPU> killList = new ArrayList<>();
        for (ECOCraftingCPU cpu : activeCpus.keySet()) {
            // Never prune a CPU that is still waiting for NBT-restore rebind
            if (cpu.getLogic().isInRestoreGrace()) {
                continue;
            }
            if (!cpu.getLogic().hasJob() && !cpu.getLogic().isMarkedForDeletion() && !cpu.hasRemainingItems()) {
                killList.add(cpu);
            }
        }
        for (ECOCraftingCPU cpu : killList) {
            killCpu(cpu, true);
        }
    }

    public int getActiveCpuCountCached() {
        return activeCpuCount;
    }

    public boolean hasFreeThread() {
        for (ECOComputationThreadingCoreBlockEntity threadingCore : threadingCores) {
            if (threadingCore.hasFreeCpuSlot()) {
                return true;
            }
        }
        return false;
    }

    public ECOCraftingCPU getFakeCPU() {
        if (this.fakeCpu == null || this.fakeCpu.getAvailableStorage() != this.availableStorage) {
            this.fakeCpu = new ECOCraftingCPU(
                    this, this.availableStorage, controller != null ? controller.getTier() : ECOTier.L4);
        }
        return fakeCpu;
    }

    public void deactivate(ECOCraftingCPU cpu) {
        ICraftingPlan plan = this.activeCpus.remove(cpu);
        if (plan != null) {
            this.activeJobBytes = Math.max(0L, this.activeJobBytes - plan.bytes());
            this.activeCpuCount = this.activeCpus.size();
            cpu.getOwner().deactivate(cpu);
            this.updateAvailableStorageFromCounters(false);
            this.updateGridForChangedCpu(this);
        }
    }

    public void cancelJob(ECOCraftingCPU cpu) {
        if (this.activeCpus.containsKey(cpu)) {
            this.killCpu(cpu, true);
        }
    }

    private void killCpu(ECOCraftingCPU cpu, boolean update) {
        killCpu(cpu, update, true);
    }

    private void killCpu(ECOCraftingCPU cpu, boolean update, boolean recalculate) {
        ICraftingPlan plan = activeCpus.get(cpu);
        if (plan == null) {
            // CPU may have already been removed by another call (e.g., from
            // recalculateRemainingStorage)
            return;
        }
        activeCpus.remove(cpu);
        activeJobBytes = Math.max(0L, activeJobBytes - plan.bytes());
        activeCpuCount = activeCpus.size();
        cpu.getLogic().cancel();
        cpu.getLogic().markForDeletion();
        cpu.getOwner().deactivate(cpu);
        if (recalculate) {
            this.updateAvailableStorageFromCounters(false);
        }
        if (update) {
            updateGridForChangedCpu(this);
        }
    }

    public void updateGridForChangedCpu(NEComputationCluster cluster) {
        postGridCpuChange();
        syncControllerStats();
    }

    private void postGridCpuChange() {
        boolean posted = false;

        for (var r : this.blockEntities) {
            IGridNode n = r.getActionableNode();
            if (n != null && n.getGrid() != null && !posted) {
                n.getGrid().postEvent(new GridCraftingCpuChange(n));
                posted = true;
            }
        }
    }

    private void syncControllerStats() {
        if (controller != null) {
            controller.markComputationStatsDirty();
            controller.updateInfos();
        }
    }
}
