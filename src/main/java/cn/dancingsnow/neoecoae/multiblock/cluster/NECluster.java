package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.me.cluster.IAECluster;
import appeng.me.cluster.MBCalculator;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

public abstract class NECluster<T extends NECluster<T>> implements IAECluster {
    private final BlockPos boundMin;
    private final BlockPos boundMax;
    protected final List<NEBlockEntity<T, ?>> blockEntities = new ArrayList<>();

    @Getter
    private boolean destroyed = false;

    @Getter
    private boolean mirrored = false;

    /** True when this physical host was formed with a network switch installed. */
    @Getter
    @Setter
    private boolean networkMode;

    /** True for the high-energy switch variant. */
    @Getter
    @Setter
    private boolean highEnergyNetworkMode;

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

    public void onStructureBroken() {}

    public void setMirrored(boolean mirrored) {
        this.mirrored = mirrored;
    }

    public int getNetworkMultiplier() {
        return 1;
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
        if (networkMode) {
            NELogicalNetworkManager.refresh(this);
        }
    }

    @Override
    @MustBeInvokedByOverriders
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;
        NELogicalNetworkManager.detachBeforeDestroy(this);
        boolean ownsModification = !MBCalculator.isModificationInProgress();
        if (ownsModification) {
            MBCalculator.setModificationInProgress(this);
        }
        try {
            for (NEBlockEntity<T, ?> blockEntity : blockEntities) {
                blockEntity.updateCluster(null);
            }
        } finally {
            if (ownsModification) {
                MBCalculator.setModificationInProgress(null);
            }
        }
    }
}
