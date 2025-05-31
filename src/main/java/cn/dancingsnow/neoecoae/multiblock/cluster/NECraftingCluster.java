package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.BlockPos;

import java.util.List;

public class NECraftingCluster extends NECluster<NECraftingCluster> {

    public NECraftingCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }


    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return false;
    }

    public boolean isBusy() {
        return false;
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
