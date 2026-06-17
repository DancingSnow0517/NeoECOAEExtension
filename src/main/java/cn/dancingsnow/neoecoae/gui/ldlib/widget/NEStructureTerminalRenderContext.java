package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibTextRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

record NEStructureTerminalRenderContext(int originX, int originY) {
    Font font() {
        return Minecraft.getInstance().font;
    }

    int absX(int localX) {
        return originX + localX;
    }

    int absY(int localY) {
        return originY + localY;
    }

    void drawLocalString(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(font(), text, absX(x), absY(y), color, false);
    }

    void drawCenteredLocalString(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        Font font = font();
        graphics.drawString(font, text, absX(x) + (width - font.width(text)) / 2, absY(y), color, false);
    }

    void drawRightLocalString(GuiGraphics graphics, Component text, int rightX, int y, int color) {
        Font font = font();
        graphics.drawString(font, text, absX(rightX) - font.width(text), absY(y), color, false);
    }

    void drawFitted(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        drawLocalString(graphics, fitted(text, width), x, y, color);
    }

    void drawCenteredFitted(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        drawCenteredLocalString(graphics, fitted(text, width), x, y, width, color);
    }

    private Component fitted(Component text, int width) {
        return NELDLibTextRender.truncateWithEllipsis(font(), text, width);
    }
}
