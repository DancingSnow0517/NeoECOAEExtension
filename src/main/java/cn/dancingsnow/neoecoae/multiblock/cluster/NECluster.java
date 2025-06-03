package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.me.cluster.IAECluster;
import appeng.me.cluster.MBCalculator;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class NECluster<T extends NECluster<T>> implements IAECluster {
    private final BlockPos boundMin;
    private final BlockPos boundMax;
    protected final List<NEBlockEntity<T, ?>> blockEntities = new ArrayList<>();

    @Getter
    private boolean destroyed = false;

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

    public void updateFormed(boolean formed) {
        for (NEBlockEntity<T, ?> be : this.blockEntities) {
            be.setFormed(formed);
        }
    }

    public boolean shouldCasingHide(NEBlockEntity<T, ?> blockEntity) {
        return true;
    }

    public void addBlockEntity(NEBlockEntity<T, ?> blockEntity) {
        blockEntity.saveChanges();
        this.blockEntities.add(blockEntity);
    }

    @Override
    @MustBeInvokedByOverriders
    public Iterator<? extends NEBlockEntity<T, ?>> getBlockEntities() {
        return blockEntities.listIterator();
    }

    @Override
    @MustBeInvokedByOverriders
    public void updateStatus(boolean updateGrid) {
        for (NEBlockEntity<T, ?> be : blockEntities) {
            be.updateState(updateGrid);
        }
    }

    @Override
    @MustBeInvokedByOverriders
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        boolean ownsModification = !MBCalculator.isModificationInProgress();
        if (ownsModification) {
            MBCalculator.setModificationInProgress(this);
        }
        try {
            for (NEBlockEntity<T, ?> blockEntity : blockEntities) {
                blockEntity.updateCluster(null);
            }
        } finally {
            MBCalculator.setModificationInProgress(null);
        }
    }
}
