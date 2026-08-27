package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ECOCraftingCPU implements ICraftingCPU {

    @Getter
    private final NEComputationCluster cluster;
    @Getter
    @Nullable
    private ICraftingPlan plan;
    @Getter
    private final ECOCraftingCPULogic logic = new ECOCraftingCPULogic(this);
    @Getter
    @Nullable
    private final ECOComputationThreadingCoreBlockEntity owner;
    @Getter
    private final IECOTier tier;

    public ECOCraftingCPU(NEComputationCluster cluster, ICraftingPlan plan, ECOComputationThreadingCoreBlockEntity owner) {
        this.cluster = cluster;
        this.plan = plan;
        this.owner = owner;
        this.tier = owner.getTier();
    }

    /**
     * Placeholder CPU that advertises the cluster's free capacity in the crafting terminal.
     * It owns no thread, never runs a job, and is never registered in the cluster's active CPU map.
     */
    public ECOCraftingCPU(NEComputationCluster cluster, IECOTier tier) {
        this.cluster = cluster;
        this.plan = null;
        this.owner = null;
        this.tier = tier;
    }

    @Override
    public boolean isBusy() {
        return logic.hasJob();
    }

    @SuppressWarnings("removal")
    @Override
    public @Nullable CraftingJobStatus getJobStatus() {
        var finalOutput = logic.getFinalJobOutput();
        if (finalOutput != null) {
            var elapsedTimeTracker = logic.getElapsedTimeTracker();
            var progress =
                Math.max(0, elapsedTimeTracker.getStartItemCount() - elapsedTimeTracker.getRemainingItemCount());
            return new CraftingJobStatus(
                finalOutput, elapsedTimeTracker.getStartItemCount(), progress, elapsedTimeTracker.getElapsedTime());
        } else {
            return null;
        }
    }


    @Override
    public void cancelJob() {
        // Cancelling the job itself must never depend on the plan, otherwise a CPU that failed to restore
        // its plan would keep a live job that no player or machine can ever stop.
        logic.cancel();
        if (this.plan != null) {
            this.cluster.cancelJob(plan);
        }
    }

    /**
     * Vanilla semantics: the byte capacity of this CPU, not the size of the job it happens to be running.
     * ECO threads share their cluster's byte pool, so the capacity of a single thread is whatever is still
     * free in the pool plus the bytes this thread already reserved. With a single job running that is
     * exactly the pool's total size, matching what a vanilla crafting CPU reports.
     */
    @Override
    public long getAvailableStorage() {
        long free = cluster.getAvailableStorage();
        if (this.plan == null || !cluster.isPlanRegistered(this.plan)) {
            // Not reserved (yet): the plan under submission must still fit in the free pool.
            return free;
        }
        long reserved = this.plan.bytes();
        return free > Long.MAX_VALUE - reserved ? Long.MAX_VALUE : free + reserved;
    }

    @Override
    public int getCoProcessors() {
        return cluster.getCPUAccelerators();
    }

    @Override
    public @Nullable Component getName() {
        // ECO computation clusters have no custom-name concept, and ICraftingCPU#getName is @Nullable.
        return null;
    }

    @Override
    public CpuSelectionMode getSelectionMode() {
        return cluster.getSelectionMode();
    }

    public void markDirty() {
        if (this.owner != null) {
            this.owner.saveChanges();
        }
    }

    public boolean isActive() {
        return cluster.isActive();
    }

    public void deactivate() {
        this.cluster.deactivate(this.plan);
    }

    @Nullable
    public Level getLevel() {
        var controller = cluster.getController();
        return controller != null ? controller.getLevel() : null;
    }

    @Nullable
    public IGrid getGrid() {
        var controller = cluster.getController();
        if (controller == null) {
            return null;
        }
        IGridNode gridNode = controller.getGridNode();
        return gridNode != null ? gridNode.getGrid() : null;
    }

    public IActionSource getActionSource() {
        return cluster.getActionSource();
    }

    private void writeCraftingPlanToNBT(ICraftingPlan plan, CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag outputTag = GenericStack.writeTag(registries, plan.finalOutput());
        tag.put("output", outputTag);
        tag.putLong("bytes", plan.bytes());
        tag.putBoolean("simulation", plan.simulation());
        tag.putBoolean("multiplePaths", plan.multiplePaths());
    }

    /**
     * @return the restored plan, or null if the persisted data cannot produce a usable one.
     */
    @Nullable
    private CraftingPlan readCraftingPlanFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        GenericStack output = GenericStack.readTag(registries, tag.getCompound("output"));
        if (output == null) {
            // The final output no longer decodes (removed item/fluid, corrupt tag). A plan without an
            // output cannot drive a job, so refuse it instead of handing out a half-valid plan.
            return null;
        }
        long bytes = tag.getLong("bytes");
        boolean simulation = tag.getBoolean("simulation");
        boolean multiplePaths = tag.getBoolean("multiplePaths");
        // ICraftingPlan exposes these collections directly and callers are allowed to iterate them,
        // so they must be empty rather than null - only the summary fields survive persistence.
        return new CraftingPlan(
            output,
            bytes,
            simulation,
            multiplePaths,
            new KeyCounter(),
            new KeyCounter(),
            new KeyCounter(),
            Map.of()
        );
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        logic.writeToNBT(data, registries);
        if (this.plan != null) {
            CompoundTag tag = new CompoundTag();
            writeCraftingPlanToNBT(this.plan, tag, registries);
            data.put("plan", tag);
        }
    }

    /**
     * Restores this CPU. The caller treats a null {@link #getPlan()} as "this CPU could not be restored"
     * and keeps the persisted data for a later retry, so the plan is decoded and validated <em>before</em>
     * the logic is loaded: restoring a job registers a crafting link with the grid and can push items back
     * into network storage, and neither may happen for a CPU that will be rejected and replayed.
     */
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        CraftingPlan restoredPlan = null;
        if (data.contains("plan")) {
            restoredPlan = readCraftingPlanFromNBT(data.getCompound("plan"), registries);
        }
        if (restoredPlan == null) {
            return;
        }
        logic.readFromNBT(data, registries);
        // Only publish the plan once the logic loaded without throwing, so that getPlan() != null
        // means "fully restored" for the caller.
        this.plan = restoredPlan;
    }

    /**
     * @return true if this CPU still physically holds items that must be returned to the network
     *         before its thread can be released.
     */
    public boolean hasRemainingItems() {
        return logic.hasOwnedItems();
    }
}
