package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class NEComputationCluster extends NECluster<NEComputationCluster> implements ICraftingCPU {

    public NEComputationCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public @Nullable CraftingJobStatus getJobStatus() {
        return null;
    }

    @Override
    public void cancelJob() {

    }

    @Override
    public long getAvailableStorage() {
        return 0;
    }

    @Override
    public int getCoProcessors() {
        return 0;
    }

    @Override
    public @Nullable Component getName() {
        return null;
    }

    @Override
    public CpuSelectionMode getSelectionMode() {
        return null;
    }

    @Override
    public void updateStatus(boolean updateGrid) {

    }

    @Override
    public void destroy() {
    }

    @Override
    public boolean isDestroyed() {
        return false;
    }
}
