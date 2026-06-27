package cn.dancingsnow.neoecoae.gui.host;

import com.mojang.blaze3d.systems.RenderSystem;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class NEHostUiPrimitives {
    static final int SLOT_SIZE = 18;
    static final int PLAYER_INVENTORY_WIDTH = SLOT_SIZE * 9;

    static final int PANEL_OUTER = 0xFF17141E;
    static final int PANEL_MIDDLE = 0xFF2B2834;
    static final int PANEL_INNER = 0xFF665F6D;
    static final int PANEL_EDGE = 0xFFC9C3D6;

    static final int TEXT_PRIMARY = 0xFFD6D0E0;
    static final int TEXT_VALUE = 0xFF8377FF;
    static final int TEXT_MUTED = 0xFFAAA4B2;
    static final int TEXT_SUCCESS = 0xFF6CFFA0;
    static final int TEXT_WARNING = 0xFFFFD65A;
    static final int TEXT_BLUE = 0xFF3FD6FF;
    static final int TEXT_ERROR = 0xFFFF6A75;
    static final int TEXT_TITLE = 0xFF403E53;

    private NEHostUiPrimitives() {
    }

    static float ax(UIElement element, float localX) {
        return element.getPositionX() + localX;
    }

    static float ay(UIElement element, float localY) {
        return element.getPositionY() + localY;
    }

    static void text(UIElement element, GUIContext context, Component text, float x, float y, int color) {
        context.graphics.drawString(context.mc.font, text, Math.round(ax(element, x)), Math.round(ay(element, y)), color, false);
    }

    static void text(UIElement element, GUIContext context, String text, float x, float y, int color) {
        context.graphics.drawString(context.mc.font, text, Math.round(ax(element, x)), Math.round(ay(element, y)), color, false);
    }

    static void fittedText(UIElement element, GUIContext context, Component text, float x, float y, int maxWidth, int color) {
        text(element, context, fit(context.mc.font, text.getString(), maxWidth), x, y, color);
    }

    static void rightText(UIElement element, GUIContext context, String text, float rightX, float y, int color) {
        text(element, context, text, rightX - context.mc.font.width(text), y, color);
    }

    static void centeredText(UIElement element, GUIContext context, Component text, float x, float y, float width, int color) {
        text(element, context, text, x + (width - context.mc.font.width(text)) / 2.0F, y, color);
    }

    static void scaledText(UIElement element, GUIContext context, Component text, float x, float y, float scale, int color) {
        context.pose.pushPose();
        context.pose.translate(ax(element, x), ay(element, y), 0.0F);
        context.pose.scale(scale, scale, 1.0F);
        context.graphics.drawString(context.mc.font, text, 0, 0, color, false);
        context.pose.popPose();
    }

    static void scaledText(UIElement element, GUIContext context, String text, float x, float y, float scale, int color) {
        scaledText(element, context, Component.literal(text), x, y, scale, color);
    }

    static void scaledFittedText(UIElement element, GUIContext context, Component text, float x, float y, float scale, int maxWidth, int color) {
        int unscaledWidth = Math.max(0, Math.round(maxWidth / Math.max(0.01F, scale)));
        scaledText(element, context, fit(context.mc.font, text.getString(), unscaledWidth), x, y, scale, color);
    }

    static void scaledRightText(UIElement element, GUIContext context, String text, float rightX, float y, float scale, int color) {
        scaledText(element, context, text, rightX - context.mc.font.width(text) * scale, y, scale, color);
    }

    static void scaledCenteredText(UIElement element, GUIContext context, Component text, float x, float y, float width, float scale, int color) {
        scaledText(element, context, text, x + (width - context.mc.font.width(text) * scale) / 2.0F, y, scale, color);
    }

    static void rect(UIElement element, GUIContext context, float x, float y, float w, float h, int color) {
        context.graphics.fill(Math.round(ax(element, x)), Math.round(ay(element, y)), Math.round(ax(element, x + w)), Math.round(ay(element, y + h)), color);
    }

    static void insetRect(UIElement element, GUIContext context, float x, float y, float w, float h) {
        rect(element, context, x, y, w, h, PANEL_EDGE);
        rect(element, context, x + 1, y + 1, w - 2, h - 2, 0xFF0D0D11);
        rect(element, context, x + 2, y + 2, w - 4, h - 4, PANEL_INNER);
        rect(element, context, x + 3, y + 3, w - 6, h - 6, PANEL_OUTER);
        rect(element, context, x + 4, y + 4, w - 8, h - 8, PANEL_MIDDLE);
        rect(element, context, x + 5, y + 5, w - 10, h - 10, 0xFF605A66);
    }

    static void smallInsetRect(UIElement element, GUIContext context, float x, float y, float w, float h) {
        rect(element, context, x, y, w, h, PANEL_EDGE);
        rect(element, context, x + 1, y + 1, w - 2, h - 2, PANEL_OUTER);
        rect(element, context, x + 2, y + 2, w - 4, h - 4, PANEL_MIDDLE);
    }

    static void progressBar(UIElement element, GUIContext context, float x, float y, float w, float h, long used, long total, int fillColor) {
        smallInsetRect(element, context, x, y, w, h);
        float ix = x + 3;
        float iy = y + 3;
        float iw = Math.max(0.0F, w - 6.0F);
        float ih = Math.max(0.0F, h - 6.0F);
        rect(element, context, ix, iy, iw, ih, 0xAA17141E);
        int fillW = ratioWidth(used, total, Math.round(iw));
        if (fillW > 0) {
            rect(element, context, ix, iy, fillW, ih, fillColor);
            rect(element, context, ix, iy, fillW, 1, 0x70FFFFFF);
        }
    }

    static void verticalGauge(UIElement element, GUIContext context, float x, float y, float w, float h, double ratio, int fillColor) {
        insetRect(element, context, x, y, w, h);
        float ix = x + 7.0F;
        float iy = y + 7.0F;
        float iw = Math.max(0.0F, w - 14.0F);
        float ih = Math.max(0.0F, h - 14.0F);
        rect(element, context, ix, iy, iw, ih, 0xAA17141E);
        int fillH = Math.max(0, Math.min(Math.round(ih * (float) Mth.clamp(ratio, 0.0D, 1.0D)), Math.round(ih)));
        if (fillH > 0) {
            float fillY = iy + ih - fillH;
            rect(element, context, ix, fillY, iw, iy + ih - fillY, fillColor);
            rect(element, context, ix, fillY, iw, Math.min(2.0F, iy + ih - fillY), 0x70FFFFFF);
        }
    }

    static void scroller(UIElement element, GUIContext context, float x, float y, float w, float h, float thumbY, float thumbH) {
        rect(element, context, x, y, w, h, 0xAA17141E);
        rect(element, context, x, thumbY, w, thumbH, 0xFF8B83A0);
    }

    static void taskCardFrame(UIElement element, GUIContext context, float x, float y, float w, float h, float alpha) {
        rect(element, context, x, y, w, h, withAlpha(0xFFD8D3E4, alpha));
        rect(element, context, x + 1, y + 1, w - 2, h - 2, withAlpha(0xFF121016, alpha));
        rect(element, context, x + 2, y + 2, w - 4, h - 4, withAlpha(0xFF4D4855, alpha));
        rect(element, context, x + 3, y + 3, w - 6, h - 6, withAlpha(0xFF2C2735, alpha));
    }

    static void item(UIElement element, GUIContext context, ItemStack stack, float x, float y) {
        if (!stack.isEmpty()) {
            context.graphics.renderItem(stack, Math.round(ax(element, x)), Math.round(ay(element, y)));
        }
    }

    static void scaledItem(UIElement element, GUIContext context, ItemStack stack, float x, float y, float scale, float alpha) {
        if (stack.isEmpty() || scale <= 0.0F || alpha <= 0.02F) {
            return;
        }
        float clamped = Mth.clamp(alpha, 0.0F, 1.0F);
        context.graphics.pose().pushPose();
        context.graphics.pose().translate(ax(element, x), ay(element, y), 0.0F);
        context.graphics.pose().scale(scale, scale, 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, clamped);
        try {
            context.graphics.renderItem(stack, 0, 0);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            context.graphics.pose().popPose();
        }
        if (clamped < 1.0F) {
            rect(element, context, x, y, 16.0F * scale, 16.0F * scale, withAlpha(0xFF2C2735, 1.0F - clamped));
        }
    }

    static boolean contains(UIElement element, float x, float y, float w, float h, double mouseX, double mouseY) {
        float ax = ax(element, x);
        float ay = ay(element, y);
        return mouseX >= ax && mouseX < ax + w && mouseY >= ay && mouseY < ay + h;
    }

    static int ratioWidth(long used, long total, int width) {
        if (width <= 0 || total <= 0 || used <= 0) {
            return 0;
        }
        long clamped = Math.max(0L, Math.min(used, total));
        return (int) Math.max(1L, Math.min(width, clamped * width / total));
    }

    static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        String trimmed = text;
        while (!trimmed.isEmpty() && font.width(trimmed) + ellipsisWidth > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    static int withAlpha(int color, float alpha) {
        float clamped = Mth.clamp(alpha, 0.0F, 1.0F);
        int baseAlpha = (color >>> 24) & 0xFF;
        int outAlpha = Mth.clamp(Math.round(baseAlpha * clamped), 0, 255);
        return (outAlpha << 24) | (color & 0x00FFFFFF);
    }

    static List<Component> itemTooltip(ItemStack stack) {
        if (stack.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
    }

    static MutableComponent tr(String key, String fallback, Object... args) {
        return Component.translatableWithFallback(key, fallback, args);
    }

    static String trString(String key, String fallback, Object... args) {
        return tr(key, fallback, args).getString();
    }
}
