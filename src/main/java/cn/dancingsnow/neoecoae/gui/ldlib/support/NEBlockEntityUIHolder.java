package cn.dancingsnow.neoecoae.gui.ldlib.support;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface NEBlockEntityUIHolder extends IUIHolder {
    default BlockEntity self() {
        return (BlockEntity) this;
    }

    @Override
    default boolean isInvalid() {
        return self().isRemoved();
    }

    @Override
    default boolean isRemote() {
        Level level = self().getLevel();
        return level != null && level.isClientSide;
    }

    @Override
    default void markAsDirty() {
        self().setChanged();
    }
}
