package com.lowdragmc.lowdraglib2.syncdata.holder.blockentity;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib2.syncdata.holder.ISyncMangedHolder;
import com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface ISyncPersistRPCBlockEntity extends ISyncMangedHolder, IUIHolder {
    default IManagedStorage getRootStorage() {
        return null;
    }

    @Override
    default ModularUI createUI(Player player) {
        var ui = new ModularUI(176, 96, this, player)
            .background(new ResourceBorderTexture(
                "neoecoae:textures/gui/background.png",
                16,
                16,
                4,
                4
            ));
        ui.widget(new LabelWidget(
            8,
            8,
            Component.translatable("gui.neoecoae.migration_ui.no_ldlib1_ui")
        ).setTextColor(0xFFFFFFFF));
        return ui;
    }

    @Override
    default boolean isInvalid() {
        return this instanceof BlockEntity blockEntity && blockEntity.isRemoved();
    }

    @Override
    default boolean isRemote() {
        return this instanceof BlockEntity blockEntity
            && blockEntity.getLevel() != null
            && blockEntity.getLevel().isClientSide();
    }

    @Override
    default void markAsDirty() {
        if (this instanceof BlockEntity blockEntity) {
            blockEntity.setChanged();
        }
    }
}
