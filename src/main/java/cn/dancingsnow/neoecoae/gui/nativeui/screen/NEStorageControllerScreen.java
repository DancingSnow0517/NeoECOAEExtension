package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStorageControllerMenu;
import cn.dancingsnow.neoecoae.network.NEStorageUiState;
import cn.dancingsnow.neoecoae.network.NEStorageUiTypeState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
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
import java.util.Optional;

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

    private static final int DARK_PANEL_OUTER = 0xFF17141E;
    private static final int DARK_PANEL_MIDDLE = 0xFF2B2834;
    private static final int DARK_PANEL_INNER = 0xFF665F6D;
    private static final int DARK_PANEL_LIGHT_EDGE = 0xFFC9C3D6;

    private static final int DARK_TEXT_PRIMARY = 0xFFD6D0E0;
    private static final int DARK_TEXT_VALUE = 0xFF8377FF;
    private static final int DARK_TEXT_USED = 0xFF00FC00;
    private static final int DARK_TEXT_MUTED = 0xFFAAA4B2;
    private static final int DARK_TEXT_SUCCESS = 0xFF6CFFA0;
    private static final int DARK_TEXT_WARNING = 0xFFFFD65A;
    private static final int DARK_TEXT_ORANGE = 0xFFFF9A3D;
    private static final int DARK_TEXT_ERROR = 0xFFFF6A75;

    private static final int LEFT_PANEL_X = 9;
    private static final int LEFT_PANEL_Y = 24;
    private static final int LEFT_PANEL_W = 198;
    private static final int LEFT_PANEL_H = 158;

    private static final int RIGHT_PANEL_X = 218;
    private static final int RIGHT_PANEL_Y = 24;
    private static final int RIGHT_PANEL_W = 130;
    private static final int RIGHT_PANEL_H = 158;

    private static final int TEXT_START_X = LEFT_PANEL_X + 8;
    private static final int TEXT_START_Y = LEFT_PANEL_Y + 8;
    private static final int TEXT_LINE_STEP = 13;
    private static final int TEXT_MAX_W = LEFT_PANEL_W - 16;

    private static final int COLUMN_Y = RIGHT_PANEL_Y + 34;
    private static final int COLUMN_H = 88;
    private static final int COLUMN_PERCENT_GAP = 7;
    private static final int COLUMN_PERCENT_H = 17;

    private static final int FORMED_BAR_Y = 187;
    private static final int FORMED_BAR_H = 25;
    private static final double ANIMATION_SPEED = 0.16D;
    private static final long BYTES_IN_G = 1024L * 1024L * 1024L;
    private static final long BYTES_IN_T = BYTES_IN_G * 1024L;
    private static final long BYTES_IN_P = BYTES_IN_T * 1024L;

    private static final String TOOLTIP_ITEMS_USED = "gui.neoecoae.storage.tooltip.items_used";
    private static final String TOOLTIP_FLUIDS_USED = "gui.neoecoae.storage.tooltip.fluids_used";
    private static final String TOOLTIP_CHEMICALS_USED = "gui.neoecoae.storage.tooltip.chemicals_used";
    private static final String TOOLTIP_USED_TOTAL = "gui.neoecoae.storage.tooltip.used_total";

    private boolean hasStorageState;
    private NEStorageUiState storageState;

    private double animatedEnergyPct = 0.0D;
    private double animatedItemPct = 0.0D;
    private double animatedFluidPct = 0.0D;
    private double animatedChemicalPct = 0.0D;

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
        animatedEnergyPct = 0.0D;
        animatedItemPct = 0.0D;
        animatedFluidPct = 0.0D;
        animatedChemicalPct = 0.0D;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderMetricColumnTooltip(guiGraphics, mouseX, mouseY);
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
        drawStorageTextLines(guiGraphics, metrics, chemicalMode);

        if (chemicalMode) {
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

        Metric energy = new Metric(
                Component.literal("能量"),
                s.storedEnergy(), s.maxEnergy(),
                0, 0,
                0xFF8377FF,
                false);
        Metric items = createTypeMetric(itemState, Component.literal("物品"), 0xFF43B678);
        Metric fluids = createTypeMetric(fluidState, Component.literal("流体"), 0xFF3A8FD6);
        Metric chemicals = createTypeMetric(chemicalState, Component.literal("化学品"), 0xFF9A6AE8);
        return new StorageMetrics(energy, items, fluids, chemicals);
    }

    private Metric createTypeMetric(NEStorageUiTypeState state, Component fallbackLabel, int accentColor) {
        if (state == null) {
            return new Metric(fallbackLabel, 0, 0, 0, 0, accentColor, true);
        }
        return new Metric(
                fallbackLabel,
                state.usedBytes(), state.totalBytes(),
                state.usedTypes(), state.totalTypes(),
                accentColor,
                true);
    }

    private void drawStorageTextLines(GuiGraphics g, StorageMetrics metrics, boolean chemicalMode) {
        int x = TEXT_START_X;
        int y = TEXT_START_Y;

        drawPlainLine(g, metrics.energy().label(), x, y, DARK_TEXT_PRIMARY);
        y += TEXT_LINE_STEP;
        drawPrefixedUsedTotalLine(g, "能量存储: ", metrics.energy().used(), metrics.energy().max(), "", x, y);
        y += TEXT_LINE_STEP;

        y = drawStorageTypeBlock(g, metrics.items(), x, y);
        y = drawStorageTypeBlock(g, metrics.fluids(), x, y);
        if (chemicalMode) {
            drawStorageTypeBlock(g, metrics.chemicals(), x, y);
        }
    }

    private int drawStorageTypeBlock(GuiGraphics g, Metric metric, int x, int y) {
        drawBoldLine(g, metric.label(), x, y, metric.accentColor());
        y += TEXT_LINE_STEP;
        drawUsedTotalLine(g, metric.usedTypes(), metric.totalTypes(), "类型", x, y);
        y += TEXT_LINE_STEP;
        drawByteUsedTotalLine(g, metric.used(), metric.max(), x, y);
        return y + TEXT_LINE_STEP;
    }

    private void drawPrefixedUsedTotalLine(GuiGraphics g, String prefix, long used, long max, String suffix, int x,
            int y) {
        int cursor = drawSegment(g, prefix, x, y, DARK_TEXT_MUTED);
        cursor += drawSegment(g, formatMetricNumber(used), x + cursor, y, usedValueColor(used, max));
        cursor += drawSegment(g, " / ", x + cursor, y, DARK_TEXT_MUTED);
        cursor += drawSegment(g, formatMetricNumber(max), x + cursor, y, DARK_TEXT_VALUE);
        if (!suffix.isEmpty()) {
            drawSegment(g, " " + suffix, x + cursor, y, DARK_TEXT_MUTED);
        }
    }

    private void drawUsedTotalLine(GuiGraphics g, long used, long max, String suffix, int x, int y) {
        drawUsedTotalLine(g, formatMetricNumber(used), formatMetricNumber(max), used, max, suffix, x, y);
    }

    private void drawByteUsedTotalLine(GuiGraphics g, long used, long max, int x, int y) {
        String usedText = formatStorageBytes(used);
        String maxText = formatStorageBytes(max);
        String suffix = "字节已使用";
        if (font.width(usedText + " / " + maxText + " " + suffix) > TEXT_MAX_W) {
            suffix = "已用";
        }
        drawUsedTotalLine(g, usedText, maxText, used, max, suffix, x, y);
    }

    private void drawUsedTotalLine(GuiGraphics g, String usedText, String maxText, long used, long max, String suffix,
            int x, int y) {
        int cursor = drawSegment(g, usedText, x, y, usedValueColor(used, max));
        cursor += drawSegment(g, " / ", x + cursor, y, DARK_TEXT_MUTED);
        cursor += drawSegment(g, maxText, x + cursor, y, DARK_TEXT_VALUE);
        drawSegment(g, " " + suffix, x + cursor, y, DARK_TEXT_MUTED);
    }

    private int drawSegment(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, Component.literal(text), x, y, color, false);
        return font.width(text);
    }

    private void drawPlainLine(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    private void drawBoldLine(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, text.copy().withStyle(net.minecraft.ChatFormatting.BOLD), x, y, color, false);
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
        drawTinyInsetRect(g, x, y, w, h, 0xFF201E27);

        int ix = x + 5;
        int iy = y + 6;
        int iw = w - 10;
        int ih = h - 12;
        int fillH = Mth.clamp((int) Math.round(ih * pct), 0, ih);
        int fillY = iy + ih - fillH;

        // Inner dark glass body.
        g.fill(ix, iy, ix + iw, iy + ih, 0xAA17141E);
        g.fill(ix + 1, iy + 3, ix + 3, iy + ih - 3, 0x45C9C3D6);
        g.fill(ix + iw - 3, iy + 3, ix + iw - 1, iy + ih - 3, 0x40202020);

        if (fillH > 0) {
            int color = metricColor(metric, pct);
            g.fill(ix, fillY, ix + iw, iy + ih, color);
            g.fill(ix, fillY, ix + iw, Math.min(fillY + 2, iy + ih), 0x70FFFFFF);
            g.fill(ix, iy + ih - 2, ix + iw, iy + ih, 0x70000000);
        }

        // White side ticks, matching the 1.12.2 vertical gauge feel.
        for (int i = 1; i < 6; i++) {
            int tickY = iy + ih - Math.round(ih * i / 6.0F);
            g.fill(ix - 2, tickY, ix + 3, tickY + 1, 0xCCC9C3D6);
            g.fill(ix + iw - 3, tickY, ix + iw + 2, tickY + 1, 0xCCC9C3D6);
        }

        // Outer dark braces.
        g.fill(x + 2, y + 2, x + w - 2, y + 5, 0xCC17141E);
        g.fill(x + 2, y + h - 5, x + w - 2, y + h - 2, 0xCC17141E);
        g.fill(x + 3, y + 3, x + 8, y + 10, 0xAA100E16);
        g.fill(x + w - 8, y + 3, x + w - 3, y + 10, 0xAA100E16);
        g.fill(x + 3, y + h - 10, x + 8, y + h - 3, 0xAA100E16);
        g.fill(x + w - 8, y + h - 10, x + w - 3, y + h - 3, 0xAA100E16);

        int percentY = y + h + COLUMN_PERCENT_GAP;
        int percentColor = metric.max() <= 0 ? DARK_TEXT_MUTED : metricColor(metric, pct);
        String percentText = metric.max() <= 0 ? "N/A" : formatPercent(metric.percent());
        drawTinyInsetRect(g, x - 2, percentY, w + 4, COLUMN_PERCENT_H, 0xFF201E27);
        drawCenteredScaledString(g, percentText, x - 2, percentY, w + 4, COLUMN_PERCENT_H, percentColor, 0.9F);
    }

    private void renderMetricColumnTooltip(GuiGraphics g, int mouseX, int mouseY) {
        boolean chemicalMode = hasChemicalStorageIntegration();
        StorageMetrics metrics = buildStorageMetrics(resolveStorageState());
        Metric[] columns = chemicalMode
                ? new Metric[] { metrics.items(), metrics.fluids(), metrics.chemicals() }
                : new Metric[] { metrics.items(), metrics.fluids() };
        String[] tooltipKeys = chemicalMode
                ? new String[] { TOOLTIP_ITEMS_USED, TOOLTIP_FLUIDS_USED, TOOLTIP_CHEMICALS_USED }
                : new String[] { TOOLTIP_ITEMS_USED, TOOLTIP_FLUIDS_USED };

        int count = columns.length;
        int columnW = count == 3 ? 30 : 38;
        int gap = count == 3 ? 10 : 20;
        int totalW = columnW * count + gap * (count - 1);
        int startX = leftPos + RIGHT_PANEL_X + (RIGHT_PANEL_W - totalW) / 2;
        int columnY = topPos + COLUMN_Y;

        for (int i = 0; i < count; i++) {
            int x = startX + i * (columnW + gap);
            Rect2i rect = new Rect2i(x, columnY, columnW, COLUMN_H);
            if (rect.contains(mouseX, mouseY)) {
                Metric metric = columns[i];
                g.renderTooltip(font, List.of(
                        Component.translatable(tooltipKeys[i], formatPercent(metric.percent())),
                        Component.translatable(TOOLTIP_USED_TOTAL,
                                formatStorageBytes(metric.used()),
                                formatStorageBytes(metric.max()))),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
        }
    }

    private void drawInsetGroupPanel(GuiGraphics g, int x, int y, int w, int h) {
        drawDarkInsetRect(g, x, y, w, h);
    }

    private void drawFormedStatusBar(GuiGraphics g, boolean formed, int x, int y, int w, int h) {
        drawDarkInsetRect(g, x, y, w, h);

        Component label = Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component value = boolText(formed);
        int textW = font.width(label) + font.width(value);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - font.lineHeight) / 2;

        g.drawString(font, label, textX, textY, DARK_TEXT_PRIMARY, false);
        g.drawString(font, value, textX + font.width(label), textY,
                formed ? DARK_TEXT_SUCCESS : DARK_TEXT_ERROR, false);
    }

    private void drawDarkInsetRect(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFCBCCD4);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0D0D11);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF85818D);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF0D0D11);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFF47434F);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xFF605A66);
    }

    private void drawTinyInsetRect(GuiGraphics g, int x, int y, int w, int h, int innerColor) {
        g.fill(x, y, x + w, y + h, DARK_PANEL_LIGHT_EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, DARK_PANEL_OUTER);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, innerColor);
    }

    private void drawCenteredString(GuiGraphics g, String text, int x, int y, int w, int color) {
        g.drawString(font, Component.literal(text), x + (w - font.width(text)) / 2, y, color, false);
    }

    private void drawCenteredScaledString(GuiGraphics g, String text, int boxX, int boxY, int boxW, int boxH,
            int color, float scale) {
        var pose = g.pose();
        float scaledTextW = font.width(text) * scale;
        float scaledTextH = font.lineHeight * scale;
        float drawX = boxX + (boxW - scaledTextW) / 2.0F;
        float drawY = boxY + (boxH - scaledTextH) / 2.0F;

        pose.pushPose();
        pose.translate(drawX, drawY, 0.0F);
        pose.scale(scale, scale, 1.0F);
        g.drawString(font, Component.literal(text), 0, 0, color, false);
        pose.popPose();
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
        String pluralNeedle = lowerNeedle + "s";
        for (NEStorageUiTypeState ts : types) {
            String path = ts.typeId().getPath().toLowerCase(Locale.ROOT);
            if (path.equals(lowerNeedle) || path.equals(pluralNeedle)) {
                return ts;
            }
        }
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
        double start = current < 0.0D ? 0.0D : current;
        return Mth.lerp(ANIMATION_SPEED, start, Mth.clamp(target, 0.0D, 1.0D));
    }

    private static double percent(long used, long max) {
        if (max <= 0) {
            return 0.0D;
        }
        return Mth.clamp((double) used / (double) max, 0.0D, 1.0D);
    }

    private static int usedValueColor(long used, long max) {
        if (used <= 0 || max <= 0) {
            return DARK_TEXT_USED;
        }
        double pct = (double) used / (double) max;
        if (pct >= 1.0D) {
            return DARK_TEXT_ERROR;
        }
        if (pct >= 0.9D) {
            return DARK_TEXT_ORANGE;
        }
        if (pct >= 0.75D) {
            return DARK_TEXT_WARNING;
        }
        return DARK_TEXT_USED;
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

    private static String formatStorageBytes(long value) {
        long abs = value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
        if (abs < BYTES_IN_G) {
            return formatMetricNumber(value);
        }

        long unit = BYTES_IN_G;
        String suffix = "G";
        if (abs >= BYTES_IN_P) {
            unit = BYTES_IN_P;
            suffix = "P";
        } else if (abs >= BYTES_IN_T) {
            unit = BYTES_IN_T;
            suffix = "T";
        }

        return trimDecimal((double) value / (double) unit) + suffix;
    }

    private static String trimDecimal(double value) {
        String text = String.format(Locale.US, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String formatPercent(double pct) {
        return String.format(Locale.US, "%.1f%%", pct * 100.0D);
    }

    public NEStorageControllerMenu getMenu() {
        return menu;
    }

    private record StorageMetrics(Metric energy, Metric items, Metric fluids, Metric chemicals) {
    }

    private record Metric(Component label, long used, long max, long usedTypes, long totalTypes,
            int accentColor, boolean byteBased) {
        private double percent() {
            return NEStorageControllerScreen.percent(used, max);
        }
    }
}
