package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ECOCraftingCPU implements ICraftingCPU {

    private long fakeStorage = 0;
    private final NEComputationCluster cluster;
    private final ICraftingPlan plan;
    private final ECOCraftingCPULogic logic = new ECOCraftingCPULogic(this);

    public ECOCraftingCPU(NEComputationCluster cluster, ICraftingPlan plan) {
        this.cluster = cluster;
        this.plan = plan;
    }
    @Override
    public boolean isBusy() {
        return logic.hasJob();
    }

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
        if (this.plan == null) {
            return;
        }

        logic.cancel();
        this.cluster.cancelJob(plan);
    }

    @Override
    public long getAvailableStorage() {
        return this.plan != null ? this.plan.bytes() : fakeStorage;
    }

    @Override
    public int getCoProcessors() {
        return cluster.getCoProcessors();
    }

    @Override
    public @Nullable Component getName() {
        return cluster.getName();
    }

    @Override
    public CpuSelectionMode getSelectionMode() {
        return cluster.getSelectionMode();
    }

    public void markDirty() {
        for (ECOComputationThreadingCoreBlockEntity threadingCore : cluster.getThreadingCores()) {
            threadingCore.saveChanges();
        }
    }

    public boolean isActive() {
        // TODO: invoke cluster isActive
        return false;
    }

    public void deactivate() {
        // TODO: invoke cluster isActive
    }

    public Level getLevel() {
        return cluster.getController().getLevel();
    }

    @Nullable
    public IGrid getGrid() {
        IGridNode gridNode = cluster.getController().getGridNode();
        return gridNode != null ? gridNode.getGrid() : null;
    }

    public IActionSource getActionSource() {
        return cluster.getActionSource();
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        logic.writeToNBT(data, registries);
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        logic.readFromNBT(data, registries);
    }
}
