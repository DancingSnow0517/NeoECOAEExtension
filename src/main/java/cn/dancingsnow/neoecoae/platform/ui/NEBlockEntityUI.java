package cn.dancingsnow.neoecoae.platform.ui;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Block-entity UI lifecycle shared by LDLib 1.0.33.b and newer releases. */
public interface NEBlockEntityUI extends IUIHolder {
    default BlockEntity self() {
        return (BlockEntity) this;
    }

    @Override
    default boolean isInvalid() {
        return self().isRemoved();
    }

    @Override
    default boolean isRemote() {
        var level = self().getLevel();
        return level == null ? LDLib.isRemote() : level.isClientSide;
    }

    @Override
    default void markAsDirty() {
        self().setChanged();
    }
}
