package cn.dancingsnow.neoecoae.gui.nativeui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * A minimal nine-slice (九宫格) renderer for GUI panels and buttons
 * that uses only vanilla Minecraft / Forge APIs.
 * <p>
 * No LDLib dependency. All textures are drawn via
 * {@link GuiGraphics#blit(ResourceLocation, int, int, int, int, int, int, int, int, int, int)}.
 * </p>
 */
public final class NENineSliceRenderer {

    private NENineSliceRenderer() {
    }

    /**
     * Draws a nine-slice panel.
     *
     * @param guiGraphics the graphics context
     * @param texture     the texture resource
     * @param x           screen x
     * @param y           screen y
     * @param width       target width
     * @param height      target height
     * @param texWidth    texture total width (e.g. 16)
     * @param texHeight   texture total height (e.g. 16)
     * @param left        left border in texture px
     * @param top         top border in texture px
     * @param right       right border in texture px
     * @param bottom      bottom border in texture px
     */
    public static void drawPanel(GuiGraphics guiGraphics, ResourceLocation texture,
                                  int x, int y, int width, int height,
                                  int texWidth, int texHeight,
                                  int left, int top, int right, int bottom) {
        int centerW = width - left - right;
        int centerH = height - top - bottom;
        int texCenterW = texWidth - left - right;
        int texCenterH = texHeight - top - bottom;

        RenderSystem.enableBlend();
        // Top-left corner (no stretch)
        blit(guiGraphics, texture, x, y, left, top, 0, 0, left, top, texWidth, texHeight);
        // Top edge (stretch horizontally)
        blit(guiGraphics, texture, x + left, y, centerW, top,
            left, 0, texCenterW, top, texWidth, texHeight);
        // Top-right corner
        blit(guiGraphics, texture, x + left + centerW, y, right, top,
            left + texCenterW, 0, right, top, texWidth, texHeight);
        // Left edge (stretch vertically)
        blit(guiGraphics, texture, x, y + top, left, centerH,
            0, top, left, texCenterH, texWidth, texHeight);
        // Center (stretch both)
        blit(guiGraphics, texture, x + left, y + top, centerW, centerH,
            left, top, texCenterW, texCenterH, texWidth, texHeight);
        // Right edge
        blit(guiGraphics, texture, x + left + centerW, y + top, right, centerH,
            left + texCenterW, top, right, texCenterH, texWidth, texHeight);
        // Bottom-left corner
        blit(guiGraphics, texture, x, y + top + centerH, left, bottom,
            0, top + texCenterH, left, bottom, texWidth, texHeight);
        // Bottom edge
        blit(guiGraphics, texture, x + left, y + top + centerH, centerW, bottom,
            left, top + texCenterH, texCenterW, bottom, texWidth, texHeight);
        // Bottom-right corner
        blit(guiGraphics, texture, x + left + centerW, y + top + centerH, right, bottom,
            left + texCenterW, top + texCenterH, right, bottom, texWidth, texHeight);
    }

    /**
     * Draws a textured button using the appropriate state texture.
     */
    public static void drawButton(GuiGraphics guiGraphics, ResourceLocation texture,
                                   int x, int y, int width, int height,
                                   int texWidth, int texHeight,
                                   int left, int top, int right, int bottom) {
        drawPanel(guiGraphics, texture, x, y, width, height, texWidth, texHeight, left, top, right, bottom);
    }

    /**
     * Draws a single slot background (no nine-slice, simple blit).
     */
    public static void drawSlot(GuiGraphics guiGraphics, ResourceLocation texture,
                                 int x, int y, int texWidth, int texHeight) {
        guiGraphics.blit(texture, x, y, 0, 0, texWidth, texHeight, texWidth, texHeight);
    }

    private static void blit(GuiGraphics guiGraphics, ResourceLocation texture,
                              int x, int y, int width, int height,
                              int u, int v, int uWidth, int vHeight,
                              int texWidth, int texHeight) {
        guiGraphics.blit(texture, x, y, width, height, u, v, uWidth, vHeight, texWidth, texHeight);
    }
}
