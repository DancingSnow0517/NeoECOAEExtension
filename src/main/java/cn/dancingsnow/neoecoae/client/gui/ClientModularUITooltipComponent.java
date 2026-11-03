package cn.dancingsnow.neoecoae.client.gui;

import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.utils.ModularUITooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/** Forge 1.20.1 tooltip adapter; content remains in the shared retained UI renderer. */
public final class ClientModularUITooltipComponent implements ClientTooltipComponent {
    private final UIElement content;

    public ClientModularUITooltipComponent(ModularUITooltipComponent tooltip) {
        content = tooltip.content();
        content.arrange();
    }

    @Override public int getHeight() { return Math.round(content.getSizeHeight()); }
    @Override public int getWidth(Font font) { return Math.round(content.getSizeWidth()); }

    @Override public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            content.drawInBackground(graphics, 0, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }
}
