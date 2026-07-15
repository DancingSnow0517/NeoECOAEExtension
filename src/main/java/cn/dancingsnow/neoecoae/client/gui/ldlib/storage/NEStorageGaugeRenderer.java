package cn.dancingsnow.neoecoae.client.gui.ldlib.storage;

import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageUsageModel;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Renders the storage load column independently from the host screen coordinator. */
public final class NEStorageGaugeRenderer {
    private static final ResourceLocation STORAGE_ELEMENTS =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/storage/estorage_controller_elements.png");
    private static final int GAUGE_W = 32;
    private static final int GAUGE_H = 143;
    private static final int CAP_H = 8;
    private static final int TOP_U = 1;
    private static final int TOP_V = 246;
    private static final int MID_U = 34;
    private static final int MID_V = 250;
    private static final int MID_H = 4;
    private static final int BOTTOM_U = 1;
    private static final int BOTTOM_V = 246;
    private static final int ELEMENTS_SIZE = 256;
    private static final int INFINITE_GAUGE_COLOR = 0xFFCA6CFF;

    public static void drawFinite(GuiGraphics g, int x, int y, double pct, int color) {
        double clamped = Mth.clamp(pct, 0.0D, 1.0D);
        if (clamped <= 0.0D) {
            return;
        }
        int bodyHeight = GAUGE_H - CAP_H;
        int barHeight = (int) Math.round(bodyHeight * clamped);
        drawSegment(g, x, GAUGE_W, y + GAUGE_H - barHeight - CAP_H, y + GAUGE_H, color);
    }

    public static void drawInfinite(GuiGraphics g, int x, int y) {
        drawFinite(g, x, y, 1.0D, INFINITE_GAUGE_COLOR);
    }

    public static double totalUsagePercent(NEStorageUiState state) {
        return NEStorageUsageModel.percent(state.totalUsedBytes(), state.totalBytes());
    }

    public static int colorForPercent(double pct) {
        double amount = Mth.clamp(pct, 0.0D, 1.0D);
        if (amount < 0.5D) {
            return NELDLibStyle.lerpColor(0xBF00FF00, 0xBFFFFF00, amount / 0.5D);
        }
        return NELDLibStyle.lerpColor(0xBFFFFF00, 0xBFFF0000, (amount - 0.5D) / 0.5D);
    }

    private static void drawSegment(GuiGraphics g, int x, int width, int top, int bottom, int color) {
        if (width <= 0 || bottom <= top) {
            return;
        }
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(red, green, blue, alpha);
        g.blit(STORAGE_ELEMENTS, x, top, width, CAP_H, TOP_U, TOP_V, GAUGE_W, CAP_H, ELEMENTS_SIZE, ELEMENTS_SIZE);
        for (int drawY = top + CAP_H / 2 + 1; drawY < bottom - CAP_H / 2 + 1; drawY++) {
            g.blit(
                    STORAGE_ELEMENTS,
                    x,
                    drawY,
                    width,
                    MID_H,
                    MID_U,
                    MID_V,
                    GAUGE_W,
                    MID_H,
                    ELEMENTS_SIZE,
                    ELEMENTS_SIZE);
        }
        g.blit(
                STORAGE_ELEMENTS,
                x,
                bottom - CAP_H,
                width,
                CAP_H,
                BOTTOM_U,
                BOTTOM_V,
                GAUGE_W,
                CAP_H,
                ELEMENTS_SIZE,
                ELEMENTS_SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private NEStorageGaugeRenderer() {}
}
