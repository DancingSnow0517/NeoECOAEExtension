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
import net.minecraftforge.fml.ModList;

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

    private static final int CHEMICAL_ROW_Y = 30;
    private static final int CHEMICAL_ROW_W = 188;
    private static final int CHEMICAL_ROW_H = 34;
    private static final int CHEMICAL_ROW_GAP = 6;

    private static final int COLUMN_PANEL_X = 210;
    private static final int COLUMN_PANEL_W = 100;
    private static final int COLUMN_Y = 47;
    private static final int COLUMN_H = 110;
    private static final int CHEMICAL_COLUMN_Y = 45;
    private static final int CHEMICAL_COLUMN_H = 104;

    private static final double ANIMATION_SPEED = 0.16D;

    private boolean hasStorageState;
    private NEStorageUiState storageState;

    private double animatedEnergyPct = -1.0D;
    private double animatedItemPct = -1.0D;
    private double animatedFluidPct = -1.0D;
    private double animatedChemicalPct = -1.0D;

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
        boolean chemicalMode = hasChemicalStorageIntegration();
        NEStorageUiState s = resolveStorageState();
        StorageMetrics metrics = buildStorageMetrics(s);

        animatedEnergyPct = animateTo(animatedEnergyPct, metrics.energy().percent());
        animatedItemPct = animateTo(animatedItemPct, metrics.items().percent());
        animatedFluidPct = animateTo(animatedFluidPct, metrics.fluids().percent());
        animatedChemicalPct = animateTo(animatedChemicalPct, metrics.chemicals().percent());

        if (chemicalMode) {
            int y = CHEMICAL_ROW_Y;
            drawMetricRow(guiGraphics, metrics.energy(), ROW_X, y, CHEMICAL_ROW_W, CHEMICAL_ROW_H, animatedEnergyPct);
            y += CHEMICAL_ROW_H + CHEMICAL_ROW_GAP;
            drawMetricRow(guiGraphics, metrics.items(), ROW_X, y, CHEMICAL_ROW_W, CHEMICAL_ROW_H, animatedItemPct);
            y += CHEMICAL_ROW_H + CHEMICAL_ROW_GAP;
            drawMetricRow(guiGraphics, metrics.fluids(), ROW_X, y, CHEMICAL_ROW_W, CHEMICAL_ROW_H, animatedFluidPct);
            y += CHEMICAL_ROW_H + CHEMICAL_ROW_GAP;
            drawMetricRow(guiGraphics, metrics.chemicals(), ROW_X, y, CHEMICAL_ROW_W, CHEMICAL_ROW_H, animatedChemicalPct);
            drawPanelFooterBar(guiGraphics, ROW_X, 188, CHEMICAL_ROW_W);

            drawBoundMetricColumns(guiGraphics, chemicalMode,
                new Metric[]{metrics.items(), metrics.fluids(), metrics.chemicals()},
                new double[]{animatedItemPct, animatedFluidPct, animatedChemicalPct});
            drawLabelBoolean(guiGraphics,
                Component.translatable("gui.neoecoae.machine.formed"),
                s.formed(), ROW_X, 198);
        } else {
            drawMetricRow(guiGraphics, metrics.energy(), ROW_X, ROW_Y, ROW_W, ROW_H, animatedEnergyPct);
            drawMetricRow(guiGraphics, metrics.items(), ROW_X, ROW_Y + ROW_H + ROW_GAP, ROW_W, ROW_H, animatedItemPct);
            drawMetricRow(guiGraphics, metrics.fluids(), ROW_X, ROW_Y + (ROW_H + ROW_GAP) * 2, ROW_W, ROW_H, animatedFluidPct);
            drawPanelFooterBar(guiGraphics, ROW_X, 181, ROW_W);

            drawBoundMetricColumns(guiGraphics, chemicalMode,
                new Metric[]{metrics.items(), metrics.fluids()},
                new double[]{animatedItemPct, animatedFluidPct});
            drawLabelBoolean(guiGraphics,
                Component.translatable("gui.neoecoae.machine.formed"),
                s.formed(), ROW_X, 191);
        }
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
        NEStorageUiTypeState chemicalState = findChemicalTypeState(types);

        if (itemState == null && !types.isEmpty()) {
            itemState = types.get(0);
        }

        Metric energy = new Metric(
            Component.literal("能量"),
            s.storedEnergy(), s.maxEnergy(),
            0xFF4C72D8,
            false,
            false
        );
        Metric items = createTypeMetric(itemState, Component.literal("物品"), true, 0xFF3A68B6);
        Metric fluids = createTypeMetric(fluidState, Component.literal("流体"), true, 0xFF3A8FD6);
        Metric chemicals = createTypeMetric(chemicalState, Component.literal("化学品"), true, 0xFF9A57E6);
        return new StorageMetrics(energy, items, fluids, chemicals);
    }

    private Metric createTypeMetric(NEStorageUiTypeState state, Component fallbackLabel,
                                    boolean dangerHigh, int accentColor) {
        if (state == null) {
            return new Metric(fallbackLabel, 0, 0, accentColor, dangerHigh, true);
        }
        return new Metric(fallbackLabel, state.usedBytes(), state.totalBytes(), accentColor, dangerHigh, true);
    }

    private void drawMetricRow(GuiGraphics g, Metric metric, int x, int y, int w, int h, double animatedPct) {
        NENativeAe2StyleRenderer.drawAeInsetRect(g, x, y, w, h, 0xFFC9C9C9);

        int labelColor = NENativeUiConstants.MACHINE_TEXT_PRIMARY;
        int valueColor = NENativeUiConstants.MACHINE_TEXT_VALUE;

        g.drawString(font, metric.label(), x + 8, y + 6, labelColor, false);

        String percent = metric.max() <= 0 ? "N/A" : formatPercent(metric.percent());
        int percentColor = metric.max() <= 0
            ? NENativeUiConstants.MACHINE_TEXT_MUTED
            : metricColor(metric, metric.percent());
        g.drawString(font, Component.literal(percent),
            x + w - 8 - font.width(percent), y + 6,
            percentColor, false);

        String valueText = formatMetricNumber(metric.used()) + " / " + formatMetricNumber(metric.max());
        int percentReserve = Math.max(34, font.width(percent) + 8);
        int maxValueWidth = w - 16 - percentReserve;
        if (font.width(valueText) > maxValueWidth) {
            valueText = font.plainSubstrByWidth(valueText, maxValueWidth - font.width("…")) + "…";
        }
        g.drawString(font, Component.literal(valueText), x + 8, y + 19, valueColor, false);

        drawHorizontalMetricBar(g, x + 8, y + h - 9, w - 16, 6, animatedPct, metric);
        g.fill(x + 8, y + h - 2, x + w - 8, y + h - 1, 0x40FFFFFF);
        g.fill(x + 8, y + h - 11, x + w - 8, y + h - 10, 0x403F3F3F);
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

    private void drawBoundMetricColumns(GuiGraphics g, boolean chemicalMode, Metric[] metrics, double[] animatedValues) {
        int count = metrics.length;
        int columnW = chemicalMode ? 28 : 38;
        int columnH = chemicalMode ? CHEMICAL_COLUMN_H : COLUMN_H;
        int gap = chemicalMode ? 8 : 20;
        int totalW = columnW * count + gap * (count - 1);
        int startX = COLUMN_PANEL_X + (COLUMN_PANEL_W - totalW) / 2;
        int y = chemicalMode ? CHEMICAL_COLUMN_Y : COLUMN_Y;

        for (int i = 0; i < count; i++) {
            int x = startX + i * (columnW + gap);
            drawBoundMetricColumn(g, metrics[i], x, y, columnW, columnH, animatedValues[i]);
        }

        drawPanelFooterBar(g, startX - 4, y + columnH + 31, totalW + 8);
    }

    private void drawBoundMetricColumn(GuiGraphics g, Metric metric, int x, int y, int w, int h, double pct) {
        drawCenteredComponent(g, metric.label(), x - 6, y - 13, w + 12, NENativeUiConstants.MACHINE_TEXT_PRIMARY);
        NENativeAe2StyleRenderer.drawAeInsetRect(g, x, y, w, h, 0xFF2F3A43);

        int ix = x + 5;
        int iy = y + 6;
        int iw = w - 10;
        int ih = h - 12;
        int fillH = Mth.clamp((int) Math.round(ih * pct), 0, ih);
        int fillY = iy + ih - fillH;

        // Inner dark glass body.
        g.fill(ix, iy, ix + iw, iy + ih, 0xA0141A20);
        g.fill(ix + 1, iy + 3, ix + 3, iy + ih - 3, 0x55FFFFFF);
        g.fill(ix + iw - 3, iy + 3, ix + iw - 1, iy + ih - 3, 0x30202020);

        if (fillH > 0) {
            int color = metricColor(metric, pct);
            g.fill(ix, fillY, ix + iw, iy + ih, color);
            g.fill(ix, fillY, ix + iw, Math.min(fillY + 2, iy + ih), 0x70FFFFFF);
            g.fill(ix, iy + ih - 2, ix + iw, iy + ih, 0x70000000);
        }

        // White side ticks, matching the 1.12.2 vertical gauge feel.
        for (int i = 1; i < 6; i++) {
            int tickY = iy + ih - Math.round(ih * i / 6.0F);
            g.fill(ix - 2, tickY, ix + 3, tickY + 1, 0xCCFFFFFF);
            g.fill(ix + iw - 3, tickY, ix + iw + 2, tickY + 1, 0xCCFFFFFF);
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
        drawCenteredString(g, formatPercent(metric.percent()), x - 2, percentBoxY + 5, w + 4,
            NENativeUiConstants.MACHINE_TEXT_VALUE);
    }

    private void drawPanelFooterBar(GuiGraphics g, int x, int y, int w) {
        NENativeAe2StyleRenderer.drawAeInsetRect(g, x, y, w, 7, 0xFF505760);
        g.fill(x + 3, y + 2, x + w - 3, y + 3, 0x704F7FB8);
        g.fill(x + 3, y + 3, x + w - 3, y + 5, 0xFF2D4774);
        g.fill(x + 3, y + 5, x + w - 3, y + 6, 0x80000000);
    }

    private void drawCenteredString(GuiGraphics g, String text, int x, int y, int w, int color) {
        g.drawString(font, Component.literal(text), x + (w - font.width(text)) / 2, y, color, false);
    }

    private void drawCenteredComponent(GuiGraphics g, Component text, int x, int y, int w, int color) {
        g.drawString(font, text, x + (w - font.width(text)) / 2, y, color, false);
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

    private static boolean hasChemicalStorageIntegration() {
        ModList mods = ModList.get();
        return mods.isLoaded("mekanism")
            && (mods.isLoaded("appmek")
            || mods.isLoaded("applied_mekanistics")
            || mods.isLoaded("appliedmekanistics"));
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

    private static NEStorageUiTypeState findChemicalTypeState(List<NEStorageUiTypeState> types) {
        String[] needles = new String[]{"chemical", "chem", "gas", "infuse", "infusion", "pigment", "slurry", "mekanism"};
        for (String needle : needles) {
            NEStorageUiTypeState state = findTypeState(types, needle);
            if (state != null) {
                return state;
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

    private record StorageMetrics(Metric energy, Metric items, Metric fluids, Metric chemicals) {
    }

    private record Metric(Component label, long used, long max, int accentColor,
                          boolean dangerHigh, boolean byteBased) {
        private double percent() {
            return NEStorageControllerScreen.percent(used, max);
        }
    }
}
