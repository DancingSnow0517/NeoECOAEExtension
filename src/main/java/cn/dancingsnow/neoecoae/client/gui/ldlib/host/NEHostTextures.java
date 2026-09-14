package cn.dancingsnow.neoecoae.client.gui.ldlib.host;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** LDLib1 adapters for the LDLib2-style textures shared by ECO host screens. */
public final class NEHostTextures {
    private static final ResourceLocation HOST_BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/background.png");
    private static final ResourceLocation ECO_STATES =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/ae2_121/eco_states.png");
    private static final ResourceLocation ECO_BUTTON =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/ae2_121/eco_button.png");
    private static final ResourceLocation ECO_BUTTON_HIGHLIGHTED =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/ae2_121/eco_button_highlighted.png");
    private static final ResourceLocation ECO_BUTTON_DISABLED =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/ae2_121/eco_button_disabled.png");
    private static final IGuiTexture PANEL_BORDER =
            new ResourceBorderTexture("neoecoae:textures/gui/storage_host_panel_border.png", 16, 16, 6, 6);
    private static final IGuiTexture SCROLLBAR_TRACK =
            new ResourceBorderTexture("neoecoae:textures/gui/card_background.png", 16, 16, 3, 3);
    private static final IGuiTexture SCROLLBAR_THUMB =
            new ResourceBorderTexture("neoecoae:textures/gui/button.png", 20, 20, 2, 2);

    public static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        PANEL_BORDER.draw(graphics, mouseX, mouseY, x, y, width, height);
    }

    public static void drawHostBackground(GuiGraphics graphics, int x, int y, int width, int height) {
        drawNineSlice(graphics, HOST_BACKGROUND, x, y, width, height, 16, 16, 2, 2, 2, 4);
    }

    public static void drawEcoButton(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            boolean hovered,
            boolean pressed,
            boolean active) {
        ResourceLocation texture =
                !active ? ECO_BUTTON_DISABLED : hovered || pressed ? ECO_BUTTON_HIGHLIGHTED : ECO_BUTTON;
        if (width == 16 && height == 16) {
            graphics.blit(ECO_STATES, x - 1, y, hovered || pressed ? 212 : 176, 128, 18, 20, 256, 256);
            return;
        }
        drawNineSlice(graphics, texture, x, y, width, height, 200, 20, 3, 3, 3, 3);
    }

    private static void drawNineSlice(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            int textureWidth,
            int textureHeight,
            int left,
            int top,
            int right,
            int bottom) {
        for (int row = 0; row < 3; row++) {
            int sourceY = row == 0 ? 0 : row == 1 ? top : textureHeight - bottom;
            int sourceHeight = row == 0 ? top : row == 1 ? textureHeight - top - bottom : bottom;
            int destY = y + (row == 0 ? 0 : row == 1 ? top : height - bottom);
            int destHeight = row == 0 ? top : row == 1 ? height - top - bottom : bottom;
            for (int col = 0; col < 3; col++) {
                int sourceX = col == 0 ? 0 : col == 1 ? left : textureWidth - right;
                int sourceWidth = col == 0 ? left : col == 1 ? textureWidth - left - right : right;
                int destX = x + (col == 0 ? 0 : col == 1 ? left : width - right);
                int destWidth = col == 0 ? left : col == 1 ? width - left - right : right;
                graphics.blit(
                        texture,
                        destX,
                        destY,
                        destWidth,
                        destHeight,
                        sourceX,
                        sourceY,
                        sourceWidth,
                        sourceHeight,
                        textureWidth,
                        textureHeight);
            }
        }
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
