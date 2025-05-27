package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import net.minecraft.core.BlockPos;

public class NEStorageCluster extends NECluster<NEStorageCluster> implements IStorageProvider {

    public NEStorageCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {

    }

    @Override
    public void updateStatus(boolean updateGrid) {

    }

    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    public boolean isDestroyed() {
        return false;
    }
}
