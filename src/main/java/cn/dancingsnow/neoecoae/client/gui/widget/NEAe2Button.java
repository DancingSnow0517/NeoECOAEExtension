package cn.dancingsnow.neoecoae.client.gui.widget;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** AE2 1.21.1 button artwork and text styling for native 1.20.1 screens. */
public final class NEAe2Button extends Button {
    private static final ResourceLocation NORMAL =
        NeoECOAE.id("textures/gui/vendor/ae2/sprites/button.png");
    private static final ResourceLocation HIGHLIGHTED =
        NeoECOAE.id("textures/gui/vendor/ae2/sprites/button_highlighted.png");
    private static final ResourceLocation DISABLED =
        NeoECOAE.id("textures/gui/vendor/ae2/sprites/button_disabled.png");

    public NEAe2Button(Component message, OnPress onPress) {
        super(0, 0, 0, 0, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation texture = !active ? DISABLED : isHovered() ? HIGHLIGHTED : NORMAL;
        graphics.setColor(1, 1, 1, alpha);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        drawBackground(graphics, texture);
        graphics.setColor(1, 1, 1, 1);

        int color = !active ? 0x413F54 : isHovered() ? 0x517497 : 0xF2F2F2;
        int yOffset = !active ? -1 : isHovered() ? 0 : 1;
        renderLabel(graphics, Minecraft.getInstance().font, color | Mth.ceil(alpha * 255) << 24, yOffset);
    }

    private void drawBackground(GuiGraphics graphics, ResourceLocation texture) {
        // The 1.20.1 built-in nine-slice helper assumes a 256 x 256 texture.
        // These standalone sprites are 200 x 20, with a three-pixel border.
        for (int row = 0; row < 3; row++) {
            int sourceY = row == 0 ? 0 : row == 1 ? 3 : 17;
            int sourceHeight = row == 1 ? 14 : 3;
            int y = getY() + (row == 0 ? 0 : row == 1 ? 3 : getHeight() - 3);
            int height = row == 1 ? getHeight() - 6 : 3;
            for (int col = 0; col < 3; col++) {
                int sourceX = col == 0 ? 0 : col == 1 ? 3 : 197;
                int sourceWidth = col == 1 ? 194 : 3;
                int x = getX() + (col == 0 ? 0 : col == 1 ? 3 : getWidth() - 3);
                int width = col == 1 ? getWidth() - 6 : 3;
                graphics.blit(texture, x, y, width, height,
                    sourceX, sourceY, sourceWidth, sourceHeight, 200, 20);
            }
        }
    }

    private void renderLabel(GuiGraphics graphics, Font font, int color, int yOffset) {
        int left = getX() + 2;
        int right = getX() + getWidth() - 2;
        int textWidth = font.width(getMessage());
        int textY = getY() + (getHeight() - 9) / 2 + 1 - yOffset;
        if (textWidth <= right - left) {
            graphics.drawString(font, getMessage(), getX() + (getWidth() - textWidth) / 2, textY, color, false);
            return;
        }

        // Keep long CPU names readable without painting over the button border.
        int overflow = textWidth - (right - left);
        double period = Math.max(overflow * 0.5, 3);
        double phase = Util.getMillis() / 1000.0 / period * Math.PI * 2;
        double progress = Math.sin(Math.PI / 2 * Math.cos(phase)) / 2 + 0.5;
        int scroll = (int) Mth.lerp(progress, 0, overflow);
        graphics.enableScissor(left, getY(), right, getY() + getHeight());
        graphics.drawString(font, getMessage(), left - scroll, textY, color, false);
        graphics.disableScissor();
    }
}
