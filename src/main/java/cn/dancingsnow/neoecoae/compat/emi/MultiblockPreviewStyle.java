package cn.dancingsnow.neoecoae.compat.emi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import java.util.List;

final class MultiblockPreviewStyle {
    static final int TEXT_COLOR = 0xFF404040;
    static final int PANEL_COLOR = 0xFFE3E3E3;
    static final int PANEL_BORDER = 0xFF4F4F4F;
    static final int BUTTON_BG = 0xFF8F8F8F;
    static final int BUTTON_BG_HOVER = 0xFFABABAB;
    static final int BUTTON_BORDER = 0xFF303030;
    static final int SLOT_SIZE = 18;

    private MultiblockPreviewStyle() {
    }

    static void drawPanel(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, PANEL_COLOR);
        g.fill(0, 0, width, 1, PANEL_BORDER);
        g.fill(0, height - 1, width, height, PANEL_BORDER);
        g.fill(0, 0, 1, height, PANEL_BORDER);
        g.fill(width - 1, 0, width, height, PANEL_BORDER);
    }

    static void drawButton(GuiGraphics g, MultiblockPreviewLayout.Rect rect, String text, int mouseX, int mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);
        Font font = Minecraft.getInstance().font;
        g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), BUTTON_BORDER);
        g.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.bottom() - 1, hovered ? BUTTON_BG_HOVER : BUTTON_BG);
        int textX = rect.x() + (rect.width() - font.width(text)) / 2;
        int textY = rect.y() + (rect.height() - font.lineHeight) / 2;
        g.drawString(font, text, textX, textY, 0xFFFFFFFF, false);
    }

    static void drawFittedString(GuiGraphics g, Component text, int x, int y, int maxW, int color) {
        Font font = Minecraft.getInstance().font;
        int textW = font.width(text);
        if (textW <= maxW) {
            g.drawString(font, text, x, y, color, false);
            return;
        }

        float scale = Math.max(0.78F, maxW / (float) textW);
        Component renderedText = text;
        if (textW * scale > maxW) {
            String ellipsis = "...";
            int unscaledMaxW = Math.max(0, (int) (maxW / scale) - font.width(ellipsis));
            renderedText = Component.literal(font.plainSubstrByWidth(text.getString(), unscaledMaxW) + ellipsis);
        }
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, renderedText, 0, 0, color, false);
        } finally {
            g.pose().popPose();
        }
    }

    static List<ClientTooltipComponent> tooltip(Component text) {
        return List.of(ClientTooltipComponent.create(text.getVisualOrderText()));
    }
}
