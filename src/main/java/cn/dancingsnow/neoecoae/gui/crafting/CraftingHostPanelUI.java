package cn.dancingsnow.neoecoae.gui.crafting;

import appeng.client.gui.Icon;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskCards;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskEntry;
import cn.dancingsnow.neoecoae.gui.task.HostTaskListElement;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.gui.Font;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class CraftingHostPanelUI {
    public static final int UI_WIDTH = 304;
    public static final int UI_HEIGHT = 196;

    private static final int HEADER_HEIGHT = 16;
    private static final int TOP_PANEL_HEIGHT = 70;
    private static final int BOTTOM_PANEL_HEIGHT = 88;
    private static final int STATUS_WIDTH = 76;
    private static final int STATS_WIDTH = 114;
    private static final int GAUGE_WIDTH = 90;
    private static final int FORMED_STATUS_WIDTH = 72;
    private static final int PERFORMANCE_WIDTH = 60;
    private static final int INVENTORY_WIDTH = 162;
    private static final int TASK_WIDTH = 122;
    private static final int TASK_HEIGHT = 88;
    private static final int TASK_CARD_X = 8;
    private static final int TASK_CARD_Y = 19;
    private static final int TASK_CARD_WIDTH = TASK_WIDTH - 16;
    private static final int TASK_CARD_HEIGHT = 16;
    private static final int TASK_CARD_STRIDE = 18;
    private static final int TASK_LIST_BOTTOM_Y = TASK_HEIGHT - 4;
    private static final int TASK_SCROLLBAR_WIDTH = 3;
    private static final int PANEL_TEXT_SHIFT_X = -2;
    private static final int TOOLBAR_BUTTON_SIZE = 16;
    private static final float COMPACT_FONT_SIZE = 8.0F;
    private static final float INLINE_STATS_FONT_SIZE = 7.0F;
    private static final long ENERGY_GAUGE_REFERENCE = 1_000_000L;
    private static final int ROOT_TEXT = 0x3F3D52;
    private static final int PANEL_TEXT = 0xFFEFEAF8;
    private static final int PANEL_MUTED = 0xFFC7BFCD;
    private static final int PANEL_VALUE = 0xFF8377FF;
    private static final int PANEL_OVERFLOW_VALUE = 0xFF000000;
    private static final int PANEL_TIME_VALUE = 0xFF55A7FF;
    private static final int PANEL_SUCCESS = 0xFF55FF8A;
    private static final int PANEL_WARNING = 0xFFFF6A75;
    private static final ThreadLocal<DecimalFormat> PERFORMANCE_MS_FORMAT = ThreadLocal.withInitial(() ->
        new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.US)));

    private CraftingHostPanelUI() {
    }

    public record Config(
        Supplier<Component> title,
        BooleanSupplier formed,
        BooleanSupplier overclocked,
        Runnable toggleOverclocked,
        BooleanSupplier activeCooling,
        Runnable toggleActiveCooling,
        IntSupplier occupiedRecipeSlots,
        IntSupplier maxRecipeSlots,
        IntSupplier batchParallel,
        IntSupplier overflowThreads,
        IntSupplier effectiveOverclockTimes,
        LongSupplier performanceAverageNanos,
        LongSupplier energyUsage,
        IntSupplier coolantAmount,
        IntSupplier coolantCapacity,
        IntSupplier coolantMaxOverclock,
        Supplier<FluidStack> coolantFluid,
        Supplier<HolderLookup.Provider> registries,
        Supplier<List<ComputationTaskEntry>> tasks
    ) {
    }

    public static UIElement create(Config config) {
        UIElement root = new UIElement()
            .addClasses("eco-host-panel", "eco-crafting-host")
            .layout(layout -> layout
                .width(UI_WIDTH)
                .height(UI_HEIGHT)
                .flexDirection(FlexDirection.COLUMN));
        root.addChildren(header(config), topPanels(config), bottomPanels(config));
        return root;
    }

    private static UIElement header(Config config) {
        UIElement header = new UIElement()
            .addClass("eco-host-header")
            .layout(layout -> layout
                .widthPercent(100)
                .height(HEADER_HEIGHT)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER));

        Label title = boundLabel(config.title, ROOT_TEXT);
        title.addClass("eco-host-title");
        title.layout(layout -> layout.flex(1).height(10));
        Label status = boundLabel(() -> Component.translatable("gui.neoecoae.machine.formed")
            .append(": ")
            .append(Component.translatable(config.formed.getAsBoolean()
                ? "gui.neoecoae.common.yes"
                : "gui.neoecoae.common.no").withColor(config.formed.getAsBoolean() ? HostText.USED : PANEL_WARNING)), ROOT_TEXT);
        status.addClass("eco-host-formed-status");
        status.textStyle(style -> style.textAlignHorizontal(Horizontal.RIGHT));
        status.layout(layout -> layout.width(FORMED_STATUS_WIDTH).height(10));

        UIElement toolbar = new UIElement()
            .addClass("eco-host-toolbar")
            .layout(layout -> layout.height(TOOLBAR_BUTTON_SIZE).flexDirection(FlexDirection.ROW));
        toolbar.addChildren(
            toolbarButton(config.toggleOverclocked, Icon.POWER_UNIT_AE, () -> Component.translatable(
                config.overclocked.getAsBoolean() ? "gui.neoecoae.crafting.overclock.on" : "gui.neoecoae.crafting.overclock.off")),
            toolbarButton(config.toggleActiveCooling, Icon.TYPE_FILTER_ALL, () -> Component.translatable(
                config.activeCooling.getAsBoolean() ? "gui.neoecoae.crafting.active_cooling.on" : "gui.neoecoae.crafting.active_cooling.off"))
        );

        header.addChildren(
            title,
            status,
            toolbar
        );
        return header;
    }

    private static Button toolbarButton(Runnable action, Icon icon, Supplier<Component> tooltip) {
        Button button = new Button()
            .noText()
            .addPreIcon(AETextures.icon(icon))
            .setOnServerClick(event -> action.run());
        button.buttonStyle(style -> style
            .baseTexture(Sprites.RECT_RD)
            .hoverTexture(Sprites.RECT_RD_LIGHT)
            .pressedTexture(Sprites.RECT_RD_DARK));
        button.addClass("eco-host-toolbar-button");
        button.layout(layout -> layout.width(TOOLBAR_BUTTON_SIZE).height(TOOLBAR_BUTTON_SIZE));

        BindableValue<Component> syncedTooltip = syncedComponent(tooltip);
        syncedTooltip.setDisplay(false);
        button.addChild(syncedTooltip);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = HoverTooltips.empty().append(syncedTooltip.getValue()));
        return button;
    }

    private static UIElement topPanels(Config config) {
        UIElement row = new UIElement().layout(layout -> layout
            .widthPercent(100)
            .height(TOP_PANEL_HEIGHT)
            .flexDirection(FlexDirection.ROW));
        row.addClass("eco-host-panel-row");
        row.addChildren(statusPanel(config), statsPanel(config), gaugePanel(config));
        return row;
    }

    private static UIElement statusPanel(Config config) {
        UIElement panel = hostCard(STATUS_WIDTH);
        panel.addChild(sectionLabel("gui.neoecoae.crafting.ui.status"));
        panel.addChild(statusRow("gui.neoecoae.crafting.ui.overclock_short", config.overclocked));
        panel.addChild(statusRow("gui.neoecoae.crafting.ui.cooling_short", config.activeCooling));
        return panel;
    }

    private static UIElement statusRow(String key, BooleanSupplier value) {
        UIElement row = new UIElement()
            .addClass("eco-host-status-row")
            .layout(layout -> layout.widthPercent(100).height(13).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER));
        row.addChild(statusIndicator(value));
        row.addChild(boundLabel(() -> Component.translatable(key)
            .withColor(PANEL_MUTED)
            .append(": ")
            .append(Component.translatable(value.getAsBoolean()
                ? "gui.neoecoae.common.on"
                : "gui.neoecoae.common.off").withColor(value.getAsBoolean() ? PANEL_SUCCESS : PANEL_MUTED)), PANEL_MUTED)
            .layout(layout -> layout.flex(1).height(10)));
        return row;
    }

    private static UIElement statusIndicator(BooleanSupplier value) {
        UIElement frame = new UIElement()
            .addClass("eco-host-status-light-edge")
            .layout(layout -> layout.width(13).height(13).paddingAll(1));
        UIElement border = new UIElement()
            .addClass("eco-host-status-light-border")
            .layout(layout -> layout.widthPercent(100).heightPercent(100).paddingAll(1));
        UIElement lamp = new UIElement()
            .addClass(value.getAsBoolean() ? "eco-host-status-light-on" : "eco-host-status-light-off")
            .layout(layout -> layout.widthPercent(100).heightPercent(100));
        border.addChild(lamp);
        frame.addChild(border);

        BindableValue<Boolean> syncedValue = new BindableValue<>(value.getAsBoolean());
        syncedValue.bind(DataBindingBuilder.boolS2C(value::getAsBoolean).build());
        syncedValue.registerValueListener(enabled -> {
            lamp.removeClasses("eco-host-status-light-on", "eco-host-status-light-off");
            lamp.addClass(Boolean.TRUE.equals(enabled) ? "eco-host-status-light-on" : "eco-host-status-light-off");
        });
        syncedValue.setDisplay(false);
        frame.addChild(syncedValue);
        return frame;
    }

    private static UIElement statsPanel(Config config) {
        UIElement panel = hostCard(STATS_WIDTH);
        UIElement titleRow = new UIElement().layout(layout -> layout
            .widthPercent(100).height(10).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER));
        titleRow.addChild(sectionLabel("gui.neoecoae.crafting.ui.stats").layout(layout -> layout.flex(1).height(10)));
        titleRow.addChild(performanceLabel(config.performanceAverageNanos));
        panel.addChild(titleRow);
        panel.addChild(boundLabel(() -> Component.translatable("gui.neoecoae.crafting.ui.recipe_slots")
            .append(": ")
            .append(HostText.typeProgress(config.occupiedRecipeSlots.getAsInt(), config.maxRecipeSlots.getAsInt()).usedText())
            .append(" / ")
            .append(HostText.typeProgress(config.occupiedRecipeSlots.getAsInt(), config.maxRecipeSlots.getAsInt()).maxText()), PANEL_MUTED));
        panel.addChild(new ProgressBar()
            .label(label -> label.setText(""))
            .barContainer(element -> element.layout(layout -> layout.paddingAll(1)))
            .bind(DataBindingBuilder.floatValS2C(() -> HostText.usageRatio(
                config.occupiedRecipeSlots.getAsInt(), config.maxRecipeSlots.getAsInt())).build())
            .addClass("eco-host-stats-progress")
            .layout(layout -> layout.widthPercent(100).height(9)));
        panel.addChild(boundLabel(() -> Component.translatable("gui.neoecoae.crafting.ui.batch_parallel")
            .append(": ").append(Tooltips.ofNumber(config.batchParallel.getAsInt())), PANEL_MUTED));
        UIElement overflowRow = new UIElement().layout(layout -> layout
            .widthPercent(100).height(9).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));
        Label overflow = boundLabel(() -> Component.translatable("gui.neoecoae.host.crafting.overflow")
            .append(": ").append(Tooltips.ofNumber(config.overflowThreads.getAsInt()).copy().withColor(PANEL_OVERFLOW_VALUE)), PANEL_MUTED);
        overflow.textStyle(CraftingHostPanelUI::inlineStatsTextStyle);
        Label timeRatio = boundLabel(() -> Component.translatable("gui.neoecoae.crafting.ui.recipe_time_ratio")
            .append(": ").append(Component.literal(formatRecipeTimeMultiplier(config.effectiveOverclockTimes.getAsInt()))
                .withColor(PANEL_TIME_VALUE)), PANEL_TIME_VALUE);
        timeRatio.textStyle(CraftingHostPanelUI::inlineStatsTextStyle);
        overflowRow.addChildren(overflow, timeRatio);
        panel.addChild(overflowRow);
        return panel;
    }

    private static Label performanceLabel(LongSupplier performanceAverageNanos) {
        Label label = boundLabel(() -> Component.literal(formatPerformanceCornerValue(performanceAverageNanos.getAsLong())), PANEL_VALUE);
        label.addClass("eco-host-performance");
        label.textStyle(style -> style.textAlignHorizontal(Horizontal.RIGHT));
        label.layout(layout -> layout.width(PERFORMANCE_WIDTH).height(10));
        BindableValue<Component> detail = syncedComponent(() -> Component.literal(formatPerformanceValue(performanceAverageNanos.getAsLong())));
        detail.setDisplay(false);
        label.addChild(detail);
        label.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = HoverTooltips.empty().append(
            Component.translatable("gui.neoecoae.crafting.performance"), detail.getValue()));
        return label;
    }

    private static UIElement gaugePanel(Config config) {
        UIElement panel = hostCard(GAUGE_WIDTH);
        panel.addChild(sectionLabel("gui.neoecoae.crafting.ui.energy_cooling"));
        UIElement gauges = new UIElement().layout(layout -> layout
            .widthPercent(100)
            .flex(1)
            .flexDirection(FlexDirection.ROW)
            .alignItems(AlignItems.CENTER)
            .justifyContent(AlignContent.CENTER));
        gauges.addClass("eco-host-gauge-row");
        gauges.addChildren(energyGauge(config.energyUsage), coolantGauge(config));
        panel.addChild(gauges);
        return panel;
    }

    private static ProgressBar energyGauge(LongSupplier energyUsage) {
        DynamicEnergyProgressBar gauge = new DynamicEnergyProgressBar();
        gauge.label(label -> label.setText(""));
        gauge.progressBarStyle(style -> style.fillDirection(FillDirection.DOWN_TO_UP));
        gauge.bind(DataBindingBuilder.floatValS2C(() -> HostText.usageRatio(
            Math.max(0L, energyUsage.getAsLong()), ENERGY_GAUGE_REFERENCE)).build());
        gauge.addClass("eco-host-energy-gauge");
        gauge.layout(layout -> layout.width(20).height(32));

        BindableValue<Component> tooltip = syncedComponent(() -> Tooltips.ofNumber(Math.max(0L, energyUsage.getAsLong())).append(" AE/t"));
        tooltip.setDisplay(false);
        gauge.addChild(tooltip);
        gauge.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = HoverTooltips.empty().append(
            Component.translatable("gui.neoecoae.crafting.ui.energy_usage"), tooltip.getValue()));
        return gauge;
    }

    private static UIElement coolantGauge(Config config) {
        UIElement frame = new UIElement()
            .addClass("eco-host-coolant-gauge")
            .layout(layout -> layout.width(23).height(32));
        CoolantFluidSlot fluid = new CoolantFluidSlot(config);
        fluid.layout(layout -> layout.widthPercent(100).heightPercent(100));
        frame.addChild(fluid);
        return frame;
    }

    private static UIElement bottomPanels(Config config) {
        UIElement row = new UIElement().layout(layout -> layout
            .widthPercent(100)
            .height(BOTTOM_PANEL_HEIGHT)
            .flexDirection(FlexDirection.ROW));
        row.addClass("eco-host-bottom-row");
        row.addChildren(inventoryPanel(), taskPanel(config));
        return row;
    }

    private static UIElement inventoryPanel() {
        UIElement panel = new UIElement()
            .addClass("eco-host-inventory")
            .layout(layout -> layout.width(INVENTORY_WIDTH).height(BOTTOM_PANEL_HEIGHT).flexDirection(FlexDirection.COLUMN));
        panel.addChild(boundLabel(() -> Component.translatable("container.inventory"), ROOT_TEXT).layout(layout -> layout.height(10)));
        panel.addChild(new InventorySlots().layout(layout -> layout.marginTop(2)));
        return panel;
    }

    private static UIElement taskPanel(Config config) {
        UIElement panel = new UIElement()
            .addClasses("eco-host-card", "eco-host-task-panel")
            .layout(layout -> layout.width(TASK_WIDTH).height(TASK_HEIGHT));
        panel.addChild(new HostTaskListElement(
            config.registries,
            config.tasks,
            TASK_WIDTH,
            TASK_HEIGHT,
            TASK_CARD_X,
            TASK_CARD_Y,
            TASK_CARD_WIDTH,
            TASK_CARD_HEIGHT,
            TASK_CARD_STRIDE,
            TASK_LIST_BOTTOM_Y,
            TASK_SCROLLBAR_WIDTH
        ) {
            @Override
            protected List<Component> tooltipLines(ComputationTaskEntry entry) {
                return craftingTooltip(entry);
            }

            @Override
            protected void drawTaskCard(GUIContext guiContext, Font font, ComputationTaskEntry entry, float x, float y) {
                drawCraftingTaskCard(guiContext, font, entry, Math.round(x), Math.round(y));
            }

            @Override
            protected int titleX() {
                return 8 + PANEL_TEXT_SHIFT_X;
            }

            @Override
            protected int countRightX() {
                return TASK_WIDTH - 12 + PANEL_TEXT_SHIFT_X;
            }

            @Override
            protected int scissorRight() {
                return TASK_WIDTH - 8;
            }

            @Override
            protected int scrollbarX() {
                return TASK_WIDTH - 9;
            }

            @Override
            protected float emptyTextX(Font font, String text) {
                return Math.max(0, TASK_WIDTH - font.width(text)) / 2.0F + PANEL_TEXT_SHIFT_X;
            }
        }.layout(layout -> layout.width(TASK_WIDTH).height(TASK_HEIGHT)));
        return panel;
    }

    private static UIElement hostCard(int width) {
        return new UIElement()
            .addClass("eco-host-card")
            .layout(layout -> layout.width(width).height(TOP_PANEL_HEIGHT).flexDirection(FlexDirection.COLUMN));
    }

    private static Label sectionLabel(String key) {
        Label label = boundLabel(() -> Component.translatable(key), PANEL_TEXT);
        label.addClass("eco-host-section-title");
        label.layout(layout -> layout.widthPercent(100).height(10));
        return label;
    }

    private static Label boundLabel(Supplier<Component> text, int color) {
        Label label = HostElements.textSegment(() -> text.get().copy().withColor(color), () -> color);
        label.addClass("eco-host-label");
        label.textStyle(CraftingHostPanelUI::compactTextStyle);
        return label;
    }

    private static BindableValue<Component> syncedComponent(Supplier<Component> supplier) {
        BindableValue<Component> value = new BindableValue<>(supplier.get());
        value.bind(DataBindingBuilder.componentS2C(supplier).build());
        return value;
    }

    private static void compactTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true)
            .adaptiveWidth(false)
            .fontSize(COMPACT_FONT_SIZE)
            .textWrap(TextWrap.HOVER_ROLL)
            .textShadow(false);
    }

    private static void inlineStatsTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true)
            .adaptiveWidth(true)
            .fontSize(INLINE_STATS_FONT_SIZE)
            .textWrap(TextWrap.HOVER_ROLL)
            .textShadow(false);
    }

    static String formatRecipeTimeMultiplier(int effectiveOverclockTimes) {
        int level = Math.clamp(effectiveOverclockTimes, 0, 9);
        int ticks = (int) Math.ceil(10.0D / (level + 1));
        return String.format(Locale.ROOT, "%.1fx", ticks / 10.0D);
    }

    private static String formatPerformanceCornerValue(long averageNanos) {
        long safeNanos = Math.max(0L, averageNanos);
        long micros = Math.round(safeNanos / 1_000.0D);
        if (micros < 1_000L) {
            return micros + " us";
        }
        return PERFORMANCE_MS_FORMAT.get().format(safeNanos / 1_000_000.0D) + " ms";
    }

    private static String formatPerformanceValue(long averageNanos) {
        long safeNanos = Math.max(0L, averageNanos);
        long micros = Math.round(safeNanos / 1_000.0D);
        String millis = PERFORMANCE_MS_FORMAT.get().format(safeNanos / 1_000_000.0D);
        return micros + " us/" + millis + " ms";
    }

    private static List<Component> craftingTooltip(ComputationTaskEntry entry) {
        List<Component> lines = new ArrayList<>();
        lines.addAll(ComputationTaskCards.tooltipLines(entry));
        lines.add(Component.translatable(ComputationTaskCards.statusKey(entry.status()))
            .append(" ")
            .append(Component.literal(ComputationTaskCards.progressText(entry))));
        return lines;
    }

    private static void drawCraftingTaskCard(GUIContext guiContext, Font font, ComputationTaskEntry entry, int x, int y) {
        int accent = ComputationTaskCards.statusColor(entry.status());
        guiContext.graphics.fill(x, y, x + TASK_CARD_WIDTH, y + TASK_CARD_HEIGHT, 0xFFD8D3E4);
        guiContext.graphics.fill(x + 1, y + 1, x + TASK_CARD_WIDTH - 1, y + TASK_CARD_HEIGHT - 1, 0xFF17141E);
        guiContext.graphics.fill(x + 2, y + 2, x + TASK_CARD_WIDTH - 2, y + TASK_CARD_HEIGHT - 2, 0xFF2C2735);
        guiContext.graphics.fill(x + 2, y + TASK_CARD_HEIGHT - 2, x + TASK_CARD_WIDTH - 2, y + TASK_CARD_HEIGHT - 1, accent);
        if (!entry.output().isEmpty()) {
            DrawerHelper.drawItemStack(guiContext.graphics, entry.output(), x + 3, y, -1, null);
        }
        String name = font.plainSubstrByWidth(entry.output().getHoverName().getString(), TASK_CARD_WIDTH - 52);
        HostTaskListElement.drawString(guiContext, font, name, x + 22 + PANEL_TEXT_SHIFT_X, y + 3, HostText.PRIMARY);
        HostTaskListElement.drawRightString(guiContext, font,
            "x" + ComputationTaskCards.compactAmount(entry.outputAmount()),
            x + TASK_CARD_WIDTH - 4, y + 3, HostText.VALUE);
    }

    private static final class DynamicEnergyProgressBar extends ProgressBar {
        @Override
        public ProgressBar setValue(@Nullable Float value) {
            super.setValue(value);
            float ratio = value == null ? 0.0F : value;
            if (ratio >= 0.9F) {
                removeClass("eco-host-energy-medium");
                addClass("eco-host-energy-warning");
            } else if (ratio >= 0.5F) {
                removeClass("eco-host-energy-warning");
                addClass("eco-host-energy-medium");
            } else {
                removeClasses("eco-host-energy-warning", "eco-host-energy-medium");
            }
            return this;
        }
    }

    private static final class CoolantFluidSlot extends FluidSlot {
        private int maxOverclock = -1;

        private CoolantFluidSlot(Config config) {
            setAllowClickFilled(false);
            setAllowClickDrained(false);
            amountLabel.setDisplay(false);
            slotStyle(style -> style
                .fillDirection(FillDirection.DOWN_TO_UP)
                .showFluidTooltips(false));
            style(style -> style.backgroundTexture(new ColorRectTexture(0xFF17141E)));
            bind(DataBindingBuilder.fluidStackS2C(() -> coolantStack(config)).build());
            addSyncValue(DataBindingBuilder.intValS2C(() -> Math.max(0, config.coolantCapacity.getAsInt()))
                .remoteSetter(this::setCapacity).build().getSyncValue());
            addSyncValue(DataBindingBuilder.intValS2C(config.coolantMaxOverclock::getAsInt)
                .remoteSetter(value -> maxOverclock = value).build().getSyncValue());
        }

        @Override
        public List<Component> getFullTooltipTexts() {
            return List.of(
                Component.translatable("gui.neoecoae.host.crafting.coolant"),
                Component.literal(HostText.typeProgress(getFluid().getAmount(), getCapacity()).usedText() + " / "
                    + HostText.typeProgress(getFluid().getAmount(), getCapacity()).maxText() + " mB"),
                Component.translatable("gui.neoecoae.crafting.coolant_max_overclock",
                    maxOverclock < 0 ? "-" : Tooltips.ofNumber(maxOverclock))
            );
        }

        private static FluidStack coolantStack(Config config) {
            FluidStack fluid = config.coolantFluid.get();
            int amount = Math.max(0, config.coolantAmount.getAsInt());
            return fluid == null || fluid.isEmpty() || amount == 0
                ? FluidStack.EMPTY
                : fluid.copyWithAmount(amount);
        }
    }
}
