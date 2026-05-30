package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStorageControllerMenu;
import cn.dancingsnow.neoecoae.network.NEStorageUiState;
import cn.dancingsnow.neoecoae.network.NEStorageUiTypeState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Screen for the ECO Storage Controller with live read-only status.
 * <p>
 * Primary display path: S2C {@link NEStorageUiState} pushed from the server
 * menu tick. Storage capacity is shown per cell type (Items, Fluids, etc.).
 * Before the first packet arrives the screen shows a brief fallback read from
 * the client-side BE (opening-time snapshot, not live).
 * </p>
 */
public class NEStorageControllerScreen extends NEBaseMachineScreen<NEStorageControllerMenu> {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private static final int ROW_X = 14;
    private static final int ROW_Y = 34;
    private static final int ROW_W = 190;
    private static final int ROW_H = 42;
    private static final int ROW_GAP = 8;

    private static final int COLUMN_X = 225;
    private static final int COLUMN_Y = 39;
    private static final int COLUMN_W = 38;
    private static final int COLUMN_H = 118;

    private static final double ANIMATION_SPEED = 0.16D;

    private boolean hasStorageState;
    private NEStorageUiState storageState;

    private double animatedEnergyPct = -1.0D;
    private double animatedItemPct = -1.0D;
    private double animatedFluidPct = -1.0D;
    private double animatedColumnPct = -1.0D;

    public NEStorageControllerScreen(NEStorageControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.STORAGE_CONTROLLER);
        this.imageWidth = 320;
        this.imageHeight = 220;
        this.storageState = NEStorageUiState.empty(menu.getMachinePos());
    }

    /** Called from the network thread via {@link cn.dancingsnow.neoecoae.client.NEClientUiPacketHandlers}. */
    public void setStorageUiState(NEStorageUiState state) {
        this.hasStorageState = true;
        this.storageState = state;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void renderAdditionalLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        NEStorageUiState s = resolveStorageState();
        StorageMetrics metrics = buildStorageMetrics(s);

        animatedEnergyPct = animateTo(animatedEnergyPct, metrics.energy().percent());
        animatedItemPct = animateTo(animatedItemPct, metrics.items().percent());
        animatedFluidPct = animateTo(animatedFluidPct, metrics.fluids().percent());

        int relMouseX = mouseX - leftPos;
        int relMouseY = mouseY - topPos;
        Metric focused = metrics.totalStorage();
        if (isInside(relMouseX, relMouseY, ROW_X, ROW_Y, ROW_W, ROW_H)) {
            focused = metrics.energy();
        } else if (isInside(relMouseX, relMouseY, ROW_X, ROW_Y + ROW_H + ROW_GAP, ROW_W, ROW_H)) {
            focused = metrics.items();
        } else if (isInside(relMouseX, relMouseY, ROW_X, ROW_Y + (ROW_H + ROW_GAP) * 2, ROW_W, ROW_H)) {
            focused = metrics.fluids();
        }
        animatedColumnPct = animateTo(animatedColumnPct, focused.percent());

        drawMetricRow(guiGraphics, metrics.energy(), ROW_X, ROW_Y, ROW_W, ROW_H, animatedEnergyPct);
        drawMetricRow(guiGraphics, metrics.items(), ROW_X, ROW_Y + ROW_H + ROW_GAP, ROW_W, ROW_H, animatedItemPct);
        drawMetricRow(guiGraphics, metrics.fluids(), ROW_X, ROW_Y + (ROW_H + ROW_GAP) * 2, ROW_W, ROW_H, animatedFluidPct);

        drawDynamicStorageColumn(guiGraphics, focused, COLUMN_X, COLUMN_Y, COLUMN_W, COLUMN_H, animatedColumnPct);

        drawLabelBoolean(guiGraphics,
            Component.translatable("gui.neoecoae.machine.formed"),
            s.formed(), ROW_X, 190);
    }

    private NEStorageUiState resolveStorageState() {
        if (hasStorageState) {
            return this.storageState;
        }

        // Opening-time fallback: read client BE once while waiting for the
        // first S2C packet. Not used for live refresh.
        ECOStorageSystemBlockEntity be = getStorageBE();
        if (be != null) {
            // Wrap legacy BE getters into a single "unknown" type row.
            NEStorageUiTypeState fallbackType = new NEStorageUiTypeState(
                ResourceLocation.fromNamespaceAndPath("neoecoae", "legacy"),
                "Storage",
                be.getTotalUsedTypes(), be.getTotalTypes(),
                be.getTotalUsedBytes(), be.getTotalBytes()
            );
            return new NEStorageUiState(
                menu.getMachinePos(),
                Collections.singletonList(fallbackType),
                be.getStoredEnergy(), be.getMaxEnergy(),
                be.isFormed()
            );
        }
        return this.storageState;
    }

    private StorageMetrics buildStorageMetrics(NEStorageUiState s) {
        List<NEStorageUiTypeState> types = s.typeStates();
        NEStorageUiTypeState itemState = findTypeState(types, "item");
        NEStorageUiTypeState fluidState = findTypeState(types, "fluid");

        if (itemState == null && !types.isEmpty()) {
            itemState = types.get(0);
        }
        if (fluidState == null) {
            fluidState = firstDifferentType(types, itemState);
        }

        Metric energy = new Metric(
            Component.translatable("gui.neoecoae.common.energy"),
            s.storedEnergy(), s.maxEnergy(),
            0xFF4C72D8,
            false,
            false
        );
        Metric items = createTypeMetric(itemState, Component.literal("Items"), true, 0xFF3A68B6);
        Metric fluids = createTypeMetric(fluidState, Component.literal("Fluids"), true, 0xFF3A8FD6);
        Metric totalStorage = new Metric(
            Component.literal("Storage"),
            s.totalUsedBytes(), s.totalBytes(),
            0xFF5A72D8,
            true,
            false
        );
        return new StorageMetrics(energy, items, fluids, totalStorage);
    }

    private Metric createTypeMetric(NEStorageUiTypeState state, Component fallbackLabel,
                                    boolean dangerHigh, int accentColor) {
        if (state == null) {
            return new Metric(fallbackLabel, 0, 0, accentColor, dangerHigh, true);
        }
        Component label = Component.literal(state.displayName());
        return new Metric(label, state.usedBytes(), state.totalBytes(), accentColor, dangerHigh, true);
    }

    private void drawMetricRow(GuiGraphics g, Metric metric, int x, int y, int w, int h, double animatedPct) {
        NENativeAe2StyleRenderer.drawAeInsetRect(g, x, y, w, h, 0xFFC9C9C9);

        int labelColor = NENativeUiConstants.MACHINE_TEXT_PRIMARY;
        int valueColor = NENativeUiConstants.MACHINE_TEXT_VALUE;
        int mutedColor = NENativeUiConstants.MACHINE_TEXT_MUTED;

        g.drawString(font, metric.label(), x + 8, y + 7, labelColor, false);

        String percent = formatPercent(metric.percent());
        g.drawString(font, Component.literal(percent),
            x + w - 8 - font.width(percent), y + 7,
            metricColor(metric, metric.percent()), false);

        String valueText = formatMetricNumber(metric.used()) + " / " + formatMetricNumber(metric.max());
        int maxValueWidth = w - 16;
        if (font.width(valueText) > maxValueWidth) {
            valueText = font.plainSubstrByWidth(valueText, maxValueWidth - font.width("…")) + "…";
        }
        g.drawString(font, Component.literal(valueText), x + 8, y + 20, valueColor, false);

        drawHorizontalMetricBar(g, x + 8, y + h - 9, w - 16, 6, animatedPct, metric);
        g.fill(x + 8, y + h - 2, x + w - 8, y + h - 1, 0x40FFFFFF);
        g.fill(x + 8, y + 32, x + w - 8, y + 33, 0x403F3F3F);

        if (metric.max() <= 0) {
            g.drawString(font, Component.literal("N/A"),
                x + w - 8 - font.width("N/A"), y + 20,
                mutedColor, false);
        }
    }

    private void drawHorizontalMetricBar(GuiGraphics g, int x, int y, int w, int h, double pct, Metric metric) {
        NENativeAe2StyleRenderer.drawAeInsetRect(g, x, y, w, h, 0xFF5E666E);
        int ix = x + 2;
        int iy = y + 2;
        int iw = w - 4;
        int ih = h - 4;
        int fillW = Mth.clamp((int) Math.round(iw * pct), 0, iw);
        if (fillW > 0) {
            int color = metricColor(metric, pct);
            g.fill(ix, iy, ix + fillW, iy + ih, color);
            g.fill(ix, iy, ix + fillW, iy + 1, 0x80FFFFFF);
        }
    }

    private void drawDynamicStorageColumn(GuiGraphics g, Metric focused, int x, int y, int w, int h, double pct) {
        g.drawString(font, focused.label(), x - 3, y - 13, NENativeUiConstants.MACHINE_TEXT_PRIMARY, false);
        NENativeAe2StyleRenderer.drawAeInsetRect(g, x, y, w, h, 0xFF2F3A43);

        int ix = x + 5;
        int iy = y + 6;
        int iw = w - 10;
        int ih = h - 12;
        int fillH = Mth.clamp((int) Math.round(ih * pct), 0, ih);
        int fillY = iy + ih - fillH;

        // Inner dark glass body.
        g.fill(ix, iy, ix + iw, iy + ih, 0xA0141A20);
        g.fill(ix + 2, iy + 3, ix + 4, iy + ih - 3, 0x55FFFFFF);
        g.fill(ix + iw - 4, iy + 3, ix + iw - 2, iy + ih - 3, 0x30202020);

        if (fillH > 0) {
            int color = metricColor(focused, pct);
            g.fill(ix, fillY, ix + iw, iy + ih, color);
            g.fill(ix, fillY, ix + iw, Math.min(fillY + 2, iy + ih), 0x70FFFFFF);
            g.fill(ix, iy + ih - 2, ix + iw, iy + ih, 0x70000000);
        }

        // White side ticks, matching the 1.12.2 vertical gauge feel.
        for (int i = 1; i < 6; i++) {
            int tickY = iy + ih - Math.round(ih * i / 6.0F);
            g.fill(ix - 2, tickY, ix + 4, tickY + 1, 0xCCFFFFFF);
            g.fill(ix + iw - 4, tickY, ix + iw + 2, tickY + 1, 0xCCFFFFFF);
        }

        // Outer dark braces.
        g.fill(x + 2, y + 2, x + w - 2, y + 5, 0xCC1B1F24);
        g.fill(x + 2, y + h - 5, x + w - 2, y + h - 2, 0xCC1B1F24);
        g.fill(x + 3, y + 3, x + 8, y + 10, 0xAA0D1115);
        g.fill(x + w - 8, y + 3, x + w - 3, y + 10, 0xAA0D1115);
        g.fill(x + 3, y + h - 10, x + 8, y + h - 3, 0xAA0D1115);
        g.fill(x + w - 8, y + h - 10, x + w - 3, y + h - 3, 0xAA0D1115);

        int percentBoxY = y + h + 6;
        NENativeAe2StyleRenderer.drawAeInsetRect(g, x - 2, percentBoxY, w + 4, 18, 0xFF202326);
        drawCenteredString(g, formatPercent(focused.percent()), x - 2, percentBoxY + 5, w + 4,
            NENativeUiConstants.MACHINE_TEXT_VALUE);
    }

    private void drawCenteredString(GuiGraphics g, String text, int x, int y, int w, int color) {
        g.drawString(font, Component.literal(text), x + (w - font.width(text)) / 2, y, color, false);
    }

    private ECOStorageSystemBlockEntity getStorageBE() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(menu.getMachinePos());
        if (be instanceof ECOStorageSystemBlockEntity storage) {
            return storage;
        }
        return null;
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static NEStorageUiTypeState findTypeState(List<NEStorageUiTypeState> types, String needle) {
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        for (NEStorageUiTypeState ts : types) {
            String path = ts.typeId().getPath().toLowerCase(Locale.ROOT);
            String name = ts.displayName().toLowerCase(Locale.ROOT);
            if (path.contains(lowerNeedle) || name.contains(lowerNeedle)) {
                return ts;
            }
        }
        return null;
    }

    private static NEStorageUiTypeState firstDifferentType(List<NEStorageUiTypeState> types,
                                                           NEStorageUiTypeState excluded) {
        for (NEStorageUiTypeState ts : types) {
            if (ts != excluded) {
                return ts;
            }
        }
        return null;
    }

    private static double animateTo(double current, double target) {
        if (current < 0.0D) {
            return target;
        }
        return Mth.lerp(ANIMATION_SPEED, current, target);
    }

    private static double percent(long used, long max) {
        if (max <= 0) {
            return 0.0D;
        }
        return Mth.clamp((double) used / (double) max, 0.0D, 1.0D);
    }

    private static int metricColor(Metric metric, double pct) {
        double danger = metric.dangerHigh() ? pct : 1.0D - pct;
        if (danger < 0.5D) {
            return lerpColor(0xFF27D852, 0xFFFFD33D, danger / 0.5D);
        }
        return lerpColor(0xFFFFD33D, 0xFFFF4A26, (danger - 0.5D) / 0.5D);
    }

    private static int lerpColor(int start, int end, double t) {
        t = Mth.clamp(t, 0.0D, 1.0D);
        int a = (int) Mth.lerp(t, (start >>> 24) & 0xFF, (end >>> 24) & 0xFF);
        int r = (int) Mth.lerp(t, (start >>> 16) & 0xFF, (end >>> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (start >>> 8) & 0xFF, (end >>> 8) & 0xFF);
        int b = (int) Mth.lerp(t, start & 0xFF, end & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String formatMetricNumber(long value) {
        return NUMBER_FORMAT.format(value);
    }

    private static String formatPercent(double pct) {
        return String.format(Locale.US, "%.1f%%", pct * 100.0D);
    }

    public NEStorageControllerMenu getMenu() {
        return menu;
    }

    private record StorageMetrics(Metric energy, Metric items, Metric fluids, Metric totalStorage) {
    }

    private record Metric(Component label, long used, long max, int accentColor,
                          boolean dangerHigh, boolean byteBased) {
        private double percent() {
            return NEStorageControllerScreen.percent(used, max);
        }
    }
}
