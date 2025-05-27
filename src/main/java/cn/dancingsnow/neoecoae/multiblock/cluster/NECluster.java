package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.me.cluster.IAECluster;
import appeng.me.helpers.MachineSource;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class NECluster<T extends NECluster<T>> implements IAECluster {
    private final BlockPos boundMin;
    private final BlockPos boundMax;
    private final List<NEBlockEntity<T, ?>> blockEntities = new ArrayList<>();
    private MachineSource machineSource = null;

    public NECluster(BlockPos boundMin, BlockPos boundMax) {
        this.boundMin = boundMin;
        this.boundMax = boundMax;
    }

    @Override
    public BlockPos getBoundsMin() {
        return boundMin;
    }

    @Override
    public BlockPos getBoundsMax() {
        return boundMax;
    }

    @Override
    public void destroy() {

    }

    public void updateFormed(boolean formed) {

    }

    public void addBlockEntity(NEBlockEntity<T, ?> blockEntity) {
        if (blockEntity.isCoreBlock()) {
            this.machineSource = new MachineSource(blockEntity);
        }
        blockEntity.saveChanges();
        this.blockEntities.add(blockEntity);
    }

    @Override
    public Iterator<? extends NEBlockEntity<T, ?>> getBlockEntities() {
        return blockEntities.listIterator();
    }
}
