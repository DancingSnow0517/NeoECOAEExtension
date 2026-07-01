package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.client.gui.Icon;
import appeng.core.localization.GuiText;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.PriorityMenu;
import appeng.menu.locator.MenuLocators;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibValueText;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEStorageMetricsModel.Metric;
import cn.dancingsnow.neoecoae.gui.ldlib.widget.NEStorageMetricsModel.StorageMetrics;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class NEStorageControllerWidget extends NELDLibSyncedStateWidget<NEStorageUiState> {
    public static final int UI_WIDTH = 344;
    public static final int UI_HEIGHT = 232;
    private static final ResourceLocation STORAGE_ELEMENTS =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/storage/estorage_controller_elements.png");
    private static final int LEFT_PANEL_X = 8;
    private static final int LEFT_PANEL_Y = 24;
    private static final int LEFT_PANEL_W = 162;
    private static final int LEFT_PANEL_H = 200;
    private static final int TEXT_START_X = LEFT_PANEL_X + 8;
    private static final int TEXT_START_Y = LEFT_PANEL_Y + 8;
    private static final int TEXT_LINE_STEP = 13;
    private static final int TEXT_MAX_W = LEFT_PANEL_W - 16;
    private static final int USAGE_PANEL_X = 174;
    private static final int USAGE_PANEL_Y = 24;
    private static final int USAGE_PANEL_W = 166;
    private static final int USAGE_PANEL_H = 200;
    private static final int STORAGE_GAUGE_X = USAGE_PANEL_X + 18;
    private static final int STORAGE_GAUGE_Y = USAGE_PANEL_Y + 23;
    private static final int STORAGE_GAUGE_W = 32;
    private static final int STORAGE_GAUGE_H = 160;
    private static final int USAGE_DETAIL_X = STORAGE_GAUGE_X + STORAGE_GAUGE_W + 10;
    private static final int USAGE_DETAIL_Y = STORAGE_GAUGE_Y + 5;
    private static final int USAGE_DETAIL_W = USAGE_PANEL_X + USAGE_PANEL_W - 10 - USAGE_DETAIL_X;
    private static final int USAGE_DETAIL_LINE_H = 12;
    private static final float USAGE_DETAIL_TEXT_SCALE = 0.66F;
    private static final int USAGE_DARK_X = USAGE_PANEL_X + 8;
    private static final int USAGE_DARK_Y = STORAGE_GAUGE_Y - 4;
    private static final int USAGE_DARK_W = USAGE_PANEL_W - 16;
    private static final int USAGE_DARK_H = STORAGE_GAUGE_H + 8;
    private static final int STORAGE_GAUGE_CAP_H = 8;
    private static final int STORAGE_GAUGE_TOP_U = 1;
    private static final int STORAGE_GAUGE_TOP_V = 246;
    private static final int STORAGE_GAUGE_MID_U = 34;
    private static final int STORAGE_GAUGE_MID_V = 250;
    private static final int STORAGE_GAUGE_MID_H = 4;
    private static final int STORAGE_GAUGE_BOTTOM_U = 1;
    private static final int STORAGE_GAUGE_BOTTOM_V = 246;
    private static final int STORAGE_ELEMENTS_SIZE = 256;
    private static final int PRIORITY_BUTTON_X = UI_WIDTH - 22;
    private static final int PRIORITY_BUTTON_Y = 0;
    private static final int PRIORITY_BUTTON_W = 22;
    private static final int PRIORITY_BUTTON_H = 22;
    static final int SLOT_SIZE = 18;
    private static final int HEADER_STATUS_RIGHT = PRIORITY_BUTTON_X - 6;
    private static final double LEFT_SCROLL_SPEED = 13.0D;
    private static final long USAGE_ANIMATION_MS = 500L;
    private static final double USAGE_ANIMATION_EPSILON = 0.0001D;

    private static final Map<ScrollKey, ScrollSnapshot> SCROLL_MEMORY = new java.util.HashMap<>();

    private final ECOStorageSystemBlockEntity storage;
    private final Player player;

    private double leftScrollPixels;
    private double usageAnimationStart;
    private double usageAnimationTarget = -1.0D;
    private long usageAnimationStartMs;

    public NEStorageControllerWidget(ECOStorageSystemBlockEntity storage, Player player) {
        super(
                storage.getBlockState().getBlock().getName(),
                UI_WIDTH,
                UI_HEIGHT,
                NEStorageUiState.empty(storage.getBlockPos()),
                storage::createStorageUiState,
                NELDLibStateCodecs::writeStorage,
                NELDLibStateCodecs::readStorage,
                20);
        this.storage = storage;
        this.player = player;
        restoreScrollState();
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        addWidget(new NEAe2IconButtonWidget(
                        PRIORITY_BUTTON_X,
                        PRIORITY_BUTTON_Y,
                        PRIORITY_BUTTON_W,
                        PRIORITY_BUTTON_H,
                        Icon.WRENCH,
                        click -> {
                            if (!click.isRemote && player instanceof ServerPlayer serverPlayer && storage.isFormed()) {
                                MenuOpener.open(PriorityMenu.TYPE, serverPlayer, MenuLocators.forBlockEntity(storage));
                            }
                        })
                .useAeTabButton());
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        restoreScrollState();
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int ox = getPositionX();
        int oy = getPositionY();
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + LEFT_PANEL_X, oy + LEFT_PANEL_Y, LEFT_PANEL_W, LEFT_PANEL_H);
        NELDLibClientStyle.drawDarkInsetRect(
                graphics, ox + USAGE_PANEL_X, oy + USAGE_PANEL_Y, USAGE_PANEL_W, USAGE_PANEL_H);
        drawUsagePanelBackground(graphics, currentState());
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        StorageMetrics metrics = NEStorageMetricsModel.from(currentState());
        drawLocalString(graphics, title, NELDLibUiTitleX(), NELDLibUiTitleY(), TEXT_PRIMARY);
        drawStorageTextLines(graphics, metrics);
        drawLeftScrollbar(graphics, metrics);
        drawUsagePanelText(graphics, currentState(), metrics);
        drawFormedStatus(graphics, currentState().formed());
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isMouseIn(PRIORITY_BUTTON_X, PRIORITY_BUTTON_Y, PRIORITY_BUTTON_W, PRIORITY_BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font(), List.of(GuiText.Priority.text()), mouseX, mouseY);
            return;
        }
        renderUsageTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (Widget.isMouseOver(absX(LEFT_PANEL_X), absY(LEFT_PANEL_Y), LEFT_PANEL_W, LEFT_PANEL_H, mouseX, mouseY)) {
            double maxScroll = maxLeftScrollPixels();
            double previous = leftScrollPixels;
            leftScrollPixels = Mth.clamp(leftScrollPixels - wheelDelta * LEFT_SCROLL_SPEED, 0.0D, maxScroll);
            rememberScrollState();
            return leftScrollPixels != previous || maxScroll > 0.0D;
        }
        return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
    }

    private void drawStorageTextLines(GuiGraphics g, StorageMetrics metrics) {
        leftScrollPixels = Mth.clamp(leftScrollPixels, 0.0D, maxLeftScrollPixels(metrics));
        List<Metric> activeMetrics = activeTypeMetrics(metrics);
        int x = absX(TEXT_START_X);
        int y = absY(TEXT_START_Y - (int) Math.round(leftScrollPixels));
        g.enableScissor(
                absX(LEFT_PANEL_X + 4),
                absY(LEFT_PANEL_Y + 4),
                absX(LEFT_PANEL_X + LEFT_PANEL_W - 4),
                absY(LEFT_PANEL_Y + LEFT_PANEL_H - 4));

        drawPlainLine(g, Component.translatable("gui.neoecoae.storage.energy"), x, y, NELDLibStyle.DARK_TEXT_PRIMARY);
        y += TEXT_LINE_STEP;
        drawEnergyUsedTotalLine(g, metrics.energy(), x, y);
        y += TEXT_LINE_STEP;

        for (Metric metric : activeMetrics) {
            y = drawStorageTypeBlock(g, metric, x, y);
        }
        g.disableScissor();
    }

    private double maxLeftScrollPixels() {
        return maxLeftScrollPixels(NEStorageMetricsModel.from(currentState()));
    }

    private double maxLeftScrollPixels(StorageMetrics metrics) {
        int typeCount = activeTypeMetrics(metrics).size();
        int lineCount = 2 + typeCount * 3;
        int contentHeight = (lineCount - 1) * TEXT_LINE_STEP + font().lineHeight;
        int viewportHeight = LEFT_PANEL_H - 16;
        return Math.max(0, contentHeight - viewportHeight);
    }

    private void drawLeftScrollbar(GuiGraphics g, StorageMetrics metrics) {
        double maxScroll = maxLeftScrollPixels(metrics);
        if (maxScroll <= 0.0D) {
            leftScrollPixels = 0.0D;
            return;
        }
        leftScrollPixels = Mth.clamp(leftScrollPixels, 0.0D, maxScroll);
        int trackX = absX(LEFT_PANEL_X + LEFT_PANEL_W - 5);
        int trackY = absY(LEFT_PANEL_Y + 5);
        int trackH = LEFT_PANEL_H - 10;
        int contentH = trackH + (int) Math.ceil(maxScroll);
        int thumbH = Math.max(12, trackH * trackH / contentH);
        int thumbY = trackY + (int) Math.round((trackH - thumbH) * leftScrollPixels / maxScroll);
        g.fill(trackX, trackY, trackX + 2, trackY + trackH, 0xAA17141E);
        g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF8B83A0);
    }

    private int drawStorageTypeBlock(GuiGraphics g, Metric metric, int x, int y) {
        drawPlainLine(g, metric.label(), x, y, metric.accentColor());
        y += TEXT_LINE_STEP;
        drawTypeUsedTotalLine(g, metric, x, y);
        y += TEXT_LINE_STEP;
        drawByteUsedTotalLine(g, metric.used(), metric.max(), x, y);
        return y + TEXT_LINE_STEP;
    }

    private void drawEnergyUsedTotalLine(GuiGraphics g, Metric energy, int x, int y) {
        String prefix =
                Component.translatable("gui.neoecoae.storage.energy_storage").getString() + ": ";
        String usedText = NELDLibText.number(energy.used());
        String maxText = NELDLibText.number(energy.max());
        String suffix = "AE";
        if (usedTotalWidth(prefix, usedText, maxText, suffix) > TEXT_MAX_W) {
            usedText = NELDLibText.compactTaskAmount(energy.used());
            maxText = NELDLibText.compactTaskAmount(energy.max());
        }
        NELDLibValueText.drawUsedTotal(g, font(), prefix, usedText, maxText, energy.used(), energy.max(), suffix, x, y);
    }

    private void drawTypeUsedTotalLine(GuiGraphics g, Metric metric, int x, int y) {
        String usedText = NELDLibText.number(metric.usedTypes());
        String maxText = NELDLibText.number(metric.totalTypes());
        String suffix = Component.translatable("gui.neoecoae.common.types").getString();
        if (usedTotalWidth("", usedText, maxText, suffix) > TEXT_MAX_W) {
            usedText = NELDLibText.compactTaskAmount(metric.usedTypes());
            maxText = NELDLibText.compactTaskAmount(metric.totalTypes());
        }
        NELDLibValueText.drawUsedTotal(
                g, font(), "", usedText, maxText, metric.usedTypes(), metric.totalTypes(), suffix, x, y);
    }

    private void drawByteUsedTotalLine(GuiGraphics g, long used, long max, int x, int y) {
        String usedText = NELDLibText.storageBytes(used);
        String maxText = NELDLibText.storageBytes(max);
        String suffix =
                Component.translatable("gui.neoecoae.storage.bytes_used").getString();
        if (usedTotalWidth("", usedText, maxText, suffix) > TEXT_MAX_W) {
            suffix = Component.translatable("gui.neoecoae.storage.used_short").getString();
        }
        if (usedTotalWidth("", usedText, maxText, suffix) > TEXT_MAX_W) {
            usedText = NELDLibText.storageBytesCompact(used);
            maxText = NELDLibText.storageBytesCompact(max);
        }
        NELDLibValueText.drawUsedTotal(g, font(), "", usedText, maxText, used, max, suffix, x, y);
    }

    private int usedTotalWidth(String prefix, String usedText, String maxText, String suffix) {
        int width = font().width(prefix + usedText + " / " + maxText);
        return suffix.isEmpty() ? width : width + font().width(" " + suffix);
    }

    private void drawFormedStatus(GuiGraphics g, boolean formed) {
        Component label = Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component value = boolText(formed);
        int textW = font().width(label) + font().width(value);
        int textX = absX(HEADER_STATUS_RIGHT - textW);
        int textY = absY(NELDLibUiTitleY());
        g.drawString(font(), label, textX, textY, 0xFF4A4A4A, false);
        g.drawString(font(), value, textX + font().width(label), textY, formed ? 0xFF1F9D55 : 0xFFD13F3F, false);
    }

    private void drawUsagePanelBackground(GuiGraphics g, NEStorageUiState state) {
        double usage = animatedUsagePercent(totalUsagePercent(state));
        NELDLibClientStyle.drawTinyInsetRect(
                g, absX(USAGE_DARK_X), absY(USAGE_DARK_Y), USAGE_DARK_W, USAGE_DARK_H, 0xFF201E27);
        drawStorageGauge(g, absX(STORAGE_GAUGE_X), absY(STORAGE_GAUGE_Y), usage, storageGaugeColor(usage));
    }

    private void drawUsagePanelText(GuiGraphics g, NEStorageUiState state, StorageMetrics metrics) {
        double usage = animatedUsagePercent(totalUsagePercent(state));
        NELDLibClientStyle.drawCentered(
                g,
                font(),
                Component.translatable("gui.neoecoae.storage.system_load"),
                absX(USAGE_PANEL_X),
                absY(USAGE_PANEL_Y + 8),
                USAGE_PANEL_W,
                NELDLibStyle.DARK_TEXT_PRIMARY);

        int y = USAGE_DETAIL_Y;
        drawUsageDetailLine(
                g,
                Component.translatable("gui.neoecoae.storage.current_load")
                        .append(": ")
                        .append(Component.literal(NELDLibText.percentOrNA(state.totalUsedBytes(), state.totalBytes()))),
                y,
                NELDLibStyle.DARK_TEXT_PRIMARY);
        y += USAGE_DETAIL_LINE_H;
        drawUsageDetailLine(
                g,
                Component.translatable("gui.neoecoae.storage.max_load")
                        .append(": ")
                        .append(Component.literal(
                                NELDLibText.percentOrNA(maxMatrixUsed(state), maxMatrixTotal(state)))),
                y,
                NELDLibStyle.DARK_TEXT_WARNING);
        y += USAGE_DETAIL_LINE_H;
        Metric highestType = highestPressureMetric(metrics);
        drawUsageDetailLine(
                g,
                Component.translatable("gui.neoecoae.storage.status")
                        .append(": ")
                        .append(storageStatus(highestType)),
                y,
                statusColor(highestType));
        y += USAGE_DETAIL_LINE_H;
        drawUsageDetailLine(
                g,
                Component.translatable("gui.neoecoae.storage.idle_matrices")
                        .append(": ")
                        .append(Component.literal(NELDLibText.number(idleMatrixCount(state)))),
                y,
                NELDLibStyle.DARK_TEXT_MUTED);

        NELDLibClientStyle.drawCenteredScaledString(
                g,
                font(),
                state.totalBytes() <= 0L ? "N/A" : NELDLibText.percent(usage),
                absX(STORAGE_GAUGE_X),
                absY(USAGE_PANEL_Y + USAGE_PANEL_H - 12),
                STORAGE_GAUGE_W,
                8,
                state.totalBytes() <= 0L
                        ? NELDLibStyle.DARK_TEXT_MUTED
                        : NELDLibStyle.usedValueColor(Math.round(usage * state.totalBytes()), state.totalBytes()),
                0.9F);
    }

    private void drawUsageDetailLine(GuiGraphics g, Component text, int localY, int color) {
        int x = absX(USAGE_DETAIL_X);
        int y = absY(localY);
        int maxW = Math.max(1, USAGE_DETAIL_W - 2);
        g.enableScissor(x, y - 1, x + maxW, y + USAGE_DETAIL_LINE_H);
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(USAGE_DETAIL_TEXT_SCALE, USAGE_DETAIL_TEXT_SCALE, 1.0F);
        g.drawString(font(), text, 0, 0, color, false);
        g.pose().popPose();
        g.disableScissor();
    }

    private List<Metric> activeTypeMetrics(StorageMetrics metrics) {
        List<Metric> active = new java.util.ArrayList<>();
        for (Metric metric : metrics.types()) {
            if (metric.max() > 0 || metric.totalTypes() > 0) {
                active.add(metric);
            }
        }
        return active;
    }

    private Metric highestPressureMetric(StorageMetrics metrics) {
        Metric highest = null;
        double highestPercent = -1.0D;
        for (Metric metric : activeTypeMetrics(metrics)) {
            double percent = metric.percent();
            if (percent > highestPercent) {
                highestPercent = percent;
                highest = metric;
            }
        }
        return highest;
    }

    private Component storageStatus(Metric highestType) {
        if (highestType == null) {
            return Component.translatable("gui.neoecoae.storage.status.ok");
        }
        if (highestType.percent() >= 0.999D) {
            return Component.translatable("gui.neoecoae.storage.status.capacity_full", highestType.label());
        }
        return Component.translatable("gui.neoecoae.storage.status.ok");
    }

    private int statusColor(Metric highestType) {
        if (highestType != null && highestType.percent() >= 0.999D) {
            return NELDLibStyle.DARK_TEXT_WARNING;
        }
        return NELDLibStyle.DARK_TEXT_MUTED;
    }

    private void renderUsageTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isMouseIn(USAGE_DARK_X, USAGE_DARK_Y, USAGE_DARK_W, USAGE_DARK_H, mouseX, mouseY)) {
            return;
        }
        NEStorageUiState state = currentState();
        graphics.renderComponentTooltip(
                font(),
                List.of(
                        Component.translatable("gui.neoecoae.storage.system_load")
                                .withStyle(ChatFormatting.AQUA),
                        NELDLibValueText.usedTotalComponent(
                                "",
                                NELDLibText.storageBytes(state.totalUsedBytes()),
                                NELDLibText.storageBytes(state.totalBytes()),
                                state.totalUsedBytes(),
                                state.totalBytes(),
                                Component.translatable("gui.neoecoae.storage.bytes_used")
                                        .getString()),
                        Component.translatable(
                                "gui.neoecoae.machine.types_value",
                                NELDLibText.number(state.totalUsedTypes()),
                                NELDLibText.number(state.totalTypes()))),
                mouseX,
                mouseY);
    }

    private void drawStorageGauge(GuiGraphics g, int x, int y, double pct, int color) {
        double clamped = Mth.clamp(pct, 0.0D, 1.0D);
        if (clamped <= 0.0D) {
            return;
        }
        int bodyHeight = STORAGE_GAUGE_H - STORAGE_GAUGE_CAP_H;
        int barHeight = (int) Math.round(bodyHeight * clamped);
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        float red = ((color >>> 16) & 0xFF) / 255.0F;
        float green = ((color >>> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(red, green, blue, alpha);
        g.blit(
                STORAGE_ELEMENTS,
                x,
                y + STORAGE_GAUGE_H - barHeight - STORAGE_GAUGE_CAP_H,
                STORAGE_GAUGE_W,
                STORAGE_GAUGE_CAP_H,
                STORAGE_GAUGE_TOP_U,
                STORAGE_GAUGE_TOP_V,
                STORAGE_GAUGE_W,
                STORAGE_GAUGE_CAP_H,
                STORAGE_ELEMENTS_SIZE,
                STORAGE_ELEMENTS_SIZE);
        int midStart = y + STORAGE_GAUGE_H - barHeight - STORAGE_GAUGE_CAP_H / 2 + 1;
        int midEnd = y + STORAGE_GAUGE_H - STORAGE_GAUGE_CAP_H + STORAGE_GAUGE_CAP_H / 2 + 1;
        for (int drawY = midStart; drawY < midEnd; drawY++) {
            g.blit(
                    STORAGE_ELEMENTS,
                    x,
                    drawY,
                    STORAGE_GAUGE_W,
                    STORAGE_GAUGE_MID_H,
                    STORAGE_GAUGE_MID_U,
                    STORAGE_GAUGE_MID_V,
                    STORAGE_GAUGE_W,
                    STORAGE_GAUGE_MID_H,
                    STORAGE_ELEMENTS_SIZE,
                    STORAGE_ELEMENTS_SIZE);
        }
        g.blit(
                STORAGE_ELEMENTS,
                x,
                y + STORAGE_GAUGE_H - STORAGE_GAUGE_CAP_H,
                STORAGE_GAUGE_W,
                STORAGE_GAUGE_CAP_H,
                STORAGE_GAUGE_BOTTOM_U,
                STORAGE_GAUGE_BOTTOM_V,
                STORAGE_GAUGE_W,
                STORAGE_GAUGE_CAP_H,
                STORAGE_ELEMENTS_SIZE,
                STORAGE_ELEMENTS_SIZE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static double totalUsagePercent(NEStorageUiState state) {
        return NELDLibMachineWidget.percent(state.totalUsedBytes(), state.totalBytes());
    }

    private static int gaugeColor(NEStorageUiState state) {
        return storageGaugeColor(totalUsagePercent(state));
    }

    private static int storageGaugeColor(double pct) {
        double amount = Mth.clamp(pct, 0.0D, 1.0D);
        if (amount < 0.5D) {
            return NELDLibStyle.lerpColor(0xBF00FF00, 0xBFFFFF00, amount / 0.5D);
        }
        return NELDLibStyle.lerpColor(0xBFFFFF00, 0xBFFF0000, (amount - 0.5D) / 0.5D);
    }

    private double animatedUsagePercent(double target) {
        long now = Util.getMillis();
        if (usageAnimationTarget < 0.0D) {
            usageAnimationStart = 0.0D;
            usageAnimationTarget = target;
            usageAnimationStartMs = now;
        } else if (Math.abs(usageAnimationTarget - target) > USAGE_ANIMATION_EPSILON) {
            usageAnimationStart = currentAnimatedUsagePercent(now);
            usageAnimationTarget = target;
            usageAnimationStartMs = now;
        }
        return currentAnimatedUsagePercent(now);
    }

    private double currentAnimatedUsagePercent(long now) {
        double elapsed = Mth.clamp((double) (now - usageAnimationStartMs) / (double) USAGE_ANIMATION_MS, 0.0D, 1.0D);
        double eased = cubicBezierEase(elapsed);
        return usageAnimationStart + (usageAnimationTarget - usageAnimationStart) * eased;
    }

    private static double cubicBezierEase(double progress) {
        double t = Mth.clamp(progress, 0.0D, 1.0D);
        for (int i = 0; i < 5; i++) {
            double x = cubicBezier(t, 0.25D, 0.25D);
            double slope = cubicBezierSlope(t, 0.25D, 0.25D);
            if (slope == 0.0D) {
                break;
            }
            t = Mth.clamp(t - (x - progress) / slope, 0.0D, 1.0D);
        }
        return cubicBezier(t, 0.1D, 1.0D);
    }

    private static double cubicBezier(double t, double p1, double p2) {
        double inverse = 1.0D - t;
        return 3.0D * inverse * inverse * t * p1 + 3.0D * inverse * t * t * p2 + t * t * t;
    }

    private static double cubicBezierSlope(double t, double p1, double p2) {
        double inverse = 1.0D - t;
        return 3.0D * inverse * inverse * p1 + 6.0D * inverse * t * (p2 - p1) + 3.0D * t * t * (1.0D - p2);
    }

    private static long maxMatrixUsed(NEStorageUiState state) {
        long used = 0L;
        long total = 0L;
        double maxPct = -1.0D;
        for (var matrix : state.matrixStates()) {
            if (!matrix.hasMatrix() || matrix.totalBytes() <= 0L) {
                continue;
            }
            double pct = NELDLibMachineWidget.percent(matrix.usedBytes(), matrix.totalBytes());
            if (pct > maxPct) {
                maxPct = pct;
                used = matrix.usedBytes();
                total = matrix.totalBytes();
            }
        }
        return total <= 0L ? 0L : used;
    }

    private static long maxMatrixTotal(NEStorageUiState state) {
        long total = 0L;
        double maxPct = -1.0D;
        for (var matrix : state.matrixStates()) {
            if (!matrix.hasMatrix() || matrix.totalBytes() <= 0L) {
                continue;
            }
            double pct = NELDLibMachineWidget.percent(matrix.usedBytes(), matrix.totalBytes());
            if (pct > maxPct) {
                maxPct = pct;
                total = matrix.totalBytes();
            }
        }
        return total;
    }

    private static int idleMatrixCount(NEStorageUiState state) {
        int count = 0;
        for (var matrix : state.matrixStates()) {
            if (matrix.hasMatrix() && matrix.usedBytes() <= 0L && matrix.usedTypes() <= 0L) {
                count++;
            }
        }
        return count;
    }

    private void drawPlainLine(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font(), text, x, y, color, false);
    }

    private int NELDLibUiTitleX() {
        return 8;
    }

    private int NELDLibUiTitleY() {
        return 8;
    }

    private void restoreScrollState() {
        scrollKey().map(SCROLL_MEMORY::get).ifPresent(snapshot -> {
            leftScrollPixels = snapshot.leftScrollPixels();
        });
    }

    private void rememberScrollState() {
        scrollKey().ifPresent(key -> SCROLL_MEMORY.put(key, new ScrollSnapshot(leftScrollPixels)));
    }

    private Optional<ScrollKey> scrollKey() {
        if (storage.getLevel() == null) {
            return Optional.empty();
        }
        return Optional.of(new ScrollKey(
                player.getUUID(),
                storage.getLevel().dimension().location(),
                storage.getBlockPos().immutable()));
    }

    private record ScrollKey(UUID playerId, ResourceLocation dimension, BlockPos pos) {}

    private record ScrollSnapshot(double leftScrollPixels) {}
}
