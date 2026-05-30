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

    private static final int DARK_PANEL_OUTER = 0xFF15191E;
    private static final int DARK_PANEL_INNER = 0xFF20272E;
    private static final int DARK_PANEL_SOFT = 0xFF263039;

    private static final int DARK_TEXT_PRIMARY = 0xFFE8EEF2;
    private static final int DARK_TEXT_VALUE = 0xFF9DBEFF;
    private static final int DARK_TEXT_MUTED = 0xFF9AA3AA;
    private static final int DARK_TEXT_SUCCESS = 0xFF4FE083;
    private static final int DARK_TEXT_ERROR = 0xFFFF6A5A;

    private static final int LEFT_PANEL_X = 9;
    private static final int LEFT_PANEL_Y = 30;
    private static final int LEFT_PANEL_W = 198;
    private static final int LEFT_PANEL_H = 158;

    private static final int RIGHT_PANEL_X = 218;
    private static final int RIGHT_PANEL_Y = 30;
    private static final int RIGHT_PANEL_W = 130;
    private static final int RIGHT_PANEL_H = 158;

    private static final int ROW_SIDE_PADDING = 10;
    private static final int ROW_TOP_PADDING = 14;
    private static final int ROW_X = LEFT_PANEL_X + 10;
    private static final int ROW_W = LEFT_PANEL_W - 20;
    private static final int ROW_H = 30;
    private static final int ROW_GAP = 4;

    private static final int COLUMN_Y = 58;
    private static final int COLUMN_H = 88;
    private static final int COLUMN_PERCENT_GAP = 7;
    private static final int COLUMN_PERCENT_H = 17;

    private static final int FORMED_BAR_Y = 198;
    private static final int FORMED_BAR_H = 16;
    private static final double ANIMATION_SPEED = 0.16D;

    private boolean hasStorageState;
    private NEStorageUiState storageState;

    private double animatedEnergyPct = -1.0D;
    private double animatedItemPct = -1.0D;
    private double animatedFluidPct = -1.0D;
    private double animatedChemicalPct = -1.0D;

    public NEStorageControllerScreen(NEStorageControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, NEMachineScreenConfig.STORAGE_CONTROLLER);
        this.imageWidth = 358;
        this.imageHeight = 220;
        this.storageState = NEStorageUiState.empty(menu.getMachinePos());
    }

    /**
     * Called from the network thread via
     * {@link cn.dancingsnow.neoecoae.client.NEClientUiPacketHandlers}.
     */
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

        drawInsetGroupPanel(guiGraphics, LEFT_PANEL_X, LEFT_PANEL_Y, LEFT_PANEL_W, LEFT_PANEL_H);
        drawInsetGroupPanel(guiGraphics, RIGHT_PANEL_X, RIGHT_PANEL_Y, RIGHT_PANEL_W, RIGHT_PANEL_H);

        int y = LEFT_PANEL_Y + ROW_TOP_PADDING;
        drawMetricRow(guiGraphics, metrics.energy(), ROW_X, y, ROW_W, ROW_H, animatedEnergyPct);
        y += ROW_H + ROW_GAP;
        drawMetricRow(guiGraphics, metrics.items(), ROW_X, y, ROW_W, ROW_H, animatedItemPct);
        y += ROW_H + ROW_GAP;
        drawMetricRow(guiGraphics, metrics.fluids(), ROW_X, y, ROW_W, ROW_H, animatedFluidPct);

        if (chemicalMode) {
            y += ROW_H + ROW_GAP;
            drawMetricRow(guiGraphics, metrics.chemicals(), ROW_X, y, ROW_W, ROW_H, animatedChemicalPct);
            drawBoundMetricColumns(guiGraphics,
                    new Metric[] { metrics.items(), metrics.fluids(), metrics.chemicals() },
                    new double[] { animatedItemPct, animatedFluidPct, animatedChemicalPct });
        } else {
            drawBoundMetricColumns(guiGraphics,
                    new Metric[] { metrics.items(), metrics.fluids() },
                    new double[] { animatedItemPct, animatedFluidPct });
        }

        drawFormedStatusBar(guiGraphics,
                s.formed(),
                LEFT_PANEL_X,
                FORMED_BAR_Y,
                RIGHT_PANEL_X + RIGHT_PANEL_W - LEFT_PANEL_X,
                FORMED_BAR_H);
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
                    be.getTotalUsedBytes(), be.getTotalBytes());
            return new NEStorageUiState(
                    menu.getMachinePos(),
                    Collections.singletonList(fallbackType),
                    be.getStoredEnergy(), be.getMaxEnergy(),
                    be.isFormed());
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
                0xFF5374C8,
                false,
                false);
        Metric items = createTypeMetric(itemState, Component.literal("物品"), true, 0xFF43B678);
        Metric fluids = createTypeMetric(fluidState, Component.literal("流体"), true, 0xFF3A8FD6);
        Metric chemicals = createTypeMetric(chemicalState, Component.literal("化学品"), true, 0xFF9A6AE8);
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
        drawMetricLane(g, metric, x, y, w, h);

        int labelColor = DARK_TEXT_PRIMARY;
        int valueColor = DARK_TEXT_VALUE;

        int labelY = y + 3;
        int valueY = y + 14;
        g.drawString(font, metric.label(), x + 8, labelY, labelColor, false);

        String valueText = formatMetricNumber(metric.used()) + " / " + formatMetricNumber(metric.max());
        int valueMaxWidth = w - 16;
        if (font.width(valueText) > valueMaxWidth) {
            valueText = font.plainSubstrByWidth(valueText, valueMaxWidth - font.width("…")) + "…";
        }
        g.drawString(font, Component.literal(valueText), x + 8, valueY, valueColor, false);

        drawHorizontalMetricBar(g, x + 8, y + h - 6, w - 16, 5, animatedPct, metric);
    }

    private void drawHorizontalMetricBar(GuiGraphics g, int x, int y, int w, int h, double pct, Metric metric) {
        NENativeAe2StyleRenderer.drawAeInsetRect(g, x, y, w, h, 0xFF14191F);
        int ix = x + 2;
        int iy = y + 2;
        int iw = w - 4;
        int ih = h - 4;
        int fillW = Mth.clamp((int) Math.round(iw * pct), 0, iw);
        if (fillW > 0) {
            int color = metricColor(metric, pct);
            g.fill(ix, iy, ix + fillW, iy + ih, color);
            g.fill(ix, iy, ix + fillW, iy + 1, 0x90FFFFFF);
        }
    }

    private void drawBoundMetricColumns(GuiGraphics g, Metric[] metrics, double[] animatedValues) {
        int count = metrics.length;
        int columnW = count == 3 ? 30 : 38;
        int gap = count == 3 ? 10 : 20;
        int totalW = columnW * count + gap * (count - 1);
        int startX = RIGHT_PANEL_X + (RIGHT_PANEL_W - totalW) / 2;

        for (int i = 0; i < count; i++) {
            int x = startX + i * (columnW + gap);
            drawBoundMetricColumn(g, metrics[i], x, COLUMN_Y, columnW, COLUMN_H, animatedValues[i]);
        }
    }

    private void drawBoundMetricColumn(GuiGraphics g, Metric metric, int x, int y, int w, int h, double pct) {
        drawCenteredComponent(g, metric.label(), x - 8, y - 14, w + 16, DARK_TEXT_PRIMARY);
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

        int percentY = y + h + COLUMN_PERCENT_GAP;
        int percentColor = metric.max() <= 0 ? DARK_TEXT_MUTED : metricColor(metric, pct);
        String percentText = metric.max() <= 0 ? "N/A" : formatPercent(metric.percent());
        NENativeAe2StyleRenderer.drawAeInsetRect(g, x - 2, percentY, w + 4, COLUMN_PERCENT_H, 0xFF202326);
        drawCenteredString(g, percentText, x - 2, percentY + 5, w + 4, percentColor);
    }

    private void drawInsetGroupPanel(GuiGraphics g, int x, int y, int w, int h) {
        // Pseudo-rounded inset panel: clipped 2px corners, dark top/left shadow and
        // light bottom/right edge.
        g.fill(x + 3, y, x + w - 3, y + 1, 0xFF080A0C);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF101318);
        g.fill(x, y + 3, x + 1, y + h - 3, 0xFF080A0C);
        g.fill(x + 1, y + 2, x + 2, y + h - 2, 0xFF101318);

        g.fill(x + 3, y + h - 1, x + w - 3, y + h, 0xFF5A6470);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF38434C);
        g.fill(x + w - 1, y + 3, x + w, y + h - 3, 0xFF5A6470);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, 0xFF38434C);

        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, DARK_PANEL_OUTER);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, DARK_PANEL_INNER);
        g.fill(x + 5, y + 5, x + w - 5, y + 6, 0x80000000);
    }

    private void drawMetricLane(GuiGraphics g, Metric metric, int x, int y, int w, int h) {
        // No light lane background; keep only a faint separator on the dark inset panel.
        g.fill(x + 4, y + h - 1, x + w - 4, y + h, 0x332F3942);
    }

    private void drawFormedStatusBar(GuiGraphics g, boolean formed, int x, int y, int w, int h) {
        NENativeAe2StyleRenderer.drawAeInsetRect(g, x, y, w, h, 0xFF1B2026);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, DARK_PANEL_INNER);
        g.fill(x + 3, y + 3, x + w - 3, y + 4, 0x30FFFFFF);
        g.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, 0x80000000);

        Component label = Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component value = boolText(formed);
        int textX = x + 8;
        int textY = y + 4;

        g.drawString(font, label, textX, textY, DARK_TEXT_PRIMARY, false);
        g.drawString(font, value, textX + font.width(label), textY,
                formed ? DARK_TEXT_SUCCESS : DARK_TEXT_ERROR, false);
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
        String[] needles = new String[] { "chemical", "chem", "gas", "infuse", "infusion", "pigment", "slurry",
                "mekanism" };
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
        if (metric.max() <= 0) {
            return DARK_TEXT_MUTED;
        }
        return lerpColor(darken(metric.accentColor(), 0.72D), metric.accentColor(), Mth.clamp(pct + 0.2D, 0.0D, 1.0D));
    }

    private static int darken(int color, double factor) {
        int a = (color >>> 24) & 0xFF;
        int r = (int) (((color >>> 16) & 0xFF) * factor);
        int g = (int) (((color >>> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
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
