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
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ECOCraftingCPU implements ICraftingCPU {

    private long fakeStorage = 0;
    private final NEComputationCluster cluster;
    @Getter
    private final ICraftingPlan plan;
    @Getter
    private final ECOCraftingCPULogic logic = new ECOCraftingCPULogic(this);
    @Getter
    private final ECOComputationThreadingCoreBlockEntity owner;

    public ECOCraftingCPU(NEComputationCluster cluster, ICraftingPlan plan, ECOComputationThreadingCoreBlockEntity owner) {
        this.cluster = cluster;
        this.plan = plan;
        this.owner = owner;
    }

    public ECOCraftingCPU(NEComputationCluster cluster, long fakeStorage) {
        this.cluster = cluster;
        this.plan = null;
        this.fakeStorage = fakeStorage;
        this.owner = null;
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
        return cluster.getCPUAccelerators();
    }

    @Override
    public @Nullable Component getName() {
        return Component.literal("123456");
    }

    @Override
    public CpuSelectionMode getSelectionMode() {
        return cluster.getSelectionMode();
    }

    public void markDirty() {
        if (this.owner != null){
            this.owner.saveChanges();
        }
    }

    public boolean isActive() {
        return cluster.isActive();
    }

    public void deactivate() {
        this.cluster.deactivate(this.plan);
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
