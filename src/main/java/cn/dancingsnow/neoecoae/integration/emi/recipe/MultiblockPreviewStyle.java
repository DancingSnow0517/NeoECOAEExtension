package cn.dancingsnow.neoecoae.integration.emi.recipe;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

public final class MultiblockPreviewStyle {
    public static final int TEXT_COLOR = 0xFF404040;
    public static final int PANEL_COLOR = 0xFFE3E3E3;
    public static final int PANEL_BORDER = 0xFF4F4F4F;
    public static final int BUTTON_BG = 0xFF8F8F8F;
    public static final int BUTTON_BG_HOVER = 0xFFABABAB;
    public static final int BUTTON_BORDER = 0xFF303030;
    public static final int SLOT_SIZE = 18;

    private MultiblockPreviewStyle() {}

    public static void drawPanel(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, PANEL_COLOR);
        g.fill(0, 0, width, 1, PANEL_BORDER);
        g.fill(0, height - 1, width, height, PANEL_BORDER);
        g.fill(0, 0, 1, height, PANEL_BORDER);
        g.fill(width - 1, 0, width, height, PANEL_BORDER);
    }

    static void drawButton(GuiGraphics g, MultiblockPreviewLayout.Rect rect, String text, int mouseX, int mouseY) {
        drawButton(g, rect.x(), rect.y(), rect.width(), rect.height(), text, mouseX, mouseY);
    }

    public static void drawButton(
            GuiGraphics g, int x, int y, int width, int height, String text, double mouseX, double mouseY) {
        boolean hovered = contains(mouseX, mouseY, x, y, width, height);
        g.fill(x, y, x + width, y + height, BUTTON_BORDER);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, hovered ? BUTTON_BG_HOVER : BUTTON_BG);
        drawCenteredFittedString(g, Component.literal(text), x + 2, y, width - 4, height, 0xFFFFFFFF);
    }

    public static void drawFittedString(GuiGraphics g, Component text, int x, int y, int maxW, int color) {
        drawFittedString(g, text, x, y, maxW, color, 0.78F);
    }

    private static void drawCenteredFittedString(
            GuiGraphics g, Component text, int x, int y, int maxW, int height, int color) {
        Font font = Minecraft.getInstance().font;
        int textW = font.width(text);
        float scale = textW <= maxW ? 1.0F : Math.max(0.75F, maxW / (float) textW);
        Component renderedText = fitText(font, text, maxW, scale);
        int fittedW = Math.round(font.width(renderedText) * scale);
        int textX = x + Math.max(0, (maxW - fittedW) / 2);
        int textY = y + Math.max(0, Math.round((height - font.lineHeight * scale) / 2.0F));
        drawScaledString(g, renderedText, textX, textY, scale, color);
    }

    private static void drawFittedString(
            GuiGraphics g, Component text, int x, int y, int maxW, int color, float minScale) {
        Font font = Minecraft.getInstance().font;
        int textW = font.width(text);
        if (textW <= maxW) {
            g.drawString(font, text, x, y, color, false);
            return;
        }

        float scale = Math.max(minScale, maxW / (float) textW);
        Component renderedText = fitText(font, text, maxW, scale);
        drawScaledString(g, renderedText, x, y, scale, color);
    }

    private static Component fitText(Font font, Component text, int maxW, float scale) {
        if (font.width(text) * scale <= maxW) {
            return text;
        }

        String ellipsis = "...";
        int unscaledMaxW = Math.max(0, (int) (maxW / scale) - font.width(ellipsis));
        return Component.literal(font.plainSubstrByWidth(text.getString(), unscaledMaxW) + ellipsis);
    }

    private static void drawScaledString(GuiGraphics g, Component text, int x, int y, float scale, int color) {
        Font font = Minecraft.getInstance().font;
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, text, 0, 0, color, false);
        } finally {
            g.pose().popPose();
        }
    }

    static List<ClientTooltipComponent> tooltip(Component text) {
        return List.of(ClientTooltipComponent.create(text.getVisualOrderText()));
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
