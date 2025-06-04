package cn.dancingsnow.neoecoae.api.me;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
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

public class ECOCraftingCPU implements ICraftingCPU {

    private long fakeStorage = 0;
    private final NEComputationCluster cluster;
    @Getter
    private ICraftingPlan plan;
    @Getter
    private final ECOCraftingCPULogic logic = new ECOCraftingCPULogic(this);
    @Getter
    private final ECOComputationThreadingCoreBlockEntity owner;
    @Getter
    private final IECOTier tier;

    public ECOCraftingCPU(NEComputationCluster cluster, ICraftingPlan plan, ECOComputationThreadingCoreBlockEntity owner) {
        this.cluster = cluster;
        this.plan = plan;
        this.owner = owner;
        this.tier = owner.getTier();
    }

    public ECOCraftingCPU(NEComputationCluster cluster, long fakeStorage, IECOTier tier) {
        this.cluster = cluster;
        this.plan = null;
        this.fakeStorage = fakeStorage;
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

    private void writeCraftingPlanToNBT(ICraftingPlan plan, CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag outputTag = GenericStack.writeTag(registries, plan.finalOutput());
        tag.put("output", outputTag);
        tag.putLong("bytes", plan.bytes());
        tag.putBoolean("simulation", plan.simulation());
        tag.putBoolean("multiplePaths", plan.multiplePaths());
    }

    private CraftingPlan readCraftingPlanFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        GenericStack output = GenericStack.readTag(registries, tag.getCompound("output"));
        long bytes = tag.getLong("bytes");
        boolean simulation = tag.getBoolean("simulation");
        boolean multiplePaths = tag.getBoolean("multiplePaths");
        return new CraftingPlan(output, bytes, simulation, multiplePaths, null, null, null, null);
    }

    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        logic.writeToNBT(data, registries);
        if (this.plan != null) {
            CompoundTag tag = new CompoundTag();
            writeCraftingPlanToNBT(this.plan, tag, registries);
            data.put("plan", tag);
        }
    }

    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        logic.readFromNBT(data, registries);
        if (data.contains("plan")) {
            CompoundTag tag = data.getCompound("plan");
            this.plan = readCraftingPlanFromNBT(tag, registries);
        }
    }
}
