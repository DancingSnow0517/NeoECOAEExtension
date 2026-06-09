package cn.dancingsnow.neoecoae.gui.ldlib;

import appeng.core.localization.GuiText;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEStorageControllerMenu;
import cn.dancingsnow.neoecoae.network.NENetwork;
import cn.dancingsnow.neoecoae.network.NEStorageUiState;
import cn.dancingsnow.neoecoae.network.NEStorageUiTypeState;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectAndBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

/**
 * LDLib1 display for the Storage Controller. It keeps the existing UI state
 * packet stream and delegates Priority to the existing AE2 menu packet.
 */
public class NEStorageControllerLDLibUI extends NELDLibMachineScreen<NEStorageControllerMenu> {
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

    private final boolean chemicalMode = hasChemicalStorageIntegration();

    private boolean hasStorageState;
    private NEStorageUiState storageState;

    public NEStorageControllerLDLibUI(NEStorageControllerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, 358, 220);
        this.storageState = NEStorageUiState.empty(menu.getMachinePos());
    }

    public void setStorageUiState(NEStorageUiState state) {
        this.hasStorageState = true;
        this.storageState = state;
    }

    @Override
    protected void initLdWidgets() {
        addLdWidget(new ButtonWidget(
                        PRIORITY_BUTTON_X,
                        PRIORITY_BUTTON_Y,
                        PRIORITY_BUTTON_W,
                        PRIORITY_BUTTON_H,
                        buttonTexture(),
                        click -> NENetwork.CHANNEL.sendToServer(
                                new NENetwork.NEOpenStoragePriorityPacket(menu.getMachinePos()))))
                .setHoverTexture(new ColorRectAndBorderTexture(0x00000000, 0xFF4F7FB6, 1.0F));
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

        y = addTypeBlock(x, y, "Items", () -> itemMetric());
        y = addTypeBlock(x, y, "Fluids", () -> fluidMetric());
        if (chemicalMode) {
            addTypeBlock(x, y, "Chemicals", () -> chemicalMetric());
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
        MetricSpec[] specs = chemicalMode
                ? new MetricSpec[] {
                    new MetricSpec("Items", 0xFF43B678, () -> itemMetric()),
                    new MetricSpec("Fluids", 0xFF3A8FD6, () -> fluidMetric()),
                    new MetricSpec("Chemicals", 0xFF8C62D6, () -> chemicalMetric())
                }
                : new MetricSpec[] {
                    new MetricSpec("Items", 0xFF43B678, () -> itemMetric()),
                    new MetricSpec("Fluids", 0xFF3A8FD6, () -> fluidMetric())
                };

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
    protected void renderLdBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        drawPanel(guiGraphics, LEFT_PANEL_X, LEFT_PANEL_Y, LEFT_PANEL_W, LEFT_PANEL_H);
        drawPanel(guiGraphics, RIGHT_PANEL_X, RIGHT_PANEL_Y, RIGHT_PANEL_W, RIGHT_PANEL_H);
        drawPanel(guiGraphics, LEFT_PANEL_X, FORMED_BAR_Y, RIGHT_PANEL_X + RIGHT_PANEL_W - LEFT_PANEL_X, FORMED_BAR_H);
        drawCenteredLocalString(
                guiGraphics,
                Component.translatable("gui.neoecoae.machine.formed")
                        .append(": ")
                        .append(boolText(currentState().formed())),
                LEFT_PANEL_X,
                FORMED_BAR_Y + 7,
                RIGHT_PANEL_X + RIGHT_PANEL_W - LEFT_PANEL_X,
                currentState().formed() ? TEXT_SUCCESS : TEXT_ERROR);
        drawCenteredLocalString(
                guiGraphics,
                Component.literal("Capacity"),
                RIGHT_PANEL_X,
                RIGHT_PANEL_Y + 8,
                RIGHT_PANEL_W,
                TEXT_MUTED);
    }

    @Override
    protected void renderLdTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (isMouseIn(PRIORITY_BUTTON_X, PRIORITY_BUTTON_Y, PRIORITY_BUTTON_W, PRIORITY_BUTTON_H, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, List.of(GuiText.Priority.text()), mouseX, mouseY);
            return;
        }

        MetricSpec[] specs = chemicalMode
                ? new MetricSpec[] {
                    new MetricSpec("Items", 0xFF43B678, () -> itemMetric()),
                    new MetricSpec("Fluids", 0xFF3A8FD6, () -> fluidMetric()),
                    new MetricSpec("Chemicals", 0xFF8C62D6, () -> chemicalMetric())
                }
                : new MetricSpec[] {
                    new MetricSpec("Items", 0xFF43B678, () -> itemMetric()),
                    new MetricSpec("Fluids", 0xFF3A8FD6, () -> fluidMetric())
                };
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
            guiGraphics.renderTooltip(
                    font,
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

    private NEStorageUiState currentState() {
        if (hasStorageState) {
            return storageState;
        }
        if (minecraft == null || minecraft.level == null) {
            return storageState;
        }
        BlockEntity be = minecraft.level.getBlockEntity(menu.getMachinePos());
        if (be instanceof ECOStorageSystemBlockEntity storage) {
            NEStorageUiTypeState fallbackType = new NEStorageUiTypeState(
                    ResourceLocation.fromNamespaceAndPath("neoecoae", "legacy"),
                    "Storage",
                    storage.getTotalUsedTypes(),
                    storage.getTotalTypes(),
                    storage.getTotalUsedBytes(),
                    storage.getTotalBytes());
            return new NEStorageUiState(
                    menu.getMachinePos(),
                    Collections.singletonList(fallbackType),
                    storage.getStoredEnergy(),
                    storage.getMaxEnergy(),
                    storage.isFormed());
        }
        return storageState;
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
        if (state == null) {
            return new Metric(0, 0, 0, 0);
        }
        return new Metric(state.usedBytes(), state.totalBytes(), state.usedTypes(), state.totalTypes());
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

    private record Metric(long used, long max, long usedTypes, long totalTypes) {
        private double percent() {
            return NELDLibMachineScreen.percent(used, max);
        }
    }

    private record MetricSpec(String label, int fillColor, java.util.function.Supplier<Metric> metricSupplier) {}
}
