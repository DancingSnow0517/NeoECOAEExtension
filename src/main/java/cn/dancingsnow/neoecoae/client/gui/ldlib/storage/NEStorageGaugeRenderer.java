package cn.dancingsnow.neoecoae.client.gui.ldlib.storage;

import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageMetricsModel.Metric;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageMetricsModel.StorageMetrics;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageTextFormatter;
import cn.dancingsnow.neoecoae.gui.ldlib.storage.NEStorageUsageModel;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import com.mojang.blaze3d.systems.RenderSystem;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
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

    public static void drawFinite(GuiGraphics g, int x, int y, double pct, int color) {
        double clamped = Mth.clamp(pct, 0.0D, 1.0D);
        if (clamped <= 0.0D) {
            return;
        }
        int bodyHeight = GAUGE_H - CAP_H;
        int barHeight = (int) Math.round(bodyHeight * clamped);
        drawSegment(g, x, GAUGE_W, y + GAUGE_H - barHeight - CAP_H, y + GAUGE_H, color);
    }

    public static void drawInfinite(GuiGraphics g, int x, int y, NEStorageUiState state, StorageMetrics metrics) {
        List<InfiniteGaugeSegment> segments = new ArrayList<>();
        BigInteger totalAmount = BigInteger.ZERO;
        for (Metric metric : metrics.types()) {
            BigInteger amount = NEStorageTextFormatter.parseAmount(metric.usedAmount());
            if (amount.signum() <= 0) {
                continue;
            }
            segments.add(new InfiniteGaugeSegment(amount, infiniteColor(metric.accentColor())));
            totalAmount = totalAmount.add(amount);
        }
        if (segments.isEmpty() || totalAmount.signum() <= 0 || state.infiniteDomainEmpty()) {
            drawFinite(g, x, y, 1.0D, 0x22CA6CFF);
            return;
        }

        int filledHeight = 0;
        BigInteger accumulated = BigInteger.ZERO;
        for (int i = 0; i < segments.size(); i++) {
            InfiniteGaugeSegment segment = segments.get(i);
            accumulated = accumulated.add(segment.amount());
            int nextFilledHeight = i == segments.size() - 1
                    ? GAUGE_H
                    : accumulated
                            .multiply(BigInteger.valueOf(GAUGE_H))
                            .divide(totalAmount)
                            .intValue();
            nextFilledHeight = Mth.clamp(nextFilledHeight, filledHeight, GAUGE_H);
            int segmentHeight = nextFilledHeight - filledHeight;
            if (segmentHeight > 0) {
                int bottom = y + GAUGE_H - filledHeight;
                drawSegment(g, x, GAUGE_W, bottom - segmentHeight, bottom, segment.color());
            }
            filledHeight = nextFilledHeight;
        }
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

    private static int infiniteColor(int color) {
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        int max = Math.max(red, Math.max(green, blue));
        if (max > 0) {
            red = Math.min(255, red * 255 / max);
            green = Math.min(255, green * 255 / max);
            blue = Math.min(255, blue * 255 / max);
        }
        return NELDLibStyle.lerpColor(0xD8000000 | (red << 16) | (green << 8) | blue, 0xD8FFFFFF, 0.08D);
    }

    private record InfiniteGaugeSegment(BigInteger amount, int color) {}

    private NEStorageGaugeRenderer() {}
}
