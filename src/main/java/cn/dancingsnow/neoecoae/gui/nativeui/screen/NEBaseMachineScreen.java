package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEBaseMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Base screen for all ECO machine native UIs.
 * <p>
 * Handles background fill and provides common rendering infrastructure.
 * </p>
 *
 * @param <T> the menu type
 */
public abstract class NEBaseMachineScreen<T extends NEBaseMachineMenu>
    extends AbstractContainerScreen<T> {

    protected NEBaseMachineScreen(T menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // Dark panel background
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2A2A3A);
    }
}
