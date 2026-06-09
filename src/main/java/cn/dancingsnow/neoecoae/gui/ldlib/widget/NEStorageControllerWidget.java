package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.core.localization.GuiText;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.PriorityMenu;
import appeng.menu.locator.MenuLocators;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStateCodecs;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectAndBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
    private static final int FORMED_BAR_Y = 187;
    private static final int FORMED_BAR_H = 25;
    private static final int PRIORITY_BUTTON_X = 288;
    private static final int PRIORITY_BUTTON_Y = 7;
    private static final int PRIORITY_BUTTON_W = 60;
    private static final int PRIORITY_BUTTON_H = 18;
    private static final int GAUGE_Y = 62;
    private static final int GAUGE_H = 82;
    private static final long BYTES_IN_G = 1024L * 1024L * 1024L;
    private static final long BYTES_IN_T = BYTES_IN_G * 1024L;
    private static final long BYTES_IN_P = BYTES_IN_T * 1024L;

    private final ECOStorageSystemBlockEntity storage;
    private final Player player;
    private final boolean chemicalMode = hasChemicalStorageIntegration();

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
    protected void initLdWidgets() {
        addWidget(new ButtonWidget(
                        PRIORITY_BUTTON_X,
                        PRIORITY_BUTTON_Y,
                        PRIORITY_BUTTON_W,
                        PRIORITY_BUTTON_H,
                        buttonTexture(),
                        click -> {
                            if (!click.isRemote && player instanceof ServerPlayer serverPlayer && storage.isFormed()) {
                                MenuOpener.open(PriorityMenu.TYPE, serverPlayer, MenuLocators.forBlockEntity(storage));
                            }
                        })
                .setHoverTexture(new ColorRectAndBorderTexture(0x00000000, 0xFF4F7FB6, 1.0F)));
        addText(
                PRIORITY_BUTTON_X,
                PRIORITY_BUTTON_Y + 5,
                PRIORITY_BUTTON_W,
                8,
                () -> GuiText.Priority.text(),
                TEXT_VALUE,
                TextTexture.TextType.NORMAL);

        addStorageLines();
        addGaugeWidgets();
    }

    private void addStorageLines() {
        int x = LEFT_PANEL_X + 8;
        int y = LEFT_PANEL_Y + 8;
        int line = 11;

        addText(
                x,
                y,
                LEFT_PANEL_W - 16,
                9,
                () -> Component.literal("Energy: " + fmt(currentState().storedEnergy()) + " / "
                        + fmt(currentState().maxEnergy()) + " AE"),
                TEXT_VALUE,
                TextTexture.TextType.LEFT_HIDE);
        y += line;
        addText(
                x,
                y,
                LEFT_PANEL_W - 16,
                9,
                () -> Component.literal("Types: " + fmt(currentState().totalUsedTypes()) + " / "
                        + fmt(currentState().totalTypes())),
                TEXT_PRIMARY,
                TextTexture.TextType.LEFT_HIDE);
        y += line;
        addText(
                x,
                y,
                LEFT_PANEL_W - 16,
                9,
                () -> Component.literal(
                        "Bytes: " + formatStorageBytes(currentState().totalUsedBytes()) + " / "
                                + formatStorageBytes(currentState().totalBytes())),
                TEXT_PRIMARY,
                TextTexture.TextType.LEFT_HIDE);
        y += line + 3;

        y = addTypeBlock(x, y, "Items", this::itemMetric);
        y = addTypeBlock(x, y, "Fluids", this::fluidMetric);
        if (chemicalMode) {
            addTypeBlock(x, y, "Chemicals", this::chemicalMetric);
        }
    }

    private int addTypeBlock(int x, int y, String label, java.util.function.Supplier<Metric> metricSupplier) {
        int line = 11;
        addText(x, y, LEFT_PANEL_W - 16, 9, () -> Component.literal(label), TEXT_VALUE, TextTexture.TextType.LEFT_HIDE);
        y += line;
        addText(
                x,
                y,
                LEFT_PANEL_W - 16,
                9,
                () -> {
                    Metric metric = metricSupplier.get();
                    return Component.literal("Types: " + fmt(metric.usedTypes()) + " / " + fmt(metric.totalTypes()));
                },
                TEXT_PRIMARY,
                TextTexture.TextType.LEFT_HIDE);
        y += line;
        addText(
                x,
                y,
                LEFT_PANEL_W - 16,
                9,
                () -> {
                    Metric metric = metricSupplier.get();
                    return Component.literal(
                            "Bytes: " + formatStorageBytes(metric.used()) + " / " + formatStorageBytes(metric.max()));
                },
                TEXT_PRIMARY,
                TextTexture.TextType.LEFT_HIDE);
        return y + line + 2;
    }

    private void addGaugeWidgets() {
        MetricSpec[] specs = metricSpecs();
        int count = specs.length;
        int columnW = count == 3 ? 30 : 38;
        int gap = count == 3 ? 10 : 20;
        int totalW = columnW * count + gap * (count - 1);
        int startX = RIGHT_PANEL_X + (RIGHT_PANEL_W - totalW) / 2;

        for (int i = 0; i < count; i++) {
            MetricSpec spec = specs[i];
            int x = startX + i * (columnW + gap);
            addText(
                    x - 8,
                    GAUGE_Y - 16,
                    columnW + 16,
                    9,
                    () -> Component.literal(spec.label()),
                    TEXT_PRIMARY,
                    TextTexture.TextType.NORMAL);
            addProgress(
                    x,
                    GAUGE_Y,
                    columnW,
                    GAUGE_H,
                    () -> spec.metricSupplier().get().percent(),
                    spec.fillColor(),
                    ProgressTexture.FillDirection.DOWN_TO_UP);
            addText(
                    x - 6,
                    GAUGE_Y + GAUGE_H + 8,
                    columnW + 12,
                    9,
                    () -> Component.literal(
                            formatPercent(spec.metricSupplier().get().percent())),
                    TEXT_VALUE,
                    TextTexture.TextType.NORMAL);
        }
    }

    @Override
    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        drawPanel(graphics, LEFT_PANEL_X, LEFT_PANEL_Y, LEFT_PANEL_W, LEFT_PANEL_H);
        drawPanel(graphics, RIGHT_PANEL_X, RIGHT_PANEL_Y, RIGHT_PANEL_W, RIGHT_PANEL_H);
        drawPanel(graphics, LEFT_PANEL_X, FORMED_BAR_Y, RIGHT_PANEL_X + RIGHT_PANEL_W - LEFT_PANEL_X, FORMED_BAR_H);
        drawCenteredLocalString(
                graphics,
                Component.translatable("gui.neoecoae.machine.formed")
                        .append(": ")
                        .append(boolText(currentState().formed())),
                LEFT_PANEL_X,
                FORMED_BAR_Y + 7,
                RIGHT_PANEL_X + RIGHT_PANEL_W - LEFT_PANEL_X,
                currentState().formed() ? TEXT_SUCCESS : TEXT_ERROR);
        drawCenteredLocalString(
                graphics, Component.literal("Capacity"), RIGHT_PANEL_X, RIGHT_PANEL_Y + 8, RIGHT_PANEL_W, TEXT_MUTED);
    }

    @Override
    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isMouseIn(PRIORITY_BUTTON_X, PRIORITY_BUTTON_Y, PRIORITY_BUTTON_W, PRIORITY_BUTTON_H, mouseX, mouseY)) {
            graphics.renderComponentTooltip(font(), List.of(GuiText.Priority.text()), mouseX, mouseY);
            return;
        }

        MetricSpec[] specs = metricSpecs();
        int count = specs.length;
        int columnW = count == 3 ? 30 : 38;
        int gap = count == 3 ? 10 : 20;
        int totalW = columnW * count + gap * (count - 1);
        int startX = RIGHT_PANEL_X + (RIGHT_PANEL_W - totalW) / 2;

        for (int i = 0; i < count; i++) {
            int x = startX + i * (columnW + gap);
            if (!isMouseIn(x, GAUGE_Y, columnW, GAUGE_H, mouseX, mouseY)) {
                continue;
            }
            Metric metric = specs[i].metricSupplier().get();
            graphics.renderTooltip(
                    font(),
                    List.of(
                            Component.literal(specs[i].label() + ": " + formatPercent(metric.percent())),
                            Component.literal(
                                    formatStorageBytes(metric.used()) + " / " + formatStorageBytes(metric.max())),
                            Component.literal("Types: " + fmt(metric.usedTypes()) + " / " + fmt(metric.totalTypes()))),
                    Optional.empty(),
                    mouseX,
                    mouseY);
            return;
        }
    }

    private MetricSpec[] metricSpecs() {
        return chemicalMode
                ? new MetricSpec[] {
                    new MetricSpec("Items", 0xFF43B678, this::itemMetric),
                    new MetricSpec("Fluids", 0xFF3A8FD6, this::fluidMetric),
                    new MetricSpec("Chemicals", 0xFF8C62D6, this::chemicalMetric)
                }
                : new MetricSpec[] {
                    new MetricSpec("Items", 0xFF43B678, this::itemMetric),
                    new MetricSpec("Fluids", 0xFF3A8FD6, this::fluidMetric)
                };
    }

    private Metric itemMetric() {
        return createMetric(findTypeState(currentState().typeStates(), "item"));
    }

    private Metric fluidMetric() {
        return createMetric(findTypeState(currentState().typeStates(), "fluid"));
    }

    private Metric chemicalMetric() {
        return createMetric(findChemicalTypeState(currentState().typeStates()));
    }

    private static Metric createMetric(NEStorageUiTypeState state) {
        return state == null
                ? new Metric(0, 0, 0, 0)
                : new Metric(state.usedBytes(), state.totalBytes(), state.usedTypes(), state.totalTypes());
    }

    private static IGuiTexture buttonTexture() {
        return new GuiTextureGroup(new ColorRectAndBorderTexture(0xFFF1F3F6, 0xFF8A96A8, 1.0F));
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
                new String[] {"mekanism", "chemical", "chem", "gas", "infuse", "infusion", "pigment", "slurry"};
        for (String needle : needles) {
            NEStorageUiTypeState state = findTypeState(types, needle);
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    private static String formatStorageBytes(long value) {
        long abs = value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
        if (abs < BYTES_IN_G) {
            return fmt(value);
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
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static String formatPercent(double pct) {
        return String.format(Locale.US, "%.1f%%", pct * 100.0D);
    }

    private record Metric(long used, long max, long usedTypes, long totalTypes) {
        private double percent() {
            return NEStorageControllerWidget.percent(used, max);
        }
    }

    private record MetricSpec(String label, int fillColor, java.util.function.Supplier<Metric> metricSupplier) {}
}
