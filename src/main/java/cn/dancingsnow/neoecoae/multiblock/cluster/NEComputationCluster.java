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
import cn.dancingsnow.neoecoae.api.me.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationParallelCoreBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import cn.dancingsnow.neoecoae.util.NEMath;
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

    @Getter
    private final List<ECOComputationDriveBlockEntity> upperDrives = new ArrayList<>();
    @Getter
    private final List<ECOComputationDriveBlockEntity> lowerDrives = new ArrayList<>();
    @Getter
    private final List<ECOComputationThreadingCoreBlockEntity> threadingCores = new ArrayList<>();
    @Getter
    private final List<ECOComputationParallelCoreBlockEntity> parallelCores = new ArrayList<>();
    @Getter
    @Nullable
    private ECOComputationSystemBlockEntity controller;
    @Getter
    @Nullable
    private IActionSource actionSource;
    private int maxThreads = 0;
    private long availableStorage = 0;
    @Getter
    private CpuSelectionMode selectionMode = CpuSelectionMode.ANY;
    @Getter
    @Nullable
    private NEComputationNetworkCluster networkCluster;

    private final Map<ICraftingPlan, ECOCraftingCPU> activeCpus = new IdentityHashMap<>();
    private ECOCraftingCPU fakeCpu;

    public NEComputationCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    @Override
    protected boolean hideAllCasingsWhenFormed() {
        return true;
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

    public void setNetworkCluster(@Nullable NEComputationNetworkCluster networkCluster) {
        this.networkCluster = networkCluster;
    }

    /**
     * Whether this member should contribute the group's shared placeholder CPU. Every member in a
     * group reports the same pooled numbers, so only the first member avoids advertising duplicate
     * free capacity in the crafting terminal.
     */
    public boolean isNetworkRepresentative() {
        return networkCluster == null || networkCluster.isLeader(this);
    }

    @Override
    public void destroy() {
        NELogicalNetworkManager.detachBeforeDestroy(this);
        super.destroy();
    }

    /**
     * Own real byte capacity from installed computation cells, ignoring network pooling.
     */
    public long getTotalStorage() {
        return collectStorage(upperDrives) + collectStorage(lowerDrives);
    }

    /**
     * Bytes reserved by this member's active jobs. This may exceed the member's physical storage while
     * it belongs to a computation network, because the network admits jobs against the shared byte pool.
     */
    public long getOwnUsedStorage() {
        return getActiveJobBytes();
    }

    public int getOwnMaxThreads() {
        return this.maxThreads;
    }

    public int getMaxThreads() {
        return networkCluster != null ? networkCluster.getTotalThreads() : this.maxThreads;
    }

    public long getAvailableStorage() {
        return networkCluster != null ? networkCluster.getPooledAvailableStorage() : this.availableStorage;
    }

    public int getPooledParallelism() {
        return networkCluster != null ? networkCluster.getTotalParallelism() : getCPUAccelerators();
    }

    public long getPooledTotalStorage() {
        return networkCluster != null ? networkCluster.getTotalStorageCapacity() : getTotalStorage();
    }

    public int getActiveCPUCount() {
        if (networkCluster == null) {
            return getActiveCPUs().size();
        }
        int total = 0;
        for (NEComputationCluster member : networkCluster.getMembers()) {
            total += member.getActiveCPUs().size();
        }
        return total;
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
            // The placeholder CPU is created lazily by getFakeCPU() and must never be replaced while the
            // cluster lives, so it is deliberately not (re)assigned here.
            this.maxThreads = threadingCores.stream().mapToInt(it -> it.getTier().getCPUThreads()).sum();
            if (controller != null) {
                this.selectionMode = controller.getCpuSelectionMode();
            }
        } else {
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
        long total = parallelCores.stream()
            .mapToLong(core -> core.getTier().getCPUAccelerators())
            .sum();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, total));
    }

    public boolean canBeAutoSelectedFor(IActionSource actionSource) {
        return switch (selectionMode) {
            case ANY -> true;
            case PLAYER_ONLY -> actionSource.player().isPresent();
            case MACHINE_ONLY -> actionSource.player().isEmpty();
        };
    }

    public void setSelectionMode(CpuSelectionMode mode) {
        if (this.selectionMode == mode) {
            return;
        }
        this.selectionMode = mode;
        if (controller != null) {
            controller.setCpuSelectionMode(mode);
        }
        updateGridForChangedCpu();
    }

    public void cycleSelectionMode() {
        setSelectionMode(switch (selectionMode) {
            case ANY -> CpuSelectionMode.PLAYER_ONLY;
            case PLAYER_ONLY -> CpuSelectionMode.MACHINE_ONLY;
            case MACHINE_ONLY -> CpuSelectionMode.ANY;
        });
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
        if (this.networkCluster != null) {
            return this.networkCluster.submitJob(grid, job, src, requestingMachine);
        }
        job = ECOPlanningResultRegistry.resolveSubmissionPlan(job);
        if (!this.isActive()) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (this.availableStorage < job.bytes()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }
        ICraftingSubmitResult result = trySpawnLocalJob(grid, job, src, requestingMachine);
        return result == null ? CraftingSubmitResult.NO_CPU_FOUND : result;
    }

    /**
     * Tries to spawn and submit the job on one of this member's own physical threading cores.
     * Returns null only when no core ever produced a CPU at all (nothing to report); an unsuccessful
     * {@link ICraftingSubmitResult} from a spawned-but-rejected CPU is still returned so the caller
     * can surface the specific rejection reason.
     */
    @Nullable
    ICraftingSubmitResult trySpawnLocalJob(
        IGrid grid,
        ICraftingPlan job,
        IActionSource src,
        ICraftingRequester requestingMachine
    ) {
        ICraftingSubmitResult lastResult = null;
        for (ECOComputationThreadingCoreBlockEntity threadingCore : threadingCores) {
            ECOCraftingCPU cpu = threadingCore.spawn(job);
            if (cpu == null) continue;
            ICraftingSubmitResult result = cpu.getLogic().trySubmitJob(grid, job, src, requestingMachine);
            if (result.successful()) {
                if (this.registerActiveJob(job, cpu)) {
                    return result;
                }
                return CraftingSubmitResult.CPU_TOO_SMALL;
            }
            threadingCore.deactivate(cpu);
            lastResult = result;
        }
        return lastResult;
    }

    boolean registerActiveJob(ICraftingPlan job, ECOCraftingCPU cpu) {
        this.activeCpus.put(job, cpu);
        this.recalculateRemainingStorage();
        this.updateGridForChangedCpu();
        return this.activeCpus.get(job) == cpu && cpu.getLogic().hasJob();
    }

    public void recalculateRemainingStorage() {
        long totalStorage = getTotalStorage();
        long usedStorage = getActiveJobBytes();

        this.availableStorage = Math.max(0, totalStorage - Math.min(totalStorage, usedStorage));
        boolean reservationsFit = networkCluster != null
            ? networkCluster.reservationsFit()
            : usedStorage <= totalStorage;
        if (reservationsFit || this.activeCpus.isEmpty()) {
            return;
        }

        List<ICraftingPlan> plansToKill = List.copyOf(this.activeCpus.keySet());
        for (ICraftingPlan plan : plansToKill) {
            this.killCpu(plan, false, false);
        }

        this.availableStorage = Math.max(0, totalStorage - getActiveJobBytes());
    }

    private long getActiveJobBytes() {
        long usedStorage = 0L;
        for (ICraftingPlan plan : List.copyOf(this.activeCpus.keySet())) {
            usedStorage = NEMath.saturatingAdd(
                usedStorage,
                Math.max(0L, plan.bytes())
            );
        }
        return usedStorage;
    }

    public List<ECOCraftingCPU> getActiveCPUs() {
        List<ECOCraftingCPU> cpus = new ArrayList<>();
        for (Map.Entry<ICraftingPlan, ECOCraftingCPU> entry : List.copyOf(activeCpus.entrySet())) {
            ECOCraftingCPU cpu = entry.getValue();
            if (cpu.getLogic().hasJob() || cpu.getLogic().isMarkedForDeletion() || cpu.hasRemainingItems()) {
                cpus.add(cpu);
            }
        }
        return cpus;
    }

    public void pruneInactiveCPUs() {
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

    /**
     * The placeholder CPU reads the cluster's free storage live, so it stays a stable identity for the
     * whole lifetime of the cluster. Recreating it would invalidate any CPU selection referring to it.
     */
    public ECOCraftingCPU getFakeCPU() {
        if (this.fakeCpu == null) {
            this.fakeCpu = new ECOCraftingCPU(this, controller != null ? controller.getTier() : ECOTier.L4);
        }
        return fakeCpu;
    }

    /**
     * @return true if the given plan currently holds a byte reservation in this cluster.
     */
    public boolean isPlanRegistered(@Nullable ICraftingPlan plan) {
        return plan != null && this.activeCpus.containsKey(plan);
    }

    public void deactivate(@Nullable ICraftingPlan plan) {
        ECOCraftingCPU cpu = this.activeCpus.remove(plan);
        this.recalculateRemainingStorage();
        this.updateGridForChangedCpu();
        if (cpu != null && cpu.getOwner() != null) {
            cpu.getOwner().deactivate(cpu);
        }
    }

    public void cancelJob(ICraftingPlan plan) {
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
            if (cpu.getOwner() != null) {
                cpu.getOwner().deactivate(cpu);
            }
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
