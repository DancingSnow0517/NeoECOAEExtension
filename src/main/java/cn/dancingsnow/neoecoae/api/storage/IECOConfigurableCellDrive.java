package cn.dancingsnow.neoecoae.api.storage;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** A cell installation that can be configured without depending on an ECO block entity class. */
public interface IECOConfigurableCellDrive {
    BlockPos getBlockPos();

    boolean isRemoved();

    @Nullable ItemStack getCellStack();

    /** Changes whenever the cell is replaced or the installation is reloaded. Synchronize to the UI. */
    UUID getCellInteractionId();

    /** False while migration or ownership rules prevent changing the installed cell. */
    boolean canExtractCell();

    int getConfiguredCellPriority();

    /**
     * Called on the owning server thread after authorization. Persist and synchronize the setting,
     * invalidate cached cell settings and refresh affected AE2 storage mounts.
     */
    void setConfiguredCellPriority(int priority);
}
