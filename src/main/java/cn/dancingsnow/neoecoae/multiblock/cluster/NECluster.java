package cn.dancingsnow.neoecoae.multiblock.cluster;

import appeng.me.cluster.IAECluster;
import appeng.me.cluster.MBCalculator;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineCasingBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class NECluster<T extends NECluster<T>> implements IAECluster {
    private final BlockPos boundMin;
    private final BlockPos boundMax;
    protected final List<BlockEntity> blockEntities = new ArrayList<>();

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
        for (BlockEntity blockEntity : this.blockEntities) {
            if (blockEntity instanceof NEBlockEntity<?, ?> be) {
                be.setFormed(formed);
            }
        }
    }

    public boolean shouldCasingHide(NEBlockEntity<T, ?> blockEntity) {
        if (!(blockEntity instanceof ECOMachineCasingBlockEntity)) {
            return false;
        }
        if (hideAllCasingsWhenFormed()) {
            return true;
        }
        BlockPos origin = getCasingHideOrigin();
        return origin != null
            && blockEntity.getBlockPos().distSqr(origin) <= 3;
    }

    protected boolean hideAllCasingsWhenFormed() {
        return false;
    }

    protected @Nullable BlockPos getCasingHideOrigin() {
        return null;
    }

    public boolean isNetworkMode() {
        return false;
    }

    public int getNetworkMultiplier() {
        return 1;
    }

    public boolean shouldCasingRenderInClassic(NEBlockEntity<T, ?> blockEntity) {
        return false;
    }

    public void addBlockEntity(NEBlockEntity<T, ?> blockEntity) {
        blockEntity.saveChanges();
        this.blockEntities.add(blockEntity);
    }

    public void addBlockEntity(BlockEntity blockEntity) {
        this.blockEntities.add(blockEntity);
    }

    @Override
    @MustBeInvokedByOverriders
    public Iterator<? extends BlockEntity> getBlockEntities() {
        return blockEntities.listIterator();
    }

    @Override
    @MustBeInvokedByOverriders
    public void updateStatus(boolean updateGrid) {
        for (BlockEntity blockEntity : blockEntities) {
            if (blockEntity instanceof NEBlockEntity<?, ?> be) {
                be.updateState(updateGrid);
            }
        }
    }

    /** Physical block removal. Recalculation and unload must continue to use destroy(). */
    public void breakCluster() {
        destroy();
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
            for (BlockEntity blockEntity : blockEntities) {
                if (blockEntity instanceof NEBlockEntity<?, ?> neBlockEntity) {
                    neBlockEntity.updateCluster(null);
                }
            }
        } finally {
            if (ownsModification) {
                MBCalculator.setModificationInProgress(null);
            }
        }
    }
}
