package cn.dancingsnow.neoecoae.gui;

import appeng.client.gui.Icon;
import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostWidgets;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.FluidStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class CraftingHostPanelUI {
    public static final int UI_WIDTH = 304;
    public static final int UI_HEIGHT = 196;

    private static final int PANEL_MARGIN = 6;
    private static final float COMPACT_FONT_SIZE = 8.0F;
    private static final int TOOLBAR_BUTTON_SIZE = 16;
    private static final int TOOLBAR_BUTTON_STRIDE = TOOLBAR_BUTTON_SIZE + 4;
    private static final int TOOLBAR_X = UI_WIDTH - PANEL_MARGIN - TOOLBAR_BUTTON_SIZE * 2 - 4;
    private static final int TOOLBAR_Y = 4;
    private static final int CONTENT_Y = 26;
    private static final int INSET_H = 70;
    private static final int STATUS_X = 12;
    private static final int STATUS_W = 76;
    private static final int STATS_X = STATUS_X + STATUS_W + 6;
    private static final int STATS_W = 126;
    private static final int GAUGE_X = STATS_X + STATS_W + 6;
    private static final int GAUGE_W = UI_WIDTH - GAUGE_X - PANEL_MARGIN;
    private static final int INVENTORY_LABEL_Y = 101;
    private static final int INVENTORY_X = 12;
    private static final int TASK_X = INVENTORY_X + 18 * 9 + 8;
    private static final int TASK_Y = INVENTORY_LABEL_Y - 2;
    private static final int TASK_W = UI_WIDTH - TASK_X - PANEL_MARGIN;
    private static final int TASK_H = 89;
    private static final int TASK_CARD_X = 8;
    private static final int TASK_CARD_Y = 19;
    private static final int TASK_CARD_W = TASK_W - 16;
    private static final int TASK_CARD_H = 16;
    private static final int TASK_CARD_STRIDE = 18;
    private static final int TASK_LIST_BOTTOM_Y = TASK_H - 4;
    private static final int TASK_SCROLLBAR_W = 3;
    private static final int GAUGE_BAR_Y = 25;
    private static final int GAUGE_BAR_H = 32;
    private static final int GAUGE_BAR_W = 23;
    private static final int ENERGY_GAUGE_BAR_W = GAUGE_BAR_W - 3;
    private static final int PANEL_CONTENT_OFFSET_X = -4;
    private static final int PANEL_CONTENT_OFFSET_Y = -4;
    private static final int PANEL_TEXT_SHIFT_X = -2;
    private static final long ENERGY_GAUGE_REFERENCE = 1_000_000L;
    private static final int ROOT_TEXT = 0x3F3D52;
    private static final int HEADER_LABEL = 0x5D5D5D;
    private static final int PANEL_EDGE = 0xFFD8D3E4;
    private static final int PANEL_BORDER = 0xFF17141E;
    private static final int PANEL_TEXT = 0xFFEFEAF8;
    private static final int PANEL_MUTED = 0xFFC7BFCD;
    private static final int PANEL_VALUE = 0xFF8377FF;
    private static final int PANEL_OVERFLOW_VALUE = 0xFF000000;
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
        UIElement root = new UIElement().layout(layout -> {
            layout.width(UI_WIDTH);
            layout.height(UI_HEIGHT);
        }).addClass("panel_bg");

        root.addChild(headerLine(config));
        root.addChild(toolbarButton(
            TOOLBAR_X,
            config.toggleOverclocked,
            Icon.POWER_UNIT_AE,
            () -> Component.translatable(config.overclocked().getAsBoolean()
                ? "gui.neoecoae.crafting.overclock.on"
                : "gui.neoecoae.crafting.overclock.off")
        ));
        root.addChild(toolbarButton(
            TOOLBAR_X + TOOLBAR_BUTTON_STRIDE,
            config.toggleActiveCooling,
            Icon.TYPE_FILTER_ALL,
            () -> Component.translatable(config.activeCooling().getAsBoolean()
                ? "gui.neoecoae.crafting.active_cooling.on"
                : "gui.neoecoae.crafting.active_cooling.off")
        ));
        root.addChild(statusPanel(config));
        root.addChild(statsPanel(config));
        root.addChild(gaugePanel(config));
        root.addChild(inventoryPanel());
        root.addChild(taskPanel(config));
        return root;
    }

    private static UIElement headerLine(Config config) {
        return new HeaderLineElement(config)
            .layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(8);
                layout.top(8);
                layout.width(TOOLBAR_X - 16);
                layout.height(10);
            });
    }

    private static Button toolbarButton(
        int x,
        Runnable action,
        Icon icon,
        Supplier<Component> tooltip
    ) {
        Button button = new SyncedToolbarButton(action, icon, tooltip);
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(TOOLBAR_Y);
            layout.width(TOOLBAR_BUTTON_SIZE);
            layout.height(TOOLBAR_BUTTON_SIZE);
        });
        return button;
    }

    private static UIElement statusPanel(Config config) {
        ScrollerView panel = insetPanel(STATUS_X, CONTENT_Y, STATUS_W, INSET_H);
        panel.addScrollViewChild(sectionLabel("gui.neoecoae.crafting.ui.status", 8, 5, 60));
        panel.addScrollViewChild(statusRow("gui.neoecoae.crafting.ui.overclock_short", config.overclocked, 8, 22));
        panel.addScrollViewChild(statusRow("gui.neoecoae.crafting.ui.cooling_short", config.activeCooling, 8, 38));
        return panel;
    }

    private static UIElement statsPanel(Config config) {
        ScrollerView panel = insetPanel(STATS_X, CONTENT_Y, STATS_W, INSET_H);
        panel.addScrollViewChild(sectionLabel("gui.neoecoae.crafting.ui.stats", 8, 5, 72));
        panel.addScrollViewChild(new PerformanceLabelElement(config.performanceAverageNanos()).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(STATS_W - 55);
            layout.top(5);
            layout.width(47);
            layout.height(9);
        }));
        panel.addScrollViewChild(panelTextLine(
            () -> Component.translatable("gui.neoecoae.crafting.ui.recipe_slots")
                .append(": ")
                .append(Component.literal(StorageHostText.typeProgress(
                    config.occupiedRecipeSlots().getAsInt(),
                    config.maxRecipeSlots().getAsInt()).usedText()))
                .append(" / ")
                .append(Component.literal(StorageHostText.typeProgress(
                    config.occupiedRecipeSlots().getAsInt(),
                    config.maxRecipeSlots().getAsInt()).maxText())),
            8,
            18,
            STATS_W - 16,
            PANEL_MUTED
        ));
        panel.addScrollViewChild(new ProgressBar()
            .label(label -> label.setText(""))
            .barContainer(element -> element.layout(layout -> layout.paddingAll(1)))
            .bind(DataBindingBuilder.floatValS2C(() -> StorageHostText.usageRatio(
                config.occupiedRecipeSlots().getAsInt(),
                config.maxRecipeSlots().getAsInt())).build())
            .layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(8);
                layout.top(31);
                layout.width(STATS_W - 16);
                layout.height(9);
            })
            .addClass("eco-host-progress"));
        panel.addScrollViewChild(panelTextLine(
            () -> Component.translatable("gui.neoecoae.crafting.ui.batch_parallel")
                .append(": ")
                .append(Tooltips.ofNumber(config.batchParallel().getAsInt())),
            8,
            45,
            STATS_W - 16,
            PANEL_MUTED
        ));
        panel.addScrollViewChild(panelTextLine(
            () -> Component.translatable("gui.neoecoae.host.crafting.overflow")
                .append(": ")
                .append(Tooltips.ofNumber(config.overflowThreads().getAsInt()).copy().withColor(PANEL_OVERFLOW_VALUE)),
            8,
            56,
            STATS_W - 16,
            PANEL_MUTED
        ));
        return panel;
    }

    private static UIElement gaugePanel(Config config) {
        ScrollerView panel = insetPanel(GAUGE_X, CONTENT_Y, GAUGE_W, INSET_H);
        panel.addScrollViewChild(sectionLabel("gui.neoecoae.crafting.ui.energy_cooling", 8, 5, GAUGE_W - 16));
        panel.addScrollViewChild(new EnergyGaugeElement(config.energyUsage()
        ).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(8);
            layout.top(GAUGE_BAR_Y);
            layout.width(ENERGY_GAUGE_BAR_W);
            layout.height(GAUGE_BAR_H);
        }));
        panel.addScrollViewChild(new CoolantGaugeElement(
            config.coolantAmount(),
            config.coolantCapacity(),
            config.coolantMaxOverclock(),
            config.coolantFluid(),
            config.registries()
        ).layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(GAUGE_W - 8 - GAUGE_BAR_W);
            layout.top(GAUGE_BAR_Y);
            layout.width(GAUGE_BAR_W);
            layout.height(GAUGE_BAR_H);
        }));
        return panel;
    }

    private static UIElement inventoryPanel() {
        UIElement panel = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(INVENTORY_X);
            layout.top(INVENTORY_LABEL_Y);
            layout.width(162);
            layout.height(88);
            layout.flexDirection(FlexDirection.COLUMN);
        });
        panel.addChild(label(() -> Component.translatable("container.inventory"), ROOT_TEXT)
            .layout(layout -> layout.height(9)));
        panel.addChild(new InventorySlots().layout(layout -> layout.marginTop(2)));
        return panel;
    }

    private static UIElement taskPanel(Config config) {
        ScrollerView panel = insetPanel(TASK_X, TASK_Y, TASK_W, TASK_H);
        panel.addScrollViewChild(new HostTaskListElement(
                config.registries(),
                config.tasks(),
                TASK_W,
                TASK_H,
                TASK_CARD_X,
                TASK_CARD_Y,
                TASK_CARD_W,
                TASK_CARD_H,
                TASK_CARD_STRIDE,
                TASK_LIST_BOTTOM_Y,
                TASK_SCROLLBAR_W
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
                    return TASK_W - 12 + PANEL_TEXT_SHIFT_X;
                }

                @Override
                protected int scissorRight() {
                    return TASK_W - 8;
                }

                @Override
                protected int scrollbarX() {
                    return TASK_W - 9;
                }

                @Override
                protected float emptyTextX(Font font, String text) {
                    return Math.max(0, TASK_W - font.width(text)) / 2.0F + PANEL_TEXT_SHIFT_X;
                }
            }.layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(0);
                layout.top(0);
                layout.width(TASK_W - 4);
                layout.height(TASK_H - 4);
            }));
        return panel;
    }

    private static ScrollerView insetPanel(int x, int y, int width, int height) {
        ScrollerView panel = ECOHostWidgets.storagePanel(width, height, 0, 0, 0);
        panel.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(width);
            layout.height(height);
        });
        panel.scrollerStyle(style -> style
            .verticalScrollDisplay(ScrollDisplay.NEVER)
            .horizontalScrollDisplay(ScrollDisplay.NEVER));
        panel.viewContainer(view -> {
            view.getLayout().paddingAll(0);
            view.getLayout().marginLeft(PANEL_CONTENT_OFFSET_X);
            view.getLayout().marginTop(PANEL_CONTENT_OFFSET_Y);
        });
        return panel;
    }

    private static UIElement sectionLabel(String key, int x, int y, int width) {
        return panelTextLine(() -> Component.translatable(key), x, y, width, PANEL_TEXT);
    }

    private static UIElement statusRow(String key, BooleanSupplier value, int x, int y) {
        UIElement row = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(STATUS_W - 16);
            layout.height(12);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(4);
        });
        row.addChild(new StatusLight(value).layout(layout -> layout.width(13).height(13)));
        row.addChild(statusTextLine(key, value)
            .layout(layout -> {
                layout.positionType(TaffyPosition.RELATIVE);
                layout.marginLeft(PANEL_TEXT_SHIFT_X);
                layout.width(STATUS_W - 33);
                layout.height(9);
            }));
        return row;
    }

    private static Label statusTextLine(String key, BooleanSupplier value) {
        Label label = new Label();
        label.bind(DataBindingBuilder.componentS2C(() -> Component.translatable(key)
            .withColor(PANEL_MUTED)
            .append(Component.literal(": ").withColor(PANEL_MUTED))
            .append(Component.translatable(value.getAsBoolean()
                ? "gui.neoecoae.common.on"
                : "gui.neoecoae.common.off").withColor(value.getAsBoolean() ? PANEL_SUCCESS : PANEL_MUTED))).build());
        label.textStyle(CraftingHostPanelUI::labelTextStyle);
        return label;
    }

    private static UIElement textLine(Supplier<Component> text, int x, int y, int width, int color) {
        return label(text, color)
            .layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(x);
                layout.top(y);
                layout.width(width);
                layout.height(9);
            });
    }

    private static UIElement panelTextLine(Supplier<Component> text, int x, int y, int width, int color) {
        return textLine(text, x + PANEL_TEXT_SHIFT_X, y, width, color);
    }

    private static Label label(Supplier<Component> text, int color) {
        return label(text, () -> color);
    }

    private static Label label(Supplier<Component> text, IntSupplier color) {
        Label label = new Label();
        label.bind(DataBindingBuilder.componentS2C(() -> withColor(text.get(), color.getAsInt())).build());
        label.textStyle(CraftingHostPanelUI::labelTextStyle);
        return label;
    }

    private static MutableComponent withColor(Component component, int color) {
        return component.copy().withColor(color);
    }

    private static void labelTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true)
            .adaptiveWidth(false)
            .fontSize(COMPACT_FONT_SIZE)
            .textWrap(TextWrap.HOVER_ROLL)
            .textShadow(false);
    }

    private static float ratio(long value, long max) {
        if (max <= 0L) {
            return 0.0F;
        }
        return Math.clamp((float)value / (float)max, 0.0F, 1.0F);
    }

    private static int energyGaugeColor(float ratio) {
        if (ratio >= 0.9F) {
            return StorageHostText.WARNING;
        }
        if (ratio >= 0.5F) {
            return 0xFFE7A943;
        }
        return StorageHostText.USED;
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

    private static final class HeaderLineElement extends UIElement implements IBindable<Boolean> {
        private final Config config;
        private boolean syncedFormed;

        private HeaderLineElement(Config config) {
            this.config = config;
            this.syncedFormed = config.formed().getAsBoolean();
            bind(DataBindingBuilder.boolS2C(config.formed::getAsBoolean).build());
        }

        @Override
        public IDataSource<Boolean> setValue(@Nullable Boolean value) {
            syncedFormed = Boolean.TRUE.equals(value);
            return this;
        }

        @Override
        public Boolean getValue() {
            return syncedFormed;
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            Font font = Minecraft.getInstance().font;
            int x = (int)getPositionX();
            int y = (int)getPositionY();
            int width = (int)getSizeWidth();
            Component label = Component.translatable("gui.neoecoae.machine.formed").append(": ");
            Component value = Component.translatable(syncedFormed
                ? "gui.neoecoae.common.yes"
                : "gui.neoecoae.common.no");
            int statusWidth = font.width(label) + font.width(value);
            int titleWidth = Math.max(0, width - statusWidth - 4);
            String title = font.plainSubstrByWidth(config.title().get().getString(), titleWidth);
            if (font.width(config.title().get()) > titleWidth && titleWidth > font.width("...")) {
                title = font.plainSubstrByWidth(config.title().get().getString(), titleWidth - font.width("...")) + "...";
            }
            guiContext.graphics.drawString(font, title, x, y, ROOT_TEXT, false);
            int statusX = x + Math.min(font.width(title), titleWidth) + 4;
            guiContext.graphics.drawString(font, label, statusX, y, HEADER_LABEL, false);
            guiContext.graphics.drawString(font, value, statusX + font.width(label), y, syncedFormed ? StorageHostText.USED : PANEL_WARNING, false);
        }
    }

    private static final class StatusLight extends UIElement implements IBindable<Boolean> {
        private boolean syncedValue;

        private StatusLight(BooleanSupplier value) {
            this.syncedValue = value.getAsBoolean();
            bind(DataBindingBuilder.boolS2C(value::getAsBoolean).build());
        }

        @Override
        public IDataSource<Boolean> setValue(@Nullable Boolean value) {
            syncedValue = Boolean.TRUE.equals(value);
            return this;
        }

        @Override
        public Boolean getValue() {
            return syncedValue;
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            float x = getPositionX();
            float y = getPositionY();
            guiContext.graphics.fill((int)x, (int)y, (int)x + 13, (int)y + 13, PANEL_EDGE);
            guiContext.graphics.fill((int)x + 1, (int)y + 1, (int)x + 12, (int)y + 12, PANEL_BORDER);
            guiContext.graphics.fill((int)x + 2, (int)y + 2, (int)x + 11, (int)y + 11, syncedValue ? PANEL_SUCCESS : PANEL_WARNING);
        }
    }

    private static final class SyncedToolbarButton extends Button implements IBindable<Component> {
        private Component syncedTooltip;

        private SyncedToolbarButton(Runnable action, Icon icon, Supplier<Component> tooltip) {
            syncedTooltip = tooltip.get();
            noText();
            addChild(new UIElement()
                .layout(layout -> {
                    layout.positionType(TaffyPosition.ABSOLUTE);
                    layout.left(0);
                    layout.top(0);
                    layout.width(16);
                    layout.height(16);
                })
                .style(style -> style.backgroundTexture(AETextures.icon(icon))));
            setOnServerClick(event -> action.run());
            bind(DataBindingBuilder.componentS2C(tooltip).build());
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips =
                HoverTooltips.empty().append(syncedTooltip));
        }

        @Override
        public IDataSource<Component> setValue(@Nullable Component value) {
            syncedTooltip = value == null ? Component.empty() : value;
            return this;
        }

        @Override
        public Component getValue() {
            return syncedTooltip;
        }
    }

    private static final class PerformanceLabelElement extends UIElement implements IBindable<Long> {
        private long syncedAverageNanos;

        private PerformanceLabelElement(LongSupplier performanceAverageNanos) {
            this.syncedAverageNanos = Math.max(0L, performanceAverageNanos.getAsLong());
            bind(DataBindingBuilder.longValS2C(() -> Math.max(0L, performanceAverageNanos.getAsLong())).build());
            addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                event.hoverTooltips = HoverTooltips.empty().append(
                    Component.translatable("gui.neoecoae.crafting.performance"),
                    Component.literal(formatPerformanceValue(syncedAverageNanos))
                ));
        }

        @Override
        public IDataSource<Long> setValue(@Nullable Long value) {
            syncedAverageNanos = value == null ? 0L : Math.max(0L, value);
            return this;
        }

        @Override
        public Long getValue() {
            return syncedAverageNanos;
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            Font font = Minecraft.getInstance().font;
            String text = formatPerformanceCornerValue(syncedAverageNanos);
            float scale = COMPACT_FONT_SIZE / 9.0F;
            int x = (int)getPositionX();
            int y = (int)getPositionY();
            int width = (int)getSizeWidth();
            guiContext.graphics.pose().pushPose();
            guiContext.graphics.pose().translate(x, y, 0.0F);
            guiContext.graphics.pose().scale(scale, scale, 1.0F);
            int scaledWidth = Math.round(width / scale);
            guiContext.graphics.drawString(font, text, scaledWidth - font.width(text), 0, PANEL_VALUE, false);
            guiContext.graphics.pose().popPose();
        }
    }

    private static final class EnergyGaugeElement extends UIElement implements IBindable<Long> {
        private long syncedEnergyUsage;

        private EnergyGaugeElement(LongSupplier energyUsage) {
            this.syncedEnergyUsage = Math.max(0L, energyUsage.getAsLong());
            bind(DataBindingBuilder.longValS2C(() -> Math.max(0L, energyUsage.getAsLong())).build());
            addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                event.hoverTooltips = HoverTooltips.empty().append(
                    Component.translatable("gui.neoecoae.crafting.ui.energy_usage"),
                    Tooltips.ofNumber(syncedEnergyUsage).append(" AE/t")
                ));
        }

        @Override
        public IDataSource<Long> setValue(@Nullable Long value) {
            syncedEnergyUsage = value == null ? 0L : Math.max(0L, value);
            return this;
        }

        @Override
        public Long getValue() {
            return syncedEnergyUsage;
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            drawGaugeFrame(guiContext, this);
            float ratio = ratio(syncedEnergyUsage, ENERGY_GAUGE_REFERENCE);
            drawGaugeFill(guiContext, this, ratio, energyGaugeColor(ratio), null);
        }
    }

    private static final class CoolantGaugeElement extends UIElement implements IBindable<CompoundTag> {
        private static final String NBT_AMOUNT = "amount";
        private static final String NBT_CAPACITY = "capacity";
        private static final String NBT_MAX_OVERCLOCK = "maxOverclock";
        private static final String NBT_FLUID = "fluid";

        private final Supplier<HolderLookup.Provider> registries;
        private int syncedAmount;
        private int syncedCapacity;
        private int syncedMaxOverclock;
        private CompoundTag syncedPayload = new CompoundTag();
        private FluidStack syncedFluid = FluidStack.EMPTY;

        private CoolantGaugeElement(
            IntSupplier amount,
            IntSupplier capacity,
            IntSupplier maxOverclock,
            Supplier<FluidStack> fluid,
            Supplier<HolderLookup.Provider> registries
        ) {
            this.registries = registries;
            applyPayload(createPayload(amount, capacity, maxOverclock, fluid, registries.get()));
            bind(DataBindingBuilder.create(
                () -> createPayload(amount, capacity, maxOverclock, fluid, registries.get()),
                ignored -> {
                }).syncType(CompoundTag.class).c2sStrategy(com.lowdragmc.lowdraglib2.gui.sync.bindings.SyncStrategy.NONE).build());
            addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                event.hoverTooltips = HoverTooltips.empty().append(tooltip().toArray(Component[]::new)));
        }

        @Override
        public IDataSource<CompoundTag> setValue(@Nullable CompoundTag value) {
            applyPayload(value);
            return this;
        }

        @Override
        public CompoundTag getValue() {
            return syncedPayload.copy();
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            drawGaugeFrame(guiContext, this);
            FluidStack fluid = syncedAmount > 0 ? syncedFluid.copyWithAmount(Math.max(1, syncedAmount)) : FluidStack.EMPTY;
            drawGaugeFill(guiContext, this, ratio(syncedAmount, syncedCapacity), 0xFF26A6BD, fluid);
        }

        private List<Component> tooltip() {
            return List.of(
                Component.translatable("gui.neoecoae.host.crafting.coolant"),
                Component.literal(StorageHostText.typeProgress(syncedAmount, syncedCapacity).usedText() + " / "
                    + StorageHostText.typeProgress(syncedAmount, syncedCapacity).maxText() + " mB"),
                Component.translatable(
                    "gui.neoecoae.crafting.coolant_max_overclock",
                    syncedMaxOverclock < 0 ? "-" : Tooltips.ofNumber(syncedMaxOverclock))
            );
        }

        private void applyPayload(@Nullable CompoundTag tag) {
            if (tag == null) {
                syncedAmount = 0;
                syncedCapacity = 0;
                syncedMaxOverclock = -1;
                syncedFluid = FluidStack.EMPTY;
                syncedPayload = new CompoundTag();
                return;
            }
            syncedPayload = tag.copy();
            syncedAmount = Math.max(0, tag.getInt(NBT_AMOUNT));
            syncedCapacity = Math.max(0, tag.getInt(NBT_CAPACITY));
            syncedMaxOverclock = tag.getInt(NBT_MAX_OVERCLOCK);
            syncedFluid = copyFluid(FluidStack.parseOptional(registries.get(), tag.getCompound(NBT_FLUID)));
        }

        private static CompoundTag createPayload(
            IntSupplier amount,
            IntSupplier capacity,
            IntSupplier maxOverclock,
            Supplier<FluidStack> fluid,
            HolderLookup.Provider registries
        ) {
            CompoundTag tag = new CompoundTag();
            tag.putInt(NBT_AMOUNT, Math.max(0, amount.getAsInt()));
            tag.putInt(NBT_CAPACITY, Math.max(0, capacity.getAsInt()));
            tag.putInt(NBT_MAX_OVERCLOCK, maxOverclock.getAsInt());
            tag.put(NBT_FLUID, copyFluid(fluid.get()).saveOptional(registries));
            return tag;
        }

        private static FluidStack copyFluid(@Nullable FluidStack fluid) {
            return fluid == null || fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(1);
        }
    }

    private static void drawGaugeFrame(GUIContext guiContext, UIElement element) {
        int x = (int)element.getPositionX();
        int y = (int)element.getPositionY();
        int w = (int)element.getSizeWidth();
        int h = (int)element.getSizeHeight();
        guiContext.graphics.fill(x, y, x + w, y + h, 0x661F2F34);
        guiContext.graphics.fill(x + 2, y + 2, x + Math.max(3, w - 2), y + Math.max(3, h - 2), 0x881F2F34);
    }

    private static void drawGaugeFill(GUIContext guiContext, UIElement element, float ratio, int color, @Nullable FluidStack fluid) {
        int x = (int)element.getPositionX();
        int y = (int)element.getPositionY();
        int w = (int)element.getSizeWidth();
        int h = (int)element.getSizeHeight();
        int ix = x + 2;
        int iy = y + 2;
        int iw = Math.max(1, w - 4);
        int ih = Math.max(1, h - 4);
        float clampedRatio = Mth.clamp(ratio, 0.0F, 1.0F);
        int fillH = Math.round(clampedRatio * ih);
        if (clampedRatio > 0.0F && fillH == 0) {
            fillH = 1;
        }
        if (fillH > 0) {
            int fillY = iy + ih - fillH;
            if (fluid != null && !fluid.isEmpty()) {
                new FluidStackTexture(fluid).draw(guiContext, ix, fillY, iw, fillH);
            } else {
                guiContext.graphics.fill(ix, fillY, ix + iw, iy + ih, color);
            }
            guiContext.graphics.fill(ix, fillY, ix + iw, Math.min(fillY + 2, iy + ih), 0x70FFFFFF);
        }
    }

    private static List<Component> craftingTooltip(ComputationTaskEntry entry) {
        return List.of(
            entry.output().getHoverName(),
            Component.translatable("gui.neoecoae.crafting.ui.recipe_slots")
                .append(": ")
                .append(Tooltips.ofNumber(entry.craftCount())),
            Component.translatable(ComputationTaskCards.statusKey(entry.status()))
                .append(" ")
                .append(Component.literal(ComputationTaskCards.progressText(entry)))
        );
    }

    private static void drawCraftingTaskCard(GUIContext guiContext, Font font, ComputationTaskEntry entry, int x, int y) {
        int accent = ComputationTaskCards.statusColor(entry.status());
        guiContext.graphics.fill(x, y, x + TASK_CARD_W, y + TASK_CARD_H, 0xFFD8D3E4);
        guiContext.graphics.fill(x + 1, y + 1, x + TASK_CARD_W - 1, y + TASK_CARD_H - 1, 0xFF121016);
        guiContext.graphics.fill(x + 2, y + 2, x + TASK_CARD_W - 2, y + TASK_CARD_H - 2, 0xFF2C2735);
        guiContext.graphics.fill(x + 2, y + TASK_CARD_H - 2, x + TASK_CARD_W - 2, y + TASK_CARD_H - 1, accent);
        if (!entry.output().isEmpty()) {
            DrawerHelper.drawItemStack(guiContext.graphics, entry.output(), x + 3, y, -1, null);
        }
        String name = entry.output().getHoverName().getString();
        if (font.width(name) > TASK_CARD_W - 48) {
            name = font.plainSubstrByWidth(name, TASK_CARD_W - 58) + "...";
        }
        HostTaskListElement.drawString(guiContext, font, name, x + 22 + PANEL_TEXT_SHIFT_X, y + 3, StorageHostText.PRIMARY);
        HostTaskListElement.drawRightString(
            guiContext,
            font,
            "x" + ComputationTaskCards.compactAmount(entry.outputAmount()),
            x + TASK_CARD_W - 4 + PANEL_TEXT_SHIFT_X,
            y + 3,
            StorageHostText.VALUE
        );
    }
}
