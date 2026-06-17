package cn.dancingsnow.neoecoae.gui.host;

import com.mojang.blaze3d.systems.RenderSystem;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy;
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
import java.util.Arrays;
import java.util.List;

abstract class NEHostCanvas extends UIElement {
    static final int SLOT_SIZE = 18;
    static final int PLAYER_INVENTORY_WIDTH = SLOT_SIZE * 9;

    static final int PANEL_OUTER = 0xFF17141E;
    static final int PANEL_MIDDLE = 0xFF2B2834;
    static final int PANEL_INNER = 0xFF665F6D;
    static final int PANEL_EDGE = 0xFFC9C3D6;

    static final int TEXT_PRIMARY = 0xFFD6D0E0;
    static final int TEXT_VALUE = 0xFF8377FF;
    static final int TEXT_USED = 0xFF6CFFA0;
    static final int TEXT_MUTED = 0xFFAAA4B2;
    static final int TEXT_SUCCESS = 0xFF6CFFA0;
    static final int TEXT_WARNING = 0xFFFFD65A;
    static final int TEXT_BLUE = 0xFF3FD6FF;
    static final int TEXT_ERROR = 0xFFFF6A75;
    static final int TEXT_TITLE = 0xFF403E53;

    private final int panelWidth;
    private final int panelHeight;
    private byte[] cachedSnapshot = new byte[0];
    private float localMouseX;
    private float localMouseY;

    protected NEHostCanvas(int width, int height) {
        this.panelWidth = width;
        this.panelHeight = height;
        layout(layout -> {
            layout.width(width);
            layout.height(height);
        });
    }

    protected void bindSnapshot() {
        var syncValue = DataBindingBuilder.<byte[]>create(this::cachedSnapshot, ignored -> {})
            .syncType(byte[].class)
            .c2sStrategy(SyncStrategy.NONE)
            .build()
            .getSyncValue();
        syncValue.addListener(this::acceptSnapshot);
        addSyncValue(syncValue);
    }

    protected byte[] encodeSnapshot() {
        return new byte[0];
    }

    protected void acceptSnapshot(byte[] snapshot) {
    }

    private byte[] cachedSnapshot() {
        byte[] next = encodeSnapshot();
        if (next == null) {
            next = new byte[0];
        }
        if (!Arrays.equals(cachedSnapshot, next)) {
            cachedSnapshot = next;
        }
        return cachedSnapshot;
    }

    @Override
    public void drawBackgroundTexture(GUIContext guiContext) {
        updateLocalMouse(guiContext);
        drawMainPanel(guiContext, 0, 0, panelWidth, panelHeight);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        updateLocalMouse(guiContext);
        drawHostBackground(guiContext);
    }

    protected void drawHostBackground(GUIContext guiContext) {}

    protected void drawText(GUIContext guiContext, Component text, float x, float y, int color) {
        guiContext.graphics.drawString(guiContext.mc.font, text, Math.round(absX(x)), Math.round(absY(y)), color, false);
    }

    protected void drawText(GUIContext guiContext, String text, float x, float y, int color) {
        guiContext.graphics.drawString(guiContext.mc.font, text, Math.round(absX(x)), Math.round(absY(y)), color, false);
    }

    protected void drawFittedText(GUIContext guiContext, Component text, float x, float y, int maxWidth, int color) {
        drawFittedText(guiContext, text.getString(), x, y, maxWidth, color);
    }

    protected void drawFittedText(GUIContext guiContext, String text, float x, float y, int maxWidth, int color) {
        drawText(guiContext, fit(guiContext, text, maxWidth), x, y, color);
    }

    protected void drawRightText(GUIContext guiContext, String text, float rightX, float y, int color) {
        Font font = guiContext.mc.font;
        drawText(guiContext, text, rightX - font.width(text), y, color);
    }

    protected void drawRightText(GUIContext guiContext, Component text, float rightX, float y, int color) {
        Font font = guiContext.mc.font;
        drawText(guiContext, text, rightX - font.width(text), y, color);
    }

    protected void drawCenteredText(GUIContext guiContext, Component text, float x, float y, float width, int color) {
        Font font = guiContext.mc.font;
        drawText(guiContext, text, x + (width - font.width(text)) / 2.0F, y, color);
    }

    protected void drawCenteredText(GUIContext guiContext, String text, float x, float y, float width, int color) {
        Font font = guiContext.mc.font;
        drawText(guiContext, text, x + (width - font.width(text)) / 2.0F, y, color);
    }

    protected void drawScaledText(GUIContext guiContext, Component text, float x, float y, float scale, int color) {
        guiContext.pose.pushPose();
        guiContext.pose.translate(absX(x), absY(y), 0.0F);
        guiContext.pose.scale(scale, scale, 1.0F);
        guiContext.graphics.drawString(guiContext.mc.font, text, 0, 0, color, false);
        guiContext.pose.popPose();
    }

    protected void drawScaledText(GUIContext guiContext, String text, float x, float y, float scale, int color) {
        drawScaledText(guiContext, Component.literal(text), x, y, scale, color);
    }

    protected void drawScaledFittedText(GUIContext guiContext, Component text, float x, float y, float scale, int maxWidth, int color) {
        drawScaledFittedText(guiContext, text.getString(), x, y, scale, maxWidth, color);
    }

    protected void drawScaledFittedText(GUIContext guiContext, String text, float x, float y, float scale, int maxWidth, int color) {
        int unscaledWidth = Math.max(0, Math.round(maxWidth / Math.max(0.01F, scale)));
        drawScaledText(guiContext, fit(guiContext, text, unscaledWidth), x, y, scale, color);
    }

    protected void drawScaledRightText(GUIContext guiContext, String text, float rightX, float y, float scale, int color) {
        drawScaledText(guiContext, text, rightX - guiContext.mc.font.width(text) * scale, y, scale, color);
    }

    protected void drawScaledCenteredText(GUIContext guiContext, Component text, float x, float y, float width, float scale, int color) {
        drawScaledText(guiContext, text, x + (width - guiContext.mc.font.width(text) * scale) / 2.0F, y, scale, color);
    }

    protected void drawScaledCenteredText(GUIContext guiContext, String text, float x, float y, float width, float scale, int color) {
        drawScaledText(guiContext, text, x + (width - guiContext.mc.font.width(text) * scale) / 2.0F, y, scale, color);
    }

    protected void drawInsetRect(GUIContext guiContext, float x, float y, float w, float h) {
        rectLocal(guiContext, x, y, w, h, PANEL_EDGE);
        rectLocal(guiContext, x + 1, y + 1, w - 2, h - 2, 0xFF0D0D11);
        rectLocal(guiContext, x + 2, y + 2, w - 4, h - 4, PANEL_INNER);
        rectLocal(guiContext, x + 3, y + 3, w - 6, h - 6, PANEL_OUTER);
        rectLocal(guiContext, x + 4, y + 4, w - 8, h - 8, PANEL_MIDDLE);
        rectLocal(guiContext, x + 5, y + 5, w - 10, h - 10, 0xFF605A66);
    }

    protected void drawSmallInsetRect(GUIContext guiContext, float x, float y, float w, float h) {
        rectLocal(guiContext, x, y, w, h, PANEL_EDGE);
        rectLocal(guiContext, x + 1, y + 1, w - 2, h - 2, PANEL_OUTER);
        rectLocal(guiContext, x + 2, y + 2, w - 4, h - 4, PANEL_MIDDLE);
    }

    protected void drawItem(GUIContext guiContext, ItemStack stack, float x, float y) {
        if (!stack.isEmpty()) {
            guiContext.graphics.renderItem(stack, Math.round(absX(x)), Math.round(absY(y)));
        }
    }

    protected void drawItem(GUIContext guiContext, ItemStack stack, float x, float y, float alpha) {
        if (stack.isEmpty() || alpha <= 0.02F) {
            return;
        }
        float clamped = Mth.clamp(alpha, 0.0F, 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, clamped);
        try {
            guiContext.graphics.renderItem(stack, Math.round(absX(x)), Math.round(absY(y)));
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (clamped < 1.0F) {
            rectLocal(guiContext, x, y, 16, 16, withAlpha(0xFF2C2735, 1.0F - clamped));
        }
    }

    protected void drawScaledItem(GUIContext guiContext, ItemStack stack, float x, float y, float scale, float alpha) {
        if (stack.isEmpty() || scale <= 0.0F || alpha <= 0.02F) {
            return;
        }
        float clamped = Mth.clamp(alpha, 0.0F, 1.0F);
        guiContext.graphics.pose().pushPose();
        guiContext.graphics.pose().translate(absX(x), absY(y), 0.0F);
        guiContext.graphics.pose().scale(scale, scale, 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, clamped);
        try {
            guiContext.graphics.renderItem(stack, 0, 0);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            guiContext.graphics.pose().popPose();
        }
        if (clamped < 1.0F) {
            rectLocal(guiContext, x, y, 16.0F * scale, 16.0F * scale, withAlpha(0xFF2C2735, 1.0F - clamped));
        }
    }

    protected void drawIcon(GUIContext guiContext, NEAeSprite icon, float x, float y) {
        icon.draw(guiContext, absX(x), absY(y));
    }

    protected void drawProgressBar(GUIContext guiContext, float x, float y, float w, float h, long used, long total, int fillColor) {
        drawSmallInsetRect(guiContext, x, y, w, h);
        float ix = x + 3;
        float iy = y + 3;
        float iw = Math.max(0.0F, w - 6.0F);
        float ih = Math.max(0.0F, h - 6.0F);
        if (iw <= 0.0F || ih <= 0.0F) {
            return;
        }
        fillLocal(guiContext, ix, iy, iw, ih, 0xAA17141E);
        int fillW = ratioWidth(used, total, Math.round(iw));
        if (fillW > 0) {
            fillLocal(guiContext, ix, iy, fillW, ih, fillColor);
            fillLocal(guiContext, ix, iy, fillW, 1, 0x70FFFFFF);
        }
    }

    protected void drawVerticalGauge(GUIContext guiContext, float x, float y, float w, float h, double ratio, int fillColor) {
        drawInsetRect(guiContext, x, y, w, h);
        float ix = x + 7.0F;
        float iy = y + 7.0F;
        float iw = Math.max(0.0F, w - 14.0F);
        float ih = Math.max(0.0F, h - 14.0F);
        fillLocal(guiContext, ix, iy, iw, ih, 0xAA17141E);
        int fillH = Math.max(0, Math.min(Math.round(ih * (float) Mth.clamp(ratio, 0.0D, 1.0D)), Math.round(ih)));
        if (fillH > 0) {
            float fillY = iy + ih - fillH;
            fillLocal(guiContext, ix, fillY, iw, iy + ih - fillY, fillColor);
            fillLocal(guiContext, ix, fillY, iw, Math.min(2.0F, iy + ih - fillY), 0x70FFFFFF);
        }
    }

    protected void drawScroller(GUIContext guiContext, float x, float y, float w, float h, float thumbY, float thumbH) {
        fillLocal(guiContext, x, y, w, h, 0xAA17141E);
        fillLocal(guiContext, x, thumbY, w, thumbH, 0xFF8B83A0);
    }

    protected void drawListScrollbar(
        GUIContext guiContext,
        int total,
        int visible,
        int scrollOffset,
        float x,
        float y,
        float w,
        float h
    ) {
        if (total <= visible || visible <= 0 || h <= 0.0F) {
            return;
        }
        float thumbH = Math.max(10.0F, h * visible / total);
        float thumbY = y + (h - thumbH) * scrollOffset / Math.max(1.0F, total - visible);
        drawScroller(guiContext, x, y, w, h, thumbY, thumbH);
    }

    protected void drawTaskCardFrame(GUIContext guiContext, float x, float y, float w, float h, float alpha) {
        fillLocal(guiContext, x, y, w, h, withAlpha(0xFFD8D3E4, alpha));
        fillLocal(guiContext, x + 1, y + 1, w - 2, h - 2, withAlpha(0xFF121016, alpha));
        fillLocal(guiContext, x + 2, y + 2, w - 4, h - 4, withAlpha(0xFF4D4855, alpha));
        fillLocal(guiContext, x + 3, y + 3, w - 6, h - 6, withAlpha(0xFF2C2735, alpha));
    }

    protected void drawMainPanel(GUIContext guiContext, float x, float y, float w, float h) {
        drawAePanel(guiContext, Math.round(absX(x)), Math.round(absY(y)), Math.round(w), Math.round(h));
    }

    private static void drawAePanel(GUIContext context, int x, int y, int w, int h) {
        if (w < 8 || h < 8) {
            return;
        }
        int right = x + w;
        int bottom = y + h;
        blitBackground(context, x, y, 0, 0, 4, 4);
        blitBackground(context, right - 4, y, 252, 0, 4, 4);
        blitBackground(context, x, bottom - 4, 0, 252, 4, 4);
        blitBackground(context, right - 4, bottom - 4, 252, 252, 4, 4);
        for (int dx = 0; dx < w - 8; dx += 248) {
            int stripW = Math.min(248, w - 8 - dx);
            blitBackground(context, x + 4 + dx, y, 4, 0, stripW, 4);
            blitBackground(context, x + 4 + dx, bottom - 4, 4, 252, stripW, 4);
            for (int dy = 0; dy < h - 8; dy += 248) {
                int stripH = Math.min(248, h - 8 - dy);
                blitBackground(context, x + 4 + dx, y + 4 + dy, 4, 4, stripW, stripH);
            }
        }
        for (int dy = 0; dy < h - 8; dy += 248) {
            int stripH = Math.min(248, h - 8 - dy);
            blitBackground(context, x, y + 4 + dy, 0, 4, 4, stripH);
            blitBackground(context, right - 4, y + 4 + dy, 252, 4, 4, stripH);
        }
    }

    private static void blitBackground(GUIContext context, int x, int y, int u, int v, int w, int h) {
        context.graphics.blit(NEAeSprite.BACKGROUND_TEXTURE, x, y, w, h, u, v, w, h, 256, 256);
    }

    protected void rectLocal(GUIContext guiContext, float x, float y, float w, float h, int color) {
        rect(guiContext, absX(x), absY(y), w, h, color);
    }

    protected void fillLocal(GUIContext guiContext, float x, float y, float w, float h, int color) {
        rectLocal(guiContext, x, y, w, h, color);
    }

    protected static void rect(GUIContext guiContext, float x, float y, float w, float h, int color) {
        guiContext.graphics.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
    }

    protected float absX(float localX) {
        return getPositionX() + localX;
    }

    protected float absY(float localY) {
        return getPositionY() + localY;
    }

    protected boolean containsLocal(float x, float y, float w, float h, double mouseX, double mouseY) {
        return contains(absX(x), absY(y), w, h, mouseX, mouseY);
    }

    protected float currentMouseX() {
        return localMouseX;
    }

    protected float currentMouseY() {
        return localMouseY;
    }

    protected static boolean contains(float x, float y, float w, float h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    protected static int ratioWidth(long used, long total, int width) {
        if (width <= 0 || total <= 0 || used <= 0) {
            return 0;
        }
        long clamped = Math.max(0L, Math.min(used, total));
        return (int) Math.max(1L, Math.min(width, clamped * width / total));
    }

    protected static int visibleRows(int firstY, int bottomY, int rowHeight, int rowStride) {
        int space = bottomY - firstY;
        return Math.max(1, 1 + Math.max(0, space - rowHeight) / rowStride);
    }

    protected static int clampScroll(int value, int total, int visibleRows) {
        return Mth.clamp(value, 0, Math.max(0, total - visibleRows));
    }

    protected static Component boolText(boolean value) {
        return value ? tr("gui.neoecoae.common.yes", "Yes") : tr("gui.neoecoae.common.no", "No");
    }

    protected static MutableComponent tr(String key, String fallback, Object... args) {
        return Component.translatableWithFallback(key, fallback, args);
    }

    protected static String trString(String key, String fallback, Object... args) {
        return tr(key, fallback, args).getString();
    }

    protected static String fit(GUIContext context, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (context.mc.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = context.mc.font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        String trimmed = text;
        while (!trimmed.isEmpty() && context.mc.font.width(trimmed) + ellipsisWidth > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ellipsis;
    }

    protected static int withAlpha(int color, float alpha) {
        float clamped = Mth.clamp(alpha, 0.0F, 1.0F);
        int baseAlpha = (color >>> 24) & 0xFF;
        int outAlpha = Mth.clamp(Math.round(baseAlpha * clamped), 0, 255);
        return (outAlpha << 24) | (color & 0x00FFFFFF);
    }

    protected static List<Component> itemTooltip(ItemStack stack) {
        if (stack.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
    }

    protected int panelWidth() {
        return panelWidth;
    }

    protected int panelHeight() {
        return panelHeight;
    }

    private void updateLocalMouse(GUIContext context) {
        this.localMouseX = context.localMouseX;
        this.localMouseY = context.localMouseY;
    }
}
