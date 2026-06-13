package cn.dancingsnow.neoecoae.client.gui.ldlib;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class NELDLibClientStyle {
    private NELDLibClientStyle() {}

    public static void drawDarkInsetRect(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFCBCCD4);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0D0D11);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF85818D);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF0D0D11);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFF47434F);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xFF605A66);
    }

    public static void drawTinyInsetRect(GuiGraphics g, int x, int y, int w, int h, int innerColor) {
        g.fill(x, y, x + w, y + h, NELDLibStyle.DARK_PANEL_LIGHT_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, NELDLibStyle.DARK_PANEL_OUTER);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, innerColor);
    }

    public static void drawInsetButton(
            GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean pressed, boolean selected) {
        int edge = hover ? 0xFFDAD5E8 : NELDLibStyle.DARK_PANEL_LIGHT_EDGE;
        int mid = selected ? 0xFF3B3445 : 0xFF47434F;
        int inner = selected ? 0xFF282232 : 0xFF5A5460;

        if (pressed) {
            mid = 0xFF302A38;
            inner = 0xFF211C29;
        }

        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0D0D11);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, mid);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, inner);

        if (!pressed) {
            g.fill(x + 3, y + 3, x + w - 3, y + 4, 0x55FFFFFF);
            g.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, 0x99000000);
        } else {
            g.fill(x + 3, y + 3, x + w - 3, y + 4, 0x99000000);
        }
        if (selected) {
            g.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, NELDLibStyle.DARK_TEXT_SUCCESS);
        }
    }

    public static void drawDarkSlot(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, NELDLibStyle.DARK_PANEL_MIDDLE);
        g.fill(x, y, x + size, y + 1, 0xFF0D0D11);
        g.fill(x, y, x + 1, y + size, 0xFF0D0D11);
        g.fill(x, y + size - 1, x + size, y + size, NELDLibStyle.DARK_PANEL_LIGHT_EDGE);
        g.fill(x + size - 1, y, x + size, y + size, NELDLibStyle.DARK_PANEL_LIGHT_EDGE);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF4B4653);
        g.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0xFF5A5460);
    }

    public static int drawSegment(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
        return font.width(text);
    }

    public static int drawSegment(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, Component.literal(text), x, y, color, false);
        return font.width(text);
    }

    public static void drawCentered(GuiGraphics g, Font font, Component text, int x, int y, int w, int color) {
        g.drawString(font, text, x + (w - font.width(text)) / 2, y, color, false);
    }

    public static void drawRight(GuiGraphics g, Font font, Component text, int rightX, int y, int color) {
        g.drawString(font, text, rightX - font.width(text), y, color, false);
    }

    public static void drawCenteredFitted(GuiGraphics g, Font font, Component text, int x, int y, int w, int color) {
        int textWidth = font.width(text);
        int maxWidth = Math.max(1, w - 4);
        if (textWidth <= maxWidth) {
            drawCentered(g, font, text, x, y, w, color);
            return;
        }
        float scale = Math.max(0.55F, (float) maxWidth / (float) textWidth);
        g.pose().pushPose();
        g.pose().translate(x + w / 2.0F, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, -textWidth / 2, 0, color, false);
        g.pose().popPose();
    }

    public static void drawCenteredScaledString(
            GuiGraphics g, Font font, String text, int boxX, int boxY, int boxW, int boxH, int color, float maxScale) {
        float scale = Math.min(maxScale, Math.max(0.55F, (float) (boxW - 4) / Math.max(1.0F, font.width(text))));
        float scaledTextW = font.width(text) * scale;
        float scaledTextH = font.lineHeight * scale;
        float drawX = boxX + (boxW - scaledTextW) / 2.0F;
        float drawY = boxY + (boxH - scaledTextH) / 2.0F;

        g.pose().pushPose();
        g.pose().translate(drawX, drawY, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, Component.literal(text), 0, 0, color, false);
        g.pose().popPose();
    }

    public static void drawAeToolbarButton(GuiGraphics graphics, int x, int y, int width, int height) {
        drawAeSprite(graphics, Icon.TOOLBAR_BUTTON_BACKGROUND, x, y, width, height);
    }

    public static void drawAeToolbarButton(
            GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height, boolean pressed) {
        drawAeToolbarButton(graphics, x, y, width, height);
        drawHoverOverlay(graphics, mouseX, mouseY, x, y, width, height, pressed);
    }

    public static void drawAeTabButton(
            GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        Icon bg = isMouseIn(x, y, width, height, mouseX, mouseY)
                ? Icon.TAB_BUTTON_BACKGROUND_FOCUS
                : Icon.TAB_BUTTON_BACKGROUND;
        drawAeSprite(graphics, bg, x, y, width, height);
    }

    public static void drawHoverOverlay(
            GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height, boolean pressed) {
        if (isMouseIn(x, y, width, height, mouseX, mouseY)) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, pressed ? 0x38000000 : 0x28FFFFFF);
        }
    }

    private static void drawAeSprite(GuiGraphics graphics, Icon icon, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(
                Icon.TEXTURE,
                x,
                y,
                width,
                height,
                icon.x,
                icon.y,
                icon.width,
                icon.height,
                Icon.TEXTURE_WIDTH,
                Icon.TEXTURE_HEIGHT);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static boolean isMouseIn(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
