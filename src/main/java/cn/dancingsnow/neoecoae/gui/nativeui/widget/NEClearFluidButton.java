package cn.dancingsnow.neoecoae.gui.nativeui.widget;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * An 8×8 pixel-drawn clear button for fluid tanks.
 * Draws a thin diagonal 'x' using pixel lines, not a font glyph,
 * so it stays centred in small button sizes.
 */
public class NEClearFluidButton extends Button {

    private static final ResourceLocation TEX_BUTTON = NeoECOAE.id("textures/gui/button.png");
    private static final int TEX_SIZE = 20;

    private final int lineColor;

    public NEClearFluidButton(int x, int y, OnPress onPress) {
        this(x, y, 0xFFFFFFFF, onPress);
    }

    public NEClearFluidButton(int x, int y, int lineColor, OnPress onPress) {
        super(x, y, 8, 8, Component.empty(), onPress, DEFAULT_NARRATION);
        this.lineColor = lineColor;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Button background
        int texY = isHoveredOrFocused() ? TEX_SIZE : 0;
        g.blit(TEX_BUTTON, getX(), getY(), 0, texY, width, height, TEX_SIZE, TEX_SIZE);

        // Thin 'x' centred in 8×8
        int cx = getX();
        int cy = getY();
        // top-left to bottom-right
        g.fill(cx + 1, cy + 1, cx + 2, cy + 2, lineColor);
        g.fill(cx + 2, cy + 2, cx + 3, cy + 3, lineColor);
        g.fill(cx + 3, cy + 3, cx + 4, cy + 4, lineColor);
        g.fill(cx + 4, cy + 4, cx + 5, cy + 5, lineColor);
        g.fill(cx + 5, cy + 5, cx + 6, cy + 6, lineColor);
        g.fill(cx + 6, cy + 6, cx + 7, cy + 7, lineColor);
        // top-right to bottom-left
        g.fill(cx + 6, cy + 1, cx + 7, cy + 2, lineColor);
        g.fill(cx + 5, cy + 2, cx + 6, cy + 3, lineColor);
        g.fill(cx + 4, cy + 3, cx + 5, cy + 4, lineColor);
        g.fill(cx + 3, cy + 4, cx + 4, cy + 5, lineColor);
        g.fill(cx + 2, cy + 5, cx + 3, cy + 6, lineColor);
        g.fill(cx + 1, cy + 6, cx + 2, cy + 7, lineColor);
    }
}
