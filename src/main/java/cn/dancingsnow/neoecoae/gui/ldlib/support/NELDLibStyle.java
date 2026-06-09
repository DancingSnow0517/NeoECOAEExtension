package cn.dancingsnow.neoecoae.gui.ldlib.support;

import appeng.client.gui.Icon;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class NELDLibStyle {
    public static final int DARK_PANEL_OUTER = 0xFF17141E;
    public static final int DARK_PANEL_MIDDLE = 0xFF2B2834;
    public static final int DARK_PANEL_INNER = 0xFF665F6D;
    public static final int DARK_PANEL_LIGHT_EDGE = 0xFFC9C3D6;

    public static final int DARK_TEXT_PRIMARY = 0xFFD6D0E0;
    public static final int DARK_TEXT_VALUE = 0xFF8377FF;
    public static final int DARK_TEXT_USED = 0xFF00FC00;
    public static final int DARK_TEXT_MUTED = 0xFFAAA4B2;
    public static final int DARK_TEXT_SUCCESS = 0xFF6CFFA0;
    public static final int DARK_TEXT_WARNING = 0xFFFFD65A;
    public static final int DARK_TEXT_BLUE = 0xFF3FD6FF;
    public static final int DARK_TEXT_ORANGE = 0xFFFF9A3D;
    public static final int DARK_TEXT_ERROR = 0xFFFF6A75;

    public static final int HOVER_OVERLAY = 0x40FFFFFF;

    private static final IGuiTexture AE_TOOLBAR_BUTTON = (graphics, mouseX, mouseY, x, y, width, height) -> {
        drawAeSprite(graphics, Icon.TOOLBAR_BUTTON_BACKGROUND, Math.round(x), Math.round(y), width, height);
    };

    private static final IGuiTexture AE_TAB_BUTTON = (graphics, mouseX, mouseY, x, y, width, height) -> {
        Icon bg = isMouseIn(Math.round(x), Math.round(y), width, height, mouseX, mouseY)
                ? Icon.TAB_BUTTON_BACKGROUND_FOCUS
                : Icon.TAB_BUTTON_BACKGROUND;
        drawAeSprite(graphics, bg, Math.round(x), Math.round(y), width, height);
    };

    private NELDLibStyle() {}

    public static IGuiTexture aeToolbarButton() {
        return AE_TOOLBAR_BUTTON;
    }

    public static IGuiTexture aeTabButton() {
        return AE_TAB_BUTTON;
    }

    public static IGuiTexture darkInsetButton(boolean selected) {
        return darkInsetButton(() -> selected);
    }

    public static IGuiTexture darkInsetButton(BooleanSupplier selectedSupplier) {
        return (graphics, mouseX, mouseY, x, y, width, height) -> {
            int ix = Math.round(x);
            int iy = Math.round(y);
            boolean hover = mouseX >= ix && mouseX < ix + width && mouseY >= iy && mouseY < iy + height;
            drawInsetButton(graphics, ix, iy, width, height, hover, false, selectedSupplier.getAsBoolean());
        };
    }

    public static void drawDarkInsetRect(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFCBCCD4);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0D0D11);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF85818D);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF0D0D11);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFF47434F);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xFF605A66);
    }

    public static void drawTinyInsetRect(GuiGraphics g, int x, int y, int w, int h, int innerColor) {
        g.fill(x, y, x + w, y + h, DARK_PANEL_LIGHT_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, DARK_PANEL_OUTER);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, innerColor);
    }

    public static void drawInsetButton(
            GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean pressed, boolean selected) {
        int edge = hover ? 0xFFDAD5E8 : DARK_PANEL_LIGHT_EDGE;
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
            g.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, DARK_TEXT_SUCCESS);
        }
    }

    public static void drawDarkSlot(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, DARK_PANEL_MIDDLE);
        g.fill(x, y, x + size, y + 1, 0xFF0D0D11);
        g.fill(x, y, x + 1, y + size, 0xFF0D0D11);
        g.fill(x, y + size - 1, x + size, y + size, DARK_PANEL_LIGHT_EDGE);
        g.fill(x + size - 1, y, x + size, y + size, DARK_PANEL_LIGHT_EDGE);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF4B4653);
        g.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0xFF5A5460);
    }

    public static void drawTexturedModuleSlot(
            GuiGraphics g,
            int x,
            int y,
            int size,
            ResourceLocation baseTexture,
            ResourceLocation overlayTexture,
            boolean active) {
        drawDarkInsetRect(g, x, y, size, size);
        int innerX = x + 2;
        int innerY = y + 2;
        int innerSize = size - 4;
        g.fill(innerX, innerY, innerX + innerSize, innerY + innerSize, 0xAA17141E);
        if (baseTexture != null) {
            g.blit(baseTexture, innerX, innerY, innerSize, innerSize, 0, 0, 16, 16, 16, 16);
        }
        if (overlayTexture != null) {
            g.blit(overlayTexture, innerX, innerY, innerSize, innerSize, 0, 0, 16, 16, 16, 16);
        }
        if (!active) {
            g.fill(innerX, innerY, innerX + innerSize, innerY + innerSize, 0x99000000);
        }
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

    public static void drawCenteredString(GuiGraphics g, Font font, String text, int x, int y, int w, int color) {
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

    public static int usedValueColor(long used, long max) {
        if (used <= 0 || max <= 0) {
            return DARK_TEXT_USED;
        }
        double pct = (double) used / (double) max;
        if (pct >= 1.0D) {
            return DARK_TEXT_ERROR;
        }
        if (pct >= 0.9D) {
            return DARK_TEXT_ORANGE;
        }
        if (pct >= 0.75D) {
            return DARK_TEXT_WARNING;
        }
        return DARK_TEXT_USED;
    }

    public static int metricColor(int accentColor, long max, double pct) {
        if (max <= 0) {
            return DARK_TEXT_MUTED;
        }
        return lerpColor(darken(accentColor, 0.72D), accentColor, Mth.clamp(pct + 0.2D, 0.0D, 1.0D));
    }

    public static int darken(int color, double factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (int) (((color >>> 16) & 0xFF) * factor);
        int g = (int) (((color >>> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int lerpColor(int start, int end, double t) {
        double safeT = Mth.clamp(t, 0.0D, 1.0D);
        int a = (int) Mth.lerp(safeT, (start >>> 24) & 0xFF, (end >>> 24) & 0xFF);
        int r = (int) Mth.lerp(safeT, (start >>> 16) & 0xFF, (end >>> 16) & 0xFF);
        int g = (int) Mth.lerp(safeT, (start >>> 8) & 0xFF, (end >>> 8) & 0xFF);
        int b = (int) Mth.lerp(safeT, start & 0xFF, end & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
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
