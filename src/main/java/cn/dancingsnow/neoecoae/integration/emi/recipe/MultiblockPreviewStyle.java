package cn.dancingsnow.neoecoae.integration.emi.recipe;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
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
    public static final int SLOT_SIZE = 18;
    private static final IGuiTexture BUTTON =
            new ResourceBorderTexture("neoecoae:textures/gui/button.png", 20, 20, 2, 2);
    private static final IGuiTexture BUTTON_HOVER =
            new ResourceBorderTexture("neoecoae:textures/gui/button_hover.png", 20, 20, 2, 2);

    private MultiblockPreviewStyle() {}

    public static void drawPanel(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, PANEL_COLOR);
    }

    static void drawButton(GuiGraphics g, MultiblockPreviewLayout.Rect rect, String text, int mouseX, int mouseY) {
        drawButton(g, rect.x(), rect.y(), rect.width(), rect.height(), text, mouseX, mouseY);
    }

    public static void drawButton(
            GuiGraphics g, int x, int y, int width, int height, String text, double mouseX, double mouseY) {
        boolean hovered = contains(mouseX, mouseY, x, y, width, height);
        IGuiTexture texture = hovered ? BUTTON_HOVER : BUTTON;
        texture.draw(g, (int) mouseX, (int) mouseY, x, y, width, height);
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
