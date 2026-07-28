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
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class NEComputationCluster extends NECluster<NEComputationCluster> {

    private static final long DEBUG_OVERDRIVE_CPU_BYTES = 9_200_000_000_000_000_000L;

    @Getter
    private final List<ECOComputationDriveBlockEntity> upperDrives = new ArrayList<>();
    @Getter
    private final List<ECOComputationDriveBlockEntity> lowerDrives = new ArrayList<>();
    @Getter
    private final List<ECOComputationThreadingCoreBlockEntity> threadingCores = new ArrayList<>();
    @Getter
    private final List<ECOComputationParallelCoreBlockEntity> parallelCores = new ArrayList<>();
    @Getter
    private ECOComputationCoolingControllerBlockEntity coolingController;
    @Getter
    @Nullable
    private ECOComputationSystemBlockEntity controller;
    @Getter
    @Nullable
    private IActionSource actionSource;
    private int maxThreads = 0;
    private long totalStorage = 0;
    private long availableStorage = 0;
    private CpuSelectionMode selectionMode = CpuSelectionMode.ANY;

    private final Map<ICraftingPlan, ECOCraftingCPU> activeCpus = new IdentityHashMap<>();
    private ECOCraftingCPU fakeCpu;
    private boolean lastDebugOverdriveState;

    @Getter
    @Nullable
    private NEComputationNetworkCluster networkCluster;

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

    @Override
    public int getNetworkMultiplier() {
        int configuredMultiplier = super.getNetworkMultiplier();
        if (configuredMultiplier <= 1 || coolingController == null) {
            return 1;
        }
        if (configuredMultiplier >= 8
            && coolingController.getTier().getTier() < ECOTier.L9.getTier()) {
            return 1;
        }
        return configuredMultiplier;
    }

    public void pickup(ICraftingPlan plan, ECOCraftingCPU cpu) {
        this.activeCpus.put(plan, cpu);
        // Restored CPUs are registered after the cluster's initial formed-state capacity calculation.
        // Recalculate immediately so persisted jobs consume their bytes and jobs restored without enough
        // computation cells are cancelled before they can intercept items from network storage.
        this.recalculateRemainingStorage();
    }

    @Override
    public void updateFormed(boolean formed) {
        super.updateFormed(formed);
        if (formed) {
            recalculateRemainingStorage();
            this.fakeCpu = new ECOCraftingCPU(this, availableStorage, controller != null ? controller.getTier() : ECOTier.L4);
            this.maxThreads = threadingCores.stream().mapToInt(it -> it.getTier().getCPUThreads()).sum();
            if (controller != null) {
                this.selectionMode = controller.getCpuSelectionMode();
            }
        } else {
            totalStorage = 0;
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
        if (networkCluster != null) {
            return networkCluster.getCPUAccelerators();
        }
        return getLocalCPUAccelerators();
    }

    public int getLocalCPUAccelerators() {
        long total = parallelCores.stream()
            .mapToLong(core -> core.getTier().getCPUAccelerators())
            .sum();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, total));
    }

    public void setNetworkCluster(@Nullable NEComputationNetworkCluster networkCluster) {
        this.networkCluster = networkCluster;
    }

    @Override
    protected boolean hasLinkedNetworkPeers() {
        return networkCluster != null && networkCluster.getMemberCount() > 1;
    }

    public long getTotalStorage() {
        return networkCluster == null ? getLocalTotalStorage() : networkCluster.getTotalStorage();
    }

    public long getLocalTotalStorage() {
        ensureDebugOverdriveState();
        return totalStorage;
    }

    public long getAvailableStorage() {
        return networkCluster == null ? getLocalAvailableStorage() : networkCluster.getAvailableStorage();
    }

    public long getAvailableStorageForGrid(@Nullable IGrid grid) {
        return networkCluster == null
            ? (grid == null || getLocalNode() != null && getLocalNode().getGrid() == grid ? getLocalAvailableStorage() : 0L)
            : networkCluster.getAvailableStorageForGrid(grid);
    }

    public long getLocalAvailableStorage() {
        ensureDebugOverdriveState();
        return availableStorage;
    }

    public boolean hasFullComputationDrives() {
        return !upperDrives.isEmpty()
            && !lowerDrives.isEmpty()
            && upperDrives.stream().allMatch(NEComputationCluster::hasComputationCell)
            && lowerDrives.stream().allMatch(NEComputationCluster::hasComputationCell);
    }

    public void onDriveContentsChanged() {
        recalculateRemainingStorage();
        if (networkCluster != null) {
            networkCluster.onHostCapacityChanged();
        }
    }

    public long getEffectiveAvailableStorage() {
        ensureDebugOverdriveState();
        long effectiveTotal = networkCluster != null && networkCluster.hasUltimateAggregateCapacity()
            ? Long.MAX_VALUE
            : saturatingMultiply(totalStorage, getNetworkMultiplier());
        return Math.max(0L, effectiveTotal - getActiveJobBytes());
    }

    public int getMaxThreads() {
        return networkCluster == null ? getLocalMaxThreads() : networkCluster.getMaxThreads();
    }

    public int getLocalMaxThreads() {
        return maxThreads;
    }

    public List<ECOCraftingCPU> getLocalActiveCPUs() {
        List<ECOCraftingCPU> cpus = new ArrayList<>();
        for (Map.Entry<ICraftingPlan, ECOCraftingCPU> entry : List.copyOf(activeCpus.entrySet())) {
            ECOCraftingCPU cpu = entry.getValue();
            if (cpu.getLogic().hasJob() || cpu.getLogic().isMarkedForDeletion() || cpu.hasRemainingItems()) {
                cpus.add(cpu);
            }
        }
        return cpus;
    }

    public IGridNode getLocalNode() {
        return controller == null ? null : controller.getActionableNode();
    }

    public boolean isLocallyActive() {
        IGridNode node = getLocalNode();
        return node != null && node.isActive();
    }

    public CpuSelectionMode getSelectionMode() {
        return networkCluster == null ? selectionMode : networkCluster.getSelectionMode();
    }

    public CpuSelectionMode getLocalSelectionMode() {
        return selectionMode;
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

    public void setLocalSelectionMode(CpuSelectionMode mode) {
        CpuSelectionMode next = mode == null ? CpuSelectionMode.ANY : mode;
        if (this.selectionMode == next) {
            return;
        }
        this.selectionMode = next;
        if (controller != null) {
            controller.setLocalSelectionMode(next);
        }
        updateGridForChangedCpu();
    }

    public void cycleSelectionMode() {
        if (networkCluster != null) {
            networkCluster.cycleSelectionMode();
            return;
        }
        setLocalSelectionMode(switch (selectionMode) {
            case ANY -> CpuSelectionMode.PLAYER_ONLY;
            case PLAYER_ONLY -> CpuSelectionMode.MACHINE_ONLY;
            case MACHINE_ONLY -> CpuSelectionMode.ANY;
        });
    }

    public @Nullable IGridNode getNode() {
        return getLocalNode();
    }

    public boolean isActive() {
        return networkCluster == null ? isLocallyActive() : networkCluster.isActive();
    }

    public ICraftingSubmitResult submitJob(
        IGrid grid,
        ICraftingPlan job,
        IActionSource src,
        ICraftingRequester requestingMachine
    ) {
        if (networkCluster != null) {
            return networkCluster.submitJob(grid, job, src, requestingMachine);
        }
        return submitJobLocal(grid, job, src, requestingMachine);
    }

    public ICraftingSubmitResult submitJobLocal(
        IGrid grid,
        ICraftingPlan job,
        IActionSource src,
        ICraftingRequester requestingMachine
    ) {
        if (!this.isLocallyActive()) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (this.getEffectiveAvailableStorage() < job.bytes()) {
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
        this.activeCpus.put(job, cpu);
        this.recalculateRemainingStorage();
        this.updateGridForChangedCpu();
        return result;
    }

    public void recalculateRemainingStorage() {
        lastDebugOverdriveState = NEConfig.debugECOHostOverdrive;
        this.totalStorage = NEConfig.debugECOHostOverdrive
            ? DEBUG_OVERDRIVE_CPU_BYTES
            : collectStorage(upperDrives) + collectStorage(lowerDrives);
        long usedStorage = getActiveJobBytes();

        this.availableStorage = Math.max(0L, totalStorage - usedStorage);
        long effectiveTotalStorage = networkCluster != null && networkCluster.hasUltimateAggregateCapacity()
            ? Long.MAX_VALUE
            : saturatingMultiply(totalStorage, getNetworkMultiplier());
        if (effectiveTotalStorage >= usedStorage || this.activeCpus.isEmpty()) {
            return;
        }

        List<ICraftingPlan> plansToKill = List.copyOf(this.activeCpus.keySet());
        for (ICraftingPlan plan : plansToKill) {
            this.killCpu(plan, false, false);
        }

        this.availableStorage = Math.max(0L, totalStorage - getActiveJobBytes());
    }

    private void ensureDebugOverdriveState() {
        if (lastDebugOverdriveState != NEConfig.debugECOHostOverdrive) {
            recalculateRemainingStorage();
        }
    }

    long getLocalActiveJobBytes() {
        return getActiveJobBytes();
    }

    private long getActiveJobBytes() {
        long usedStorage = 0L;
        for (ICraftingPlan plan : List.copyOf(this.activeCpus.keySet())) {
            long bytes = Math.max(0L, plan.bytes());
            if (usedStorage > Long.MAX_VALUE - bytes) {
                return Long.MAX_VALUE;
            }
            usedStorage += bytes;
        }
        return usedStorage;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static boolean hasComputationCell(ECOComputationDriveBlockEntity drive) {
        ItemStack stack = drive.getCellStack();
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ECOComputationCellItem;
    }

    public List<ECOCraftingCPU> getActiveCPUs() {
        return networkCluster == null ? getLocalActiveCPUs() : networkCluster.getActiveCPUs();
    }

    public List<ECOCraftingCPU> getActiveCPUs(@Nullable IGrid grid) {
        if (grid == null) {
            return List.of();
        }
        return (networkCluster == null ? getLocalActiveCPUs() : networkCluster.getActiveCPUs()).stream()
            .filter(cpu -> cpu.getGrid() == grid)
            .toList();
    }

    public void pruneInactiveCPUs() {
        if (networkCluster != null) {
            networkCluster.pruneInactiveCPUs();
            return;
        }
        pruneInactiveCPUsLocal();
    }

    public void pruneInactiveCPUsLocal() {
        List<ICraftingPlan> killList = new ArrayList<>();
        for (Map.Entry<ICraftingPlan, ECOCraftingCPU> entry : List.copyOf(activeCpus.entrySet())) {
            ECOCraftingCPU cpu = entry.getValue();
            if (!cpu.getLogic().hasJob() && !cpu.getLogic().isMarkedForDeletion() && !cpu.hasRemainingItems()) {
                killList.add(entry.getKey());
            }
        }
        for (ICraftingPlan iCraftingPlan : killList) {
            killCpu(iCraftingPlan, false);
        }
        if (!killList.isEmpty()) {
            updateGridForChangedCpu();
        }
    }

    public ECOCraftingCPU getFakeCPU() {
        ECOCraftingCPU fake = networkCluster == null ? null : networkCluster.getFakeCPU();
        return fake == null ? getFakeCPULocal() : fake;
    }

    public @Nullable ECOCraftingCPU getFakeCPU(@Nullable IGrid grid) {
        if (networkCluster == null) {
            return getFakeCPULocal();
        }
        return networkCluster.getFakeCPU(grid);
    }

    public ECOCraftingCPU getFakeCPULocal() {
        long currentAvailableStorage = this.getLocalAvailableStorage();
        if (this.fakeCpu == null || this.fakeCpu.getAvailableStorage() != currentAvailableStorage) {
            this.fakeCpu = new ECOCraftingCPU(
                this,
                currentAvailableStorage,
                controller != null ? controller.getTier() : ECOTier.L4
            );
        }
        return fakeCpu;
    }

    public void deactivate(ICraftingPlan plan) {
        if (networkCluster != null) {
            networkCluster.deactivate(plan);
            return;
        }
        deactivateLocal(plan);
    }

    public boolean hasLocalPlan(ICraftingPlan plan) {
        return activeCpus.containsKey(plan);
    }

    public void deactivateLocal(ICraftingPlan plan) {
        ECOCraftingCPU cpu = this.activeCpus.remove(plan);
        this.recalculateRemainingStorage();
        this.updateGridForChangedCpu();
        if (cpu != null) {
            cpu.getOwner().deactivate(cpu);
        }
    }

    public void cancelJob(ICraftingPlan plan) {
        if (networkCluster != null) {
            networkCluster.cancelJob(plan);
            return;
        }
        cancelJobLocal(plan);
    }

    public void cancelJobLocal(ICraftingPlan plan) {
        if (this.activeCpus.get(plan) != null) {
            this.killCpu(plan, true);
        }
    }

    private void killCpu(ICraftingPlan plan, boolean update) {
        killCpu(plan, update, true);
    }

    private void killCpu(ICraftingPlan plan, boolean update, boolean recalculate) {
        ECOCraftingCPU cpu = activeCpus.get(plan);
        if (cpu == null) {
            // CPU may have already been removed by another call (e.g., from recalculateRemainingStorage)
            return;
        }
        cpu.getLogic().cancel();
        cpu.getLogic().markForDeletion();
        if (!cpu.hasRemainingItems()) {
            cpu.getOwner().deactivate(cpu);
            this.activeCpus.remove(plan);
        }
        if (recalculate) {
            this.recalculateRemainingStorage();
        }
        if (update) {
            updateGridForChangedCpu();
        }
    }

    private void updateGridForChangedCpu() {
        boolean posted = false;

        for (var r : this.blockEntities) {
            IGridNode n = r.getActionableNode();
            if (n != null && n.getGrid() != null && !posted) {
                n.getGrid().postEvent(new GridCraftingCpuChange(n));
                posted = true;
            }
        }

    }
}
