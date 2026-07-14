package cn.dancingsnow.neoecoae.client.gui.ldlib.host;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import net.minecraft.client.gui.GuiGraphics;

/** LDLib1 adapters for the LDLib2-style textures shared by ECO host screens. */
public final class NEHostTextures {
    private static final IGuiTexture PANEL_BORDER =
            new ResourceBorderTexture("neoecoae:textures/gui/storage_host_panel_border.png", 16, 16, 6, 6);
    private static final IGuiTexture SCROLLBAR_TRACK =
            new ResourceBorderTexture("neoecoae:textures/gui/card_background.png", 16, 16, 3, 3);
    private static final IGuiTexture SCROLLBAR_THUMB =
            new ResourceBorderTexture("neoecoae:textures/gui/button.png", 20, 20, 2, 2);

    public static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        PANEL_BORDER.draw(graphics, mouseX, mouseY, x, y, width, height);
    }

    public static void drawScrollbarTrack(
            GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        SCROLLBAR_TRACK.draw(graphics, mouseX, mouseY, x, y, width, height);
    }

    public static void drawScrollbarThumb(
            GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        SCROLLBAR_THUMB.draw(graphics, mouseX, mouseY, x, y, width, height);
    }

    private NEHostTextures() {}
}
