package cn.dancingsnow.neoecoae.client.gui.ldlib.crafting;

import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared coordinate and text primitives used by the focused crafting host panels. */
public final class NECraftingRenderContext {
    public static final float TEXT_SCALE = 0.8F;

    private final GuiGraphics graphics;
    private final Font font;
    private final IntUnaryOperator screenX;
    private final IntUnaryOperator screenY;

    public NECraftingRenderContext(
            GuiGraphics graphics, Font font, IntUnaryOperator screenX, IntUnaryOperator screenY) {
        this.graphics = graphics;
        this.font = font;
        this.screenX = screenX;
        this.screenY = screenY;
    }

    public GuiGraphics graphics() {
        return graphics;
    }

    public Font font() {
        return font;
    }

    public int x(int localX) {
        return screenX.applyAsInt(localX);
    }

    public int y(int localY) {
        return screenY.applyAsInt(localY);
    }

    public int scaledWidth(Component text) {
        return Math.round(font.width(text) * TEXT_SCALE);
    }

    public int scaledWidth(String text) {
        return Math.round(font.width(text) * TEXT_SCALE);
    }

    public int draw(Component text, int localX, int localY, int color) {
        return drawAbsolute(text, x(localX), y(localY), color);
    }

    public int draw(String text, int localX, int localY, int color) {
        return draw(Component.literal(text), localX, localY, color);
    }

    public int drawAbsolute(Component text, int absoluteX, int absoluteY, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(absoluteX, absoluteY, 0.0F);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
        return scaledWidth(text);
    }

    public int drawAbsolute(String text, int absoluteX, int absoluteY, int color) {
        return drawAbsolute(Component.literal(text), absoluteX, absoluteY, color);
    }

    public void drawRight(Component text, int localRightX, int localY, int color) {
        drawAbsolute(text, x(localRightX) - scaledWidth(text), y(localY), color);
    }

    public void drawRightAbsolute(Component text, int absoluteRightX, int absoluteY, int color) {
        drawAbsolute(text, absoluteRightX - scaledWidth(text), absoluteY, color);
    }

    public void drawFitted(Component text, int localX, int localY, int maxWidth, int color) {
        draw(fit(text.getString(), maxWidth), localX, localY, color);
    }

    public void drawFittedAbsolute(Component text, int absoluteX, int absoluteY, int maxWidth, int color) {
        drawAbsolute(fit(text.getString(), maxWidth), absoluteX, absoluteY, color);
    }

    public String fit(String text, int maxWidth) {
        if (scaledWidth(text) <= maxWidth) {
            return text;
        }
        int rawWidth = Math.max(1, (int) Math.floor(maxWidth / TEXT_SCALE));
        return font.plainSubstrByWidth(text, rawWidth);
    }
}
