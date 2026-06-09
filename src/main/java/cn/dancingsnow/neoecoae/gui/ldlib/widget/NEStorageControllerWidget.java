package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.client.gui.Icon;
import appeng.core.localization.GuiText;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.PriorityMenu;
import appeng.menu.locator.MenuLocators;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

public class NEStorageControllerWidget extends NELDLibSyncedStateWidget<NEStorageUiState> {
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
    private static final int PRIORITY_BUTTON_X = 336;
    private static final int PRIORITY_BUTTON_Y = 0;
    private static final int PRIORITY_BUTTON_W = 22;
    private static final int PRIORITY_BUTTON_H = 22;
    private static final double ANIMATION_SPEED = 0.16D;

    private static final String TOOLTIP_ITEMS_USED = "gui.neoecoae.storage.tooltip.items_used";
    private static final String TOOLTIP_FLUIDS_USED = "gui.neoecoae.storage.tooltip.fluids_used";
    private static final String TOOLTIP_CHEMICALS_USED = "gui.neoecoae.storage.tooltip.chemicals_used";
    private static final String TOOLTIP_USED_TOTAL = "gui.neoecoae.storage.tooltip.used_total";

    private final ECOStorageSystemBlockEntity storage;
    private final Player player;
    private final boolean chemicalMode = hasChemicalStorageIntegration();

    private double animatedEnergyPct;
    private double animatedItemPct;
    private double animatedFluidPct;
    private double animatedChemicalPct;

    public NEStorageControllerWidget(ECOStorageSystemBlockEntity storage, Player player) {
        super(
                storage.getBlockState().getBlock().getName(),
                358,
                220,
                NEStorageUiState.empty(storage.getBlockPos()),
                storage::createStorageUiState,
                NELDLibStateCodecs::writeStorage,
                NELDLibStateCodecs::readStorage,
                20);
        this.storage = storage;
        this.player = player;
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
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        StorageMetrics metrics = buildStorageMetrics(currentState());
        animatedEnergyPct = animateTo(animatedEnergyPct, metrics.energy().percent());
        animatedItemPct = animateTo(animatedItemPct, metrics.items().percent());
        animatedFluidPct = animateTo(animatedFluidPct, metrics.fluids().percent());
        animatedChemicalPct = animateTo(animatedChemicalPct, metrics.chemicals().percent());

        int ox = getPositionX();
        int oy = getPositionY();
        NELDLibStyle.drawDarkInsetRect(graphics, ox + LEFT_PANEL_X, oy + LEFT_PANEL_Y, LEFT_PANEL_W, LEFT_PANEL_H);
        NELDLibStyle.drawDarkInsetRect(graphics, ox + RIGHT_PANEL_X, oy + RIGHT_PANEL_Y, RIGHT_PANEL_W, RIGHT_PANEL_H);

        Metric[] columns = chemicalMode
                ? new Metric[] {metrics.items(), metrics.fluids(), metrics.chemicals()}
                : new Metric[] {metrics.items(), metrics.fluids()};
        double[] values = chemicalMode
                ? new double[] {animatedItemPct, animatedFluidPct, animatedChemicalPct}
                : new double[] {animatedItemPct, animatedFluidPct};
        drawBoundMetricColumns(graphics, columns, values);

        NELDLibStyle.drawDarkInsetRect(
                graphics,
                ox + LEFT_PANEL_X,
                oy + FORMED_BAR_Y,
                RIGHT_PANEL_X + RIGHT_PANEL_W - LEFT_PANEL_X,
                FORMED_BAR_H);
    }

    @Override
    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        StorageMetrics metrics = buildStorageMetrics(currentState());
        drawLocalString(graphics, title, NELDLibUiTitleX(), NELDLibUiTitleY(), TEXT_PRIMARY);
        drawStorageTextLines(graphics, metrics);
        drawFormedStatusBar(graphics, currentState().formed());
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isMouseIn(PRIORITY_BUTTON_X, PRIORITY_BUTTON_Y, PRIORITY_BUTTON_W, PRIORITY_BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font(), List.of(GuiText.Priority.text()), mouseX, mouseY);
            return;
        }
        renderMetricColumnTooltip(graphics, mouseX, mouseY);
    }

    private void drawStorageTextLines(GuiGraphics g, StorageMetrics metrics) {
        int x = absX(TEXT_START_X);
        int y = absY(TEXT_START_Y);

        drawPlainLine(g, Component.translatable("gui.neoecoae.storage.energy"), x, y, NELDLibStyle.DARK_TEXT_PRIMARY);
        y += TEXT_LINE_STEP;
        drawPrefixedUsedTotalLine(
                g,
                Component.translatable("gui.neoecoae.storage.energy_storage").getString() + ": ",
                metrics.energy().used(),
                metrics.energy().max(),
                "AE",
                x,
                y);
        y += TEXT_LINE_STEP;

        y = drawStorageTypeBlock(g, metrics.items(), x, y);
        y = drawStorageTypeBlock(g, metrics.fluids(), x, y);
        if (chemicalMode) {
            drawStorageTypeBlock(g, metrics.chemicals(), x, y);
        }
    }

    private int drawStorageTypeBlock(GuiGraphics g, Metric metric, int x, int y) {
        drawPlainLine(g, metric.label(), x, y, metric.accentColor());
        y += TEXT_LINE_STEP;
        drawUsedTotalLine(
                g,
                NELDLibText.number(metric.usedTypes()),
                NELDLibText.number(metric.totalTypes()),
                metric.usedTypes(),
                metric.totalTypes(),
                Component.translatable("gui.neoecoae.common.types").getString(),
                x,
                y);
        y += TEXT_LINE_STEP;
        drawByteUsedTotalLine(g, metric.used(), metric.max(), x, y);
        return y + TEXT_LINE_STEP;
    }

    private void drawPrefixedUsedTotalLine(
            GuiGraphics g, String prefix, long used, long max, String suffix, int x, int y) {
        int cursor = NELDLibStyle.drawSegment(g, font(), prefix, x, y, NELDLibStyle.DARK_TEXT_MUTED);
        cursor += NELDLibStyle.drawSegment(
                g,
                font(),
                NELDLibText.number(Math.max(0L, used)),
                x + cursor,
                y,
                NELDLibStyle.usedValueColor(used, max));
        cursor += NELDLibStyle.drawSegment(g, font(), " / ", x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
        cursor += NELDLibStyle.drawSegment(
                g, font(), NELDLibText.number(Math.max(0L, max)), x + cursor, y, NELDLibStyle.DARK_TEXT_VALUE);
        if (!suffix.isEmpty()) {
            NELDLibStyle.drawSegment(g, font(), " " + suffix, x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
        }
    }

    private void drawByteUsedTotalLine(GuiGraphics g, long used, long max, int x, int y) {
        String usedText = NELDLibText.storageBytes(used);
        String maxText = NELDLibText.storageBytes(max);
        String suffix =
                Component.translatable("gui.neoecoae.storage.bytes_used").getString();
        if (font().width(usedText + " / " + maxText + " " + suffix) > TEXT_MAX_W) {
            suffix = Component.translatable("gui.neoecoae.storage.used_short").getString();
        }
        drawUsedTotalLine(g, usedText, maxText, used, max, suffix, x, y);
    }

    private void drawUsedTotalLine(
            GuiGraphics g, String usedText, String maxText, long used, long max, String suffix, int x, int y) {
        int cursor = NELDLibStyle.drawSegment(g, font(), usedText, x, y, NELDLibStyle.usedValueColor(used, max));
        cursor += NELDLibStyle.drawSegment(g, font(), " / ", x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
        cursor += NELDLibStyle.drawSegment(g, font(), maxText, x + cursor, y, NELDLibStyle.DARK_TEXT_VALUE);
        NELDLibStyle.drawSegment(g, font(), " " + suffix, x + cursor, y, NELDLibStyle.DARK_TEXT_MUTED);
    }

    private void drawBoundMetricColumns(GuiGraphics g, Metric[] metrics, double[] animatedValues) {
        int count = metrics.length;
        int columnW = count == 3 ? 30 : 38;
        int gap = count == 3 ? 10 : 20;
        int totalW = columnW * count + gap * (count - 1);
        int startX = RIGHT_PANEL_X + (RIGHT_PANEL_W - totalW) / 2;

        for (int i = 0; i < count; i++) {
            int x = startX + i * (columnW + gap);
            drawBoundMetricColumn(g, metrics[i], absX(x), absY(COLUMN_Y), columnW, COLUMN_H, animatedValues[i]);
        }
    }

    private void drawBoundMetricColumn(GuiGraphics g, Metric metric, int x, int y, int w, int h, double pct) {
        NELDLibStyle.drawCenteredFitted(
                g, font(), metric.label(), x - 9, y - 14, w + 18, NELDLibStyle.DARK_TEXT_PRIMARY);
        NELDLibStyle.drawTinyInsetRect(g, x, y, w, h, 0xFF201E27);

        int ix = x + 5;
        int iy = y + 6;
        int iw = w - 10;
        int ih = h - 12;
        int fillH = Mth.clamp((int) Math.round(ih * pct), 0, ih);
        int fillY = iy + ih - fillH;

        g.fill(ix, iy, ix + iw, iy + ih, 0xAA17141E);
        g.fill(ix + 1, iy + 3, ix + 3, iy + ih - 3, 0x45C9C3D6);
        g.fill(ix + iw - 3, iy + 3, ix + iw - 1, iy + ih - 3, 0x40202020);

        if (fillH > 0) {
            int color = NELDLibStyle.metricColor(metric.accentColor(), metric.max(), pct);
            g.fill(ix, fillY, ix + iw, iy + ih, color);
            g.fill(ix, fillY, ix + iw, Math.min(fillY + 2, iy + ih), 0x70FFFFFF);
            g.fill(ix, iy + ih - 2, ix + iw, iy + ih, 0x70000000);
        }

        for (int i = 1; i < 6; i++) {
            int tickY = iy + ih - Math.round(ih * i / 6.0F);
            g.fill(ix - 2, tickY, ix + 3, tickY + 1, 0xCCC9C3D6);
            g.fill(ix + iw - 3, tickY, ix + iw + 2, tickY + 1, 0xCCC9C3D6);
        }

        g.fill(x + 2, y + 2, x + w - 2, y + 5, 0xCC17141E);
        g.fill(x + 2, y + h - 5, x + w - 2, y + h - 2, 0xCC17141E);
        g.fill(x + 3, y + 3, x + 8, y + 10, 0xAA100E16);
        g.fill(x + w - 8, y + 3, x + w - 3, y + 10, 0xAA100E16);
        g.fill(x + 3, y + h - 10, x + 8, y + h - 3, 0xAA100E16);
        g.fill(x + w - 8, y + h - 10, x + w - 3, y + h - 3, 0xAA100E16);

        int percentY = y + h + COLUMN_PERCENT_GAP;
        int percentColor = metric.max() <= 0
                ? NELDLibStyle.DARK_TEXT_MUTED
                : NELDLibStyle.metricColor(metric.accentColor(), metric.max(), pct);
        String percentText = NELDLibText.percentOrNA(metric.used(), metric.max());
        NELDLibStyle.drawTinyInsetRect(g, x - 2, percentY, w + 4, COLUMN_PERCENT_H, 0xFF201E27);
        NELDLibStyle.drawCenteredScaledString(
                g, font(), percentText, x - 2, percentY, w + 4, COLUMN_PERCENT_H, percentColor, 0.9F);
    }

    private void drawFormedStatusBar(GuiGraphics g, boolean formed) {
        Component label = Component.translatable("gui.neoecoae.machine.formed").append(": ");
        Component value = boolText(formed);
        int w = RIGHT_PANEL_X + RIGHT_PANEL_W - LEFT_PANEL_X;
        int textW = font().width(label) + font().width(value);
        int textX = absX(LEFT_PANEL_X) + (w - textW) / 2;
        int textY = absY(FORMED_BAR_Y) + (FORMED_BAR_H - font().lineHeight) / 2;
        g.drawString(font(), label, textX, textY, NELDLibStyle.DARK_TEXT_PRIMARY, false);
        g.drawString(
                font(),
                value,
                textX + font().width(label),
                textY,
                formed ? NELDLibStyle.DARK_TEXT_SUCCESS : NELDLibStyle.DARK_TEXT_ERROR,
                false);
    }

    private void renderMetricColumnTooltip(GuiGraphics g, int mouseX, int mouseY) {
        StorageMetrics metrics = buildStorageMetrics(currentState());
        Metric[] columns = chemicalMode
                ? new Metric[] {metrics.items(), metrics.fluids(), metrics.chemicals()}
                : new Metric[] {metrics.items(), metrics.fluids()};
        String[] tooltipKeys = chemicalMode
                ? new String[] {TOOLTIP_ITEMS_USED, TOOLTIP_FLUIDS_USED, TOOLTIP_CHEMICALS_USED}
                : new String[] {TOOLTIP_ITEMS_USED, TOOLTIP_FLUIDS_USED};

        int count = columns.length;
        int columnW = count == 3 ? 30 : 38;
        int gap = count == 3 ? 10 : 20;
        int totalW = columnW * count + gap * (count - 1);
        int startX = RIGHT_PANEL_X + (RIGHT_PANEL_W - totalW) / 2;

        for (int i = 0; i < count; i++) {
            int x = startX + i * (columnW + gap);
            if (!isMouseIn(x, COLUMN_Y, columnW, COLUMN_H, mouseX, mouseY)) {
                continue;
            }
            Metric metric = columns[i];
            g.renderTooltip(
                    font(),
                    List.of(
                            Component.translatable(
                                    tooltipKeys[i], NELDLibText.percentOrNA(metric.used(), metric.max())),
                            Component.translatable(
                                    TOOLTIP_USED_TOTAL,
                                    NELDLibText.number(metric.used()),
                                    NELDLibText.number(metric.max())),
                            Component.translatable(
                                    "gui.neoecoae.machine.types_value",
                                    NELDLibText.number(metric.usedTypes()),
                                    NELDLibText.number(metric.totalTypes()))),
                    Optional.empty(),
                    mouseX,
                    mouseY);
            return;
        }
    }

    private StorageMetrics buildStorageMetrics(NEStorageUiState state) {
        List<NEStorageUiTypeState> types = state.typeStates();
        NEStorageUiTypeState itemState = findTypeState(types, "item");
        NEStorageUiTypeState fluidState = findTypeState(types, "fluid");
        NEStorageUiTypeState chemicalState = findChemicalTypeState(types);
        Metric energy = new Metric(
                Component.translatable("gui.neoecoae.common.energy"),
                state.storedEnergy(),
                state.maxEnergy(),
                0,
                0,
                NELDLibStyle.DARK_TEXT_VALUE);
        Metric items = createTypeMetric(itemState, Component.translatable("gui.neoecoae.storage.items"), 0xFF43B678);
        Metric fluids = createTypeMetric(fluidState, Component.translatable("gui.neoecoae.storage.fluids"), 0xFF3A8FD6);
        Metric chemicals =
                createTypeMetric(chemicalState, Component.translatable("gui.neoecoae.storage.chemicals"), 0xFF9A6AE8);
        return new StorageMetrics(energy, items, fluids, chemicals);
    }

    private static Metric createTypeMetric(NEStorageUiTypeState state, Component fallbackLabel, int accentColor) {
        if (state == null) {
            return new Metric(fallbackLabel, 0, 0, 0, 0, accentColor);
        }
        return new Metric(
                fallbackLabel,
                state.usedBytes(),
                state.totalBytes(),
                state.usedTypes(),
                state.totalTypes(),
                accentColor);
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
        for (NEStorageUiTypeState state : types) {
            String path = state.typeId().getPath().toLowerCase(Locale.ROOT);
            if (path.equals(lowerNeedle) || path.equals(pluralNeedle)) {
                return state;
            }
        }
        for (NEStorageUiTypeState state : types) {
            String path = state.typeId().getPath().toLowerCase(Locale.ROOT);
            String name = state.displayName().toLowerCase(Locale.ROOT);
            if (path.contains(lowerNeedle) || name.contains(lowerNeedle)) {
                return state;
            }
        }
        return null;
    }

    private static NEStorageUiTypeState findChemicalTypeState(List<NEStorageUiTypeState> types) {
        String[] needles =
                new String[] {"chemical", "chem", "gas", "infuse", "infusion", "pigment", "slurry", "mekanism"};
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

    private record StorageMetrics(Metric energy, Metric items, Metric fluids, Metric chemicals) {}

    private record Metric(Component label, long used, long max, long usedTypes, long totalTypes, int accentColor) {
        private double percent() {
            return NEStorageControllerWidget.percent(used, max);
        }
    }
}
