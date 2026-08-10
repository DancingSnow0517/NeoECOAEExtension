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
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationCoolingControllerBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.NEComputationUpgradeRules;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import com.google.common.math.LongMath;
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

    @Nullable private ECOComputationCoolingControllerBlockEntity coolingController;

    @Getter
    @Nullable private ECOComputationSystemBlockEntity controller;

    @Getter
    @Nullable private IActionSource actionSource;

    /**
     * User-configured parallel accelerators (persisted by controller).
     * This is the desired value set by the player.
     */
    private int configuredAccelerators = 0;

    /**
     * Effective parallel accelerators actually applied to CPUs.
     * This is min(configuredAccelerators, acceleratorLimit).
     */
    private int effectiveAccelerators = 0;

    /**
     * Maximum accelerators allowed by the current upgrade and parallel cores.
     */
    private int acceleratorLimit = 0;

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

    @Nullable private NEComputationNetworkCluster networkCluster;

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
        if (blockEntity instanceof ECOComputationCoolingControllerBlockEntity coolingController) {
            this.coolingController = coolingController;
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
            // Load persisted configuration from controller and apply upgrade capacity
            if (controller != null) {
                configuredAccelerators = controller.getConfiguredParallelAccelerators();
            }
            applyComputationUpgradeCapacity();

            // Auto-set to max if configuration is 0 or uninitialized
            if (configuredAccelerators == 0 && acceleratorLimit > 0) {
                configuredAccelerators = acceleratorLimit;
                effectiveAccelerators = acceleratorLimit;
                // Persist back to controller
                if (controller != null) {
                    controller.onClusterAcceleratorsChanged(acceleratorLimit);
                }
            }

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
            // Apply the controller's persisted CPU auto-selection mode to the cluster
            if (controller != null) {
                this.selectionMode = controller.getCpuSelectionMode();
            }

            LOGGER.debug(
                    "NE computation cluster formed: controller={} accelerators={} maxThreads={} availableStorage={}",
                    controller != null ? controller.getBlockPos() : null,
                    effectiveAccelerators,
                    maxThreads,
                    availableStorage);
        } else {
            configuredAccelerators = 0;
            effectiveAccelerators = 0;
            acceleratorLimit = 0;
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
                    "Cancelling {} ECO CPU job(s) because the computation multiblock was broken", cpusToCancel.size());
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
                    ret = LongMath.saturatedAdd(ret, cellItem.getBytes());
                }
            }
        }
        return ret;
    }

    /**
     * Sets the user-configured parallel accelerators and applies them immediately.
     * This is called from the UI when the player changes the setting.
     *
     * @param value the desired accelerator count (will be clamped to [0, acceleratorLimit])
     * @return true if the value was accepted and applied
     */
    public boolean setConfiguredAccelerators(int value) {
        if (value < 0 || value > acceleratorLimit) {
            return false;
        }
        this.configuredAccelerators = value;
        this.effectiveAccelerators = Math.min(value, acceleratorLimit);

        // Notify controller to persist the configuration
        if (controller != null) {
            controller.onClusterAcceleratorsChanged(value);
        }

        // Notify the grid that CPU capacity has changed
        updateGridForChangedCpu(this);
        return true;
    }

    /**
     * Recalculates the accelerator limit based on upgrade items and parallel cores,
     * then applies the configured value within the new limit.
     * Called when upgrade slot changes or multiblock structure changes.
     */
    public void recalculateAcceleratorLimit() {
        ItemStack upgrade = controller != null
                ? controller.getComputationUpgradeItemHandler().getStackInSlot(0)
                : ItemStack.EMPTY;
        int multiplier = NEComputationUpgradeRules.fieldGeneratorMultiplier(upgrade);

        // Calculate new limit
        int oldLimit = acceleratorLimit;
        acceleratorLimit = NEComputationUpgradeRules.hasInfiniteCapacity(upgrade)
                ? NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS
                : saturatedAcceleratorMultiply(baseAcceleratorCapacity(), multiplier);

        // Clamp configured value to new limit. Push the clamp through to the controller too: it owns
        // the persisted copy, and a silent divergence here would resurface the old, larger value the
        // next time the cluster forms.
        int clamped = Math.min(configuredAccelerators, acceleratorLimit);
        if (clamped != configuredAccelerators) {
            configuredAccelerators = clamped;
            if (controller != null) {
                controller.onClusterAcceleratorsChanged(clamped);
            }
        }
        effectiveAccelerators = clamped;

        // Update thread capacity
        maxThreads = saturatedIntMultiply(baseThreadCapacity(), multiplier);
        for (ECOComputationThreadingCoreBlockEntity core : threadingCores) {
            core.ensureCpuCapacity(multiplier);
        }

        // Notify controller if limit changed
        if (controller != null && oldLimit != acceleratorLimit) {
            controller.onClusterLimitChanged(acceleratorLimit);
        }

        // Recalculate storage and notify grid
        recalculateRemainingStorage();
        updateGridForChangedCpu(this);
    }

    public void tick() {
        pruneInactiveCpus();
    }

    private void applyComputationUpgradeCapacity() {
        ItemStack upgrade = controller != null
                ? controller.getComputationUpgradeItemHandler().getStackInSlot(0)
                : ItemStack.EMPTY;
        int multiplier = NEComputationUpgradeRules.fieldGeneratorMultiplier(upgrade);
        acceleratorLimit = NEComputationUpgradeRules.hasInfiniteCapacity(upgrade)
                ? NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS
                : saturatedAcceleratorMultiply(baseAcceleratorCapacity(), multiplier);
        effectiveAccelerators = Math.max(0, Math.min(acceleratorLimit, configuredAccelerators));
        maxThreads = saturatedIntMultiply(baseThreadCapacity(), multiplier);
        for (ECOComputationThreadingCoreBlockEntity core : threadingCores) {
            core.ensureCpuCapacity(multiplier);
        }
    }

    private int baseAcceleratorCapacity() {
        long capacity = 0L;
        for (ECOComputationParallelCoreBlockEntity core : parallelCores) {
            capacity = LongMath.saturatedAdd(capacity, core.getTier().getCPUAccelerators());
        }
        return capacity >= NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS
                ? NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS
                : (int) capacity;
    }

    private int baseThreadCapacity() {
        long capacity = 0L;
        for (ECOComputationThreadingCoreBlockEntity core : threadingCores) {
            capacity = LongMath.saturatedAdd(capacity, core.getTier().getCPUThreads());
        }
        return capacity >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) capacity;
    }

    private static int saturatedIntMultiply(int value, int multiplier) {
        if (value <= 0 || multiplier <= 0) {
            return 0;
        }
        return value > Integer.MAX_VALUE / multiplier ? Integer.MAX_VALUE : value * multiplier;
    }

    private static int saturatedAcceleratorMultiply(int value, int multiplier) {
        if (value <= 0 || multiplier <= 0) {
            return 0;
        }
        return value > NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS / multiplier
                ? NEComputationUpgradeRules.MAX_SAFE_ACCELERATORS
                : value * multiplier;
    }

    public int getCPUAccelerators() {
        return networkCluster == null ? getLocalCPUAccelerators() : networkCluster.getCPUAccelerators();
    }

    public int getLocalCPUAccelerators() {
        return effectiveAccelerators;
    }

    public int getCPUAcceleratorLimit() {
        return acceleratorLimit;
    }

    public int getMaxThreads() {
        return networkCluster == null ? getLocalMaxThreads() : networkCluster.getMaxThreads();
    }

    public int getLocalMaxThreads() {
        return maxThreads;
    }

    public long getAvailableStorage() {
        return networkCluster == null ? getLocalAvailableStorage() : networkCluster.getAvailableStorage();
    }

    public long getLocalAvailableStorage() {
        return availableStorage;
    }

    public long getTotalStorageBytes() {
        return networkCluster == null ? getLocalTotalStorageBytes() : networkCluster.getTotalStorageBytes();
    }

    public long getLocalTotalStorageBytes() {
        return totalStorageBytes;
    }

    /** Bytes reserved by jobs owned by this physical host, before network aggregation. */
    public long getLocalActiveJobBytes() {
        return activeJobBytes;
    }

    public int getConfiguredAccelerators() {
        return configuredAccelerators;
    }

    public boolean canBeAutoSelectedFor(IActionSource actionSource) {
        if (networkCluster != null) {
            return networkCluster.canBeAutoSelectedFor(actionSource);
        }
        return switch (selectionMode) {
            case ANY -> true;
            case PLAYER_ONLY -> actionSource.player().isPresent();
            case MACHINE_ONLY -> actionSource.player().isEmpty();
        };
    }

    public void setSelectionMode(CpuSelectionMode mode) {
        if (networkCluster != null) {
            networkCluster.setSelectionMode(mode);
            return;
        }
        setLocalSelectionMode(mode);
    }

    public CpuSelectionMode getLocalSelectionMode() {
        return selectionMode;
    }

    public CpuSelectionMode getSelectionMode() {
        return networkCluster == null ? selectionMode : networkCluster.getSelectionMode();
    }

    public void setLocalSelectionMode(CpuSelectionMode mode) {
        if (this.selectionMode != mode) {
            this.selectionMode = mode;
            // Persist to controller NBT so the mode survives world reload
            if (controller != null) {
                controller.setCpuSelectionMode(mode);
            }
            updateGridForChangedCpu(this);
        }
    }

    public void cycleSelectionMode() {
        CpuSelectionMode next =
                switch (selectionMode) {
                    case ANY -> CpuSelectionMode.PLAYER_ONLY;
                    case PLAYER_ONLY -> CpuSelectionMode.MACHINE_ONLY;
                    case MACHINE_ONLY -> CpuSelectionMode.ANY;
                };
        setSelectionMode(next);
    }

    public @Nullable IGridNode getNode() {
        return controller != null ? controller.getActionableNode() : null;
    }

    public boolean isActive() {
        return networkCluster == null ? isLocallyActive() : networkCluster.isActive();
    }

    public boolean isLocallyActive() {
        IGridNode node = this.getNode();
        return node != null && node.isActive();
    }

    public ICraftingSubmitResult submitJob(
            IGrid grid, ICraftingPlan job, IActionSource src, ICraftingRequester requestingMachine) {
        return networkCluster == null
                ? submitLocalJob(grid, job, src, requestingMachine)
                : networkCluster.submitJob(grid, job, src, requestingMachine);
    }

    public ICraftingSubmitResult submitLocalJob(
            IGrid grid, ICraftingPlan job, IActionSource src, ICraftingRequester requestingMachine) {
        return submitLocalJob(grid, job, src, requestingMachine, false);
    }

    /**
     * A linked computation network owns byte capacity collectively, while the
     * selected local threading core owns the CPU instance that executes the job.
     */
    public ICraftingSubmitResult submitLocalJob(
            IGrid grid,
            ICraftingPlan job,
            IActionSource src,
            ICraftingRequester requestingMachine,
            boolean useAggregateNetworkCapacity) {
        if (!this.isLocallyActive()) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (!this.hasLocalFreeThread()) {
            return CraftingSubmitResult.CPU_BUSY;
        }
        if (!hasSufficientSubmissionCapacity(useAggregateNetworkCapacity, this.availableStorage, job.bytes())) {
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

    static boolean hasSufficientSubmissionCapacity(
            boolean useAggregateNetworkCapacity, long localAvailableStorage, long requiredStorage) {
        return useAggregateNetworkCapacity || localAvailableStorage >= requiredStorage;
    }

    public void recalculateRemainingStorage() {
        long oldAvailableStorage = this.availableStorage;
        long baseStorage = LongMath.saturatedAdd(collectStorage(upperDrives), collectStorage(lowerDrives));
        ItemStack upgrade = controller != null
                ? controller.getComputationUpgradeItemHandler().getStackInSlot(0)
                : ItemStack.EMPTY;
        if (NEComputationUpgradeRules.hasInfiniteCapacity(upgrade)) {
            this.totalStorageBytes = Long.MAX_VALUE;
        } else {
            this.totalStorageBytes = LongMath.saturatedMultiply(
                    baseStorage, NEComputationUpgradeRules.fieldGeneratorMultiplier(upgrade));
        }

        this.activeJobBytes = 0L;

        for (ICraftingPlan plan : this.activeCpus.values()) {
            this.activeJobBytes = LongMath.saturatedAdd(this.activeJobBytes, plan.bytes());
        }
        this.activeCpuCount = this.activeCpus.size();

        long remainingStorage = this.totalStorageBytes - this.activeJobBytes;
        if (networkCluster != null) {
            // A linked network owns capacity collectively. This host may be locally overcommitted
            // while the aggregate still has room, so do not evict its active CPUs here.
            this.availableStorage = Math.max(0L, remainingStorage);
            return;
        }
        this.availableStorage = remainingStorage;
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
        return networkCluster == null ? getLocalActiveCPUs() : networkCluster.getActiveCPUs();
    }

    public List<ECOCraftingCPU> getActiveCPUs(@Nullable IGrid grid) {
        if (grid == null) {
            return List.of();
        }
        if (networkCluster != null) {
            return networkCluster.getActiveCPUs(grid);
        }
        if (!isLocallyActive()) {
            return List.of();
        }
        List<ECOCraftingCPU> result = new ArrayList<>();
        for (ECOCraftingCPU cpu : getLocalActiveCPUs()) {
            try {
                if (cpu.getGrid() == grid) {
                    result.add(cpu);
                }
            } catch (RuntimeException ignored) {
                // A CPU can lose its controller node while the grid is splitting.
            }
        }
        return result;
    }

    public List<ECOCraftingCPU> getLocalActiveCPUs() {
        return new ArrayList<>(activeCpus.keySet());
    }

    /** Returns the active CPU map view without pruning or allocating a snapshot list. */
    public Iterable<ECOCraftingCPU> activeCpusView() {
        if (networkCluster != null) {
            return networkCluster.getActiveCPUs();
        }
        return activeCpus.keySet();
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
        return networkCluster == null ? activeCpuCount : networkCluster.getActiveCpuCount();
    }

    public int getLocalActiveCpuCount() {
        return activeCpuCount;
    }

    public boolean hasActiveCraftingJobs() {
        if (activeCpuCount > 0 || !activeCpus.isEmpty()) {
            return true;
        }
        for (ECOComputationThreadingCoreBlockEntity core : threadingCores) {
            for (ECOCraftingCPU cpu : core.getCpus()) {
                if (cpu != null && cpu.getLogic().hasJob()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasFreeThread() {
        return networkCluster == null ? hasLocalFreeThread() : networkCluster.hasFreeThread();
    }

    public boolean hasLocalFreeThread() {
        if (activeCpuCount >= maxThreads) {
            return false;
        }
        for (ECOComputationThreadingCoreBlockEntity threadingCore : threadingCores) {
            if (threadingCore.hasFreeCpuSlot()) {
                return true;
            }
        }
        return false;
    }

    public ECOCraftingCPU getFakeCPU() {
        long available = getAvailableStorage();
        if (this.fakeCpu == null || this.fakeCpu.getAvailableStorage() != available) {
            this.fakeCpu = new ECOCraftingCPU(this, available, controller != null ? controller.getTier() : ECOTier.L4);
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
        if (networkCluster != null) {
            networkCluster.onHostCapacityChanged();
            return;
        }
        notifyLocalGridChange();
    }

    public void notifyLocalGridChange() {
        postGridCpuChange();
        syncControllerStats();
    }

    public void setNetworkCluster(@Nullable NEComputationNetworkCluster networkCluster) {
        if (this.networkCluster == networkCluster) {
            return;
        }
        this.networkCluster = networkCluster;
        if (controller != null) {
            controller.markComputationStatsDirty();
        }
    }

    @Nullable public NEComputationNetworkCluster getNetworkCluster() {
        return networkCluster;
    }

    @Override
    public int getNetworkMultiplier() {
        if (networkCluster == null || networkCluster.getMemberCount() <= 1) {
            return 1;
        }
        int configuredMultiplier = isHighEnergyNetworkMode() ? 8 : isNetworkMode() ? 2 : 1;
        if (configuredMultiplier <= 1 || coolingController == null) {
            return 1;
        }
        if (configuredMultiplier >= 8 && coolingController.getTier().getTier() < ECOTier.L9.getTier()) {
            return 1;
        }
        return configuredMultiplier;
    }

    private void postGridCpuChange() {
        boolean posted = false;

        for (var r : this.blockEntities) {
            IGridNode n = r.getActionableNode();
            if (n == null || posted) {
                continue;
            }
            try {
                if (n.isOnline() && n.getGrid() != null) {
                    n.getGrid().postEvent(new GridCraftingCpuChange(n));
                    posted = true;
                }
            } catch (RuntimeException ignored) {
                // A node can leave its grid between the online check and the
                // event post while a network is splitting.
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
