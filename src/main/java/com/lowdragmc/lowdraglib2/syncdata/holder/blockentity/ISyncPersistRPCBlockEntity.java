package com.lowdragmc.lowdraglib2.syncdata.holder.blockentity;

import com.lowdragmc.lowdraglib2.syncdata.holder.ISyncMangedHolder;
import com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface ISyncPersistRPCBlockEntity extends ISyncMangedHolder {
    default IManagedStorage getRootStorage() {
        return null;
    }

    default boolean isInvalid() {
        return this instanceof BlockEntity blockEntity && blockEntity.isRemoved();
    }

    default boolean isRemote() {
        return this instanceof BlockEntity blockEntity
            && blockEntity.getLevel() != null
            && blockEntity.getLevel().isClientSide();
    }

    default void markAsDirty() {
        if (this instanceof BlockEntity blockEntity) {
            blockEntity.setChanged();
        }
    }
}
