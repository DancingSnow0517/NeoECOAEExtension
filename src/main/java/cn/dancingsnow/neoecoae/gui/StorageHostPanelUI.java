package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostWidgets;
import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class StorageHostPanelUI {
    public static final int LEFT_PANEL_WIDTH = 172;
    public static final int RIGHT_PANEL_WIDTH = 156;
    public static final int PANEL_HEIGHT = 200;

    private static final int PANEL_PADDING = 2;
    private static final int PANEL_GAP = 2;
    private static final int LEFT_STORAGE_PANEL_HEIGHT_WITH_INVENTORY = 108;
    private static final int LEFT_INVENTORY_HEIGHT = 88;
    private static final int INVENTORY_SLOT_GRID_WIDTH = 9 * 18;
    private static final int TEXT_MAX_WIDTH = LEFT_PANEL_WIDTH - 16;
    private static final int RIGHT_SCROLLER_RESERVED_WIDTH = 12;
    private static final int RIGHT_SCROLLER_RESERVED_HEIGHT = 15;
    private static final int RIGHT_CONTENT_WIDTH = RIGHT_PANEL_WIDTH - RIGHT_SCROLLER_RESERVED_WIDTH;
    private static final int RIGHT_CONTENT_HEIGHT = PANEL_HEIGHT - RIGHT_SCROLLER_RESERVED_HEIGHT;
    private static final int RIGHT_TITLE_Y = 2;
    private static final int RIGHT_TITLE_HEIGHT = 10;
    private static final int RIGHT_INSET_X = 2;
    private static final int RIGHT_INSET_TOP_GAP = 2;
    private static final int RIGHT_INSET_Y = RIGHT_TITLE_Y + RIGHT_TITLE_HEIGHT + RIGHT_INSET_TOP_GAP;
    private static final int RIGHT_INSET_RIGHT_SPACE = 1;
    private static final int RIGHT_INSET_BOTTOM_SPACE = 2;
    private static final int RIGHT_INSET_WIDTH = RIGHT_CONTENT_WIDTH - RIGHT_INSET_X - RIGHT_INSET_RIGHT_SPACE;
    private static final int RIGHT_INSET_HEIGHT = RIGHT_CONTENT_HEIGHT - RIGHT_INSET_Y - RIGHT_INSET_BOTTOM_SPACE;
    private static final int RIGHT_GAUGE_X = RIGHT_INSET_X + 8;
    private static final int RIGHT_GAUGE_Y = RIGHT_INSET_Y + 12;
    private static final int RIGHT_GAUGE_WIDTH = 32;
    private static final int RIGHT_GAUGE_HEIGHT = RIGHT_INSET_HEIGHT - 26;
    private static final int RIGHT_DETAIL_X = RIGHT_GAUGE_X + RIGHT_GAUGE_WIDTH + 8;
    private static final int RIGHT_DETAIL_Y = RIGHT_GAUGE_Y + 5;
    private static final int RIGHT_DETAIL_LINE_HEIGHT = 15;
    private static final int RIGHT_DETAIL_WIDTH = RIGHT_CONTENT_WIDTH - RIGHT_DETAIL_X - 6;
    private static final int RIGHT_PERCENT_Y = RIGHT_INSET_Y + RIGHT_INSET_HEIGHT - 12;
    private static final float RIGHT_DETAIL_FONT_SIZE = 8.0F;
    private static final float RIGHT_PERCENT_TEXT_SCALE = 0.9F;
    private static final int RIGHT_COMPONENT_SLOT_SIZE = 18;
    private static final int RIGHT_COMPONENT_SLOT_X = RIGHT_INSET_X + RIGHT_INSET_WIDTH - RIGHT_COMPONENT_SLOT_SIZE - 5;
    private static final int RIGHT_COMPONENT_SLOT_Y = RIGHT_INSET_Y + RIGHT_INSET_HEIGHT - RIGHT_COMPONENT_SLOT_SIZE - 5;
    private static final int SCROLLBAR_HORIZONTAL_OFFSET = 2;
    private static final int PROGRESS_ROW_LABEL_WIDTH = 24;
    private static final int PROGRESS_ROW_BAR_WIDTH = 36;

    private StorageHostPanelUI() {
    }

    public record StorageTypeLine(
        ECOCellType type,
        int registryIndex,
        LongSupplier usedTypes,
        LongSupplier totalTypes,
        LongSupplier usedBytes,
        LongSupplier totalBytes
    ) {
    }

    public record Config(
        LongSupplier storedEnergy,
        LongSupplier maxEnergy,
        LongSupplier maxLoadUsedBytes,
        LongSupplier maxLoadTotalBytes,
        IntSupplier idleMatrices,
        List<StorageTypeLine> storageTypes,
        BooleanSupplier showComponentSlots,
        IItemHandlerModifiable componentInventory
    ) {
    }

    private record StorageTotals(long usedBytes, long totalBytes) {
    }

    public static UIElement createLeftPanel(Config config) {
        if (!config.showComponentSlots().getAsBoolean()) {
            return createLeftStoragePanel(config, PANEL_HEIGHT);
        }

        UIElement panel = new UIElement().layout(layout -> {
            layout.width(LEFT_PANEL_WIDTH);
            layout.height(PANEL_HEIGHT);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(4);
        });
        panel.addChild(createLeftStoragePanel(config, LEFT_STORAGE_PANEL_HEIGHT_WITH_INVENTORY));
        panel.addChild(createInventoryPanel());
        return panel;
    }

    private static ScrollerView createLeftStoragePanel(Config config, int height) {
        ScrollerView panel = createPanel(LEFT_PANEL_WIDTH, height);
        addLeftStorageContent(panel, config);
        return panel;
    }

    private static void addLeftStorageContent(ScrollerView panel, Config config) {
        panel.addScrollViewChild(StorageHostElements.sectionLabel(
            () -> Component.translatable("gui.neoecoae.storage.energy"),
            () -> StorageHostText.PRIMARY
        ));
        panel.addScrollViewChild(usedTotalRow(
            () -> Component.translatable("gui.neoecoae.storage.energy_storage").append(": "),
            () -> StorageHostText.energyUsage(config.storedEnergy().getAsLong(), config.maxEnergy().getAsLong(), TEXT_MAX_WIDTH),
            config.storedEnergy(),
            config.maxEnergy()
        ));
        config.storageTypes().forEach(line -> panel.addScrollViewChild(storageTypeBlock(line)));
    }

    public static ScrollerView createRightPanel(Config config) {
        ScrollerView panel = createEmptyPanel(RIGHT_PANEL_WIDTH);
        StorageHostAnimatedRatio loadRatio = new StorageHostAnimatedRatio();
        panel.scrollerStyle(style -> style
            .verticalScrollDisplay(ScrollDisplay.NEVER)
            .horizontalScrollDisplay(ScrollDisplay.NEVER));
        panel.viewContainer(view -> {
            view.getLayout().paddingAll(0);
            view.addChild(StorageHostElements.absolute(
                StorageHostElements.panelTitle(() -> Component.translatable("gui.neoecoae.storage.system_load")),
                0,
                RIGHT_TITLE_Y,
                RIGHT_CONTENT_WIDTH,
                RIGHT_TITLE_HEIGHT
            ));
            view.addChild(StorageHostElements.absolute(
                StorageHostElements.tinyInsetPanel(RIGHT_INSET_WIDTH, RIGHT_INSET_HEIGHT),
                RIGHT_INSET_X,
                RIGHT_INSET_Y,
                RIGHT_INSET_WIDTH,
                RIGHT_INSET_HEIGHT
            ));
            view.addChild(StorageHostElements.absolute(
                StorageHostLoadGauge.bindRatio(
                    () -> {
                        StorageTotals totals = storageTotals(config);
                        return StorageHostText.usageRatio(totals.usedBytes(), totals.totalBytes());
                    },
                    loadRatio
                ),
                RIGHT_GAUGE_X,
                RIGHT_GAUGE_Y,
                RIGHT_GAUGE_WIDTH,
                RIGHT_GAUGE_HEIGHT
            ));
            view.addChild(StorageHostElements.absolute(
                storageLoadLine(
                    () -> Component.translatable("gui.neoecoae.storage.current_load")
                        .append(": ")
                        .append(currentLoadPercent(config)),
                    () -> StorageHostText.PRIMARY
                ),
                RIGHT_DETAIL_X,
                RIGHT_DETAIL_Y,
                RIGHT_DETAIL_WIDTH,
                RIGHT_DETAIL_LINE_HEIGHT
            ));
            view.addChild(StorageHostElements.absolute(
                storageLoadLine(
                    () -> Component.translatable("gui.neoecoae.storage.max_load")
                        .append(": ")
                        .append(StorageHostText.percent(config.maxLoadUsedBytes().getAsLong(), config.maxLoadTotalBytes().getAsLong())),
                    () -> StorageHostText.WARNING
                ),
                RIGHT_DETAIL_X,
                RIGHT_DETAIL_Y + RIGHT_DETAIL_LINE_HEIGHT,
                RIGHT_DETAIL_WIDTH,
                RIGHT_DETAIL_LINE_HEIGHT
            ));
            view.addChild(StorageHostElements.absolute(
                storageLoadLine(
                    () -> Component.translatable("gui.neoecoae.storage.status")
                        .append(": ")
                        .append(storageStatus(config)),
                    () -> storageStatusColor(config)
                ),
                RIGHT_DETAIL_X,
                RIGHT_DETAIL_Y + RIGHT_DETAIL_LINE_HEIGHT * 2,
                RIGHT_DETAIL_WIDTH,
                RIGHT_DETAIL_LINE_HEIGHT
            ));
            view.addChild(StorageHostElements.absolute(
                storageLoadLine(
                    () -> Component.translatable("gui.neoecoae.storage.idle_matrices")
                        .append(": ")
                        .append(Integer.toString(config.idleMatrices().getAsInt())),
                    () -> StorageHostText.MUTED
                ),
                RIGHT_DETAIL_X,
                RIGHT_DETAIL_Y + RIGHT_DETAIL_LINE_HEIGHT * 3,
                RIGHT_DETAIL_WIDTH,
                RIGHT_DETAIL_LINE_HEIGHT
            ));
            view.addChild(StorageHostElements.absolute(
                StorageHostAnimatedPercentLabel.centered(
                    loadRatio,
                    () -> StorageHostText.gaugeTextColor((float)loadRatio.value()),
                    RIGHT_PERCENT_TEXT_SCALE
                ),
                RIGHT_GAUGE_X,
                RIGHT_PERCENT_Y,
                RIGHT_GAUGE_WIDTH,
                8
            ));
            view.addChild(StorageHostElements.absolute(
                componentSlot(config.showComponentSlots(), config.componentInventory()),
                RIGHT_COMPONENT_SLOT_X,
                RIGHT_COMPONENT_SLOT_Y,
                RIGHT_COMPONENT_SLOT_SIZE,
                RIGHT_COMPONENT_SLOT_SIZE
            ));
        });
        return panel;
    }

    public static ScrollerView createEmptyPanel(int width) {
        return createPanel(width, PANEL_HEIGHT);
    }

    private static ScrollerView createPanel(int width, int height) {
        return ECOHostWidgets.storagePanel(width, height, PANEL_PADDING, PANEL_GAP, SCROLLBAR_HORIZONTAL_OFFSET);
    }

    private static UIElement createInventoryPanel() {
        UIElement panel = StorageHostElements.syncedDisplay(() -> true);
        panel.layout(layout -> {
            layout.widthPercent(100);
            layout.height(LEFT_INVENTORY_HEIGHT);
            layout.flexDirection(FlexDirection.COLUMN);
        });
        panel.addChild(new TextElement()
            .setText("container.inventory", true)
            .textStyle(StorageHostPanelUI::inventoryTitleTextStyle));
        InventorySlots inventorySlots = new InventorySlots();
        inventorySlots.layout(layout -> {
            layout.width(INVENTORY_SLOT_GRID_WIDTH);
            layout.marginTop(2);
        });
        inventorySlots.getChildren().forEach(child -> child.layout(layout -> layout.width(INVENTORY_SLOT_GRID_WIDTH)));
        panel.addChild(inventorySlots);
        return panel;
    }

    private static UIElement componentSlot(BooleanSupplier display, IItemHandlerModifiable componentInventory) {
        UIElement wrapper = StorageHostElements.syncedDisplay(display);
        wrapper.addChild(new ItemSlot(new ItemHandlerSlot(componentInventory, 0)));
        return wrapper;
    }

    private static void inventoryTitleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true)
            .adaptiveWidth(true)
            .textWrap(TextWrap.HOVER_ROLL)
            .textColor(0x3f3d52)
            .textShadow(false);
    }

    private static UIElement storageTypeBlock(StorageTypeLine line) {
        UIElement block = StorageHostElements.syncedDisplay(() -> shouldShowStorageType(line));
        block.layout(layout -> {
            layout.gapAll(2);
            layout.flexDirection(FlexDirection.COLUMN);
        });
        block.addChild(StorageHostElements.sectionLabel(
            () -> line.type().desc(),
            () -> StorageHostText.storageTypeAccentColor(line.type(), line.registryIndex())
        ));
        block.addChild(usageProgressRow(
            () -> Component.translatable("gui.neoecoae.host.metric.types"),
            () -> StorageHostText.typeProgress(line.usedTypes().getAsLong(), line.totalTypes().getAsLong()),
            () -> usedTotalTooltip(
                StorageHostText.fullTypeProgress(line.usedTypes().getAsLong(), line.totalTypes().getAsLong()),
                line.usedTypes().getAsLong(),
                line.totalTypes().getAsLong()
            ),
            line.usedTypes(),
            line.totalTypes()
        ));
        block.addChild(usageProgressRow(
            () -> Component.translatable("gui.neoecoae.host.metric.bytes"),
            () -> StorageHostText.byteProgress(line.usedBytes().getAsLong(), line.totalBytes().getAsLong()),
            () -> usedTotalTooltip(
                StorageHostText.fullByteProgressValues(line.usedBytes().getAsLong(), line.totalBytes().getAsLong()),
                line.usedBytes().getAsLong(),
                line.totalBytes().getAsLong()
            ),
            line.usedBytes(),
            line.totalBytes()
        ));
        return block;
    }

    private static UIElement usedTotalRow(
        Supplier<Component> prefix,
        Supplier<StorageHostText.UsedTotal> text,
        LongSupplier used,
        LongSupplier max
    ) {
        UIElement row = StorageHostElements.horizontalRow(10, 0);
        row.addChild(StorageHostElements.textSegment(prefix, () -> StorageHostText.MUTED));
        row.addChild(StorageHostElements.textSegment(
            () -> Component.literal(text.get().usedText()),
            () -> StorageHostText.usedValueColor(used.getAsLong(), max.getAsLong())
        ));
        row.addChild(StorageHostElements.textSegment(() -> Component.literal(" / "), () -> StorageHostText.MUTED));
        row.addChild(StorageHostElements.textSegment(() -> Component.literal(text.get().maxText()), () -> StorageHostText.VALUE));
        row.addChild(StorageHostElements.textSegment(() -> Component.literal(" ").append(text.get().suffix()), () -> StorageHostText.MUTED));
        return row;
    }

    private static UIElement usageProgressRow(
        Supplier<Component> label,
        Supplier<StorageHostText.UsedTotal> text,
        Supplier<Component> tooltip,
        LongSupplier used,
        LongSupplier max
    ) {
        UIElement row = StorageHostElements.horizontalRow(10, 2);
        row.addChild(StorageHostElements.textSegment(label, () -> StorageHostText.MUTED)
            .layout(layout -> layout.width(PROGRESS_ROW_LABEL_WIDTH)));
        row.addChild(new TooltipProgressBarElement(used, max, tooltip)
            .layout(layout -> layout.width(PROGRESS_ROW_BAR_WIDTH).height(4)));

        UIElement value = StorageHostElements.horizontalRow(10, 0);
        value.addChild(StorageHostElements.textSegment(
            () -> Component.literal(text.get().usedText()),
            () -> StorageHostText.usedValueColor(used.getAsLong(), max.getAsLong())
        ));
        value.addChild(StorageHostElements.textSegment(() -> Component.literal(" / "), () -> StorageHostText.MUTED));
        value.addChild(StorageHostElements.textSegment(() -> Component.literal(text.get().maxText()), () -> StorageHostText.VALUE));
        row.addChild(value);
        return row;
    }

    private static ProgressBar syncedProgressBar(LongSupplier used, LongSupplier max) {
        ProgressBar progressBar = new ProgressBar();
        progressBar
            .label(progressLabel -> progressLabel.setText(""))
            .barContainer(element -> element.layout(layout -> layout.paddingAll(1)))
            .bind(DataBindingBuilder.floatValS2C(() -> StorageHostText.usageRatio(used.getAsLong(), max.getAsLong())).build());
        progressBar.addClass("eco-host-progress");
        return progressBar;
    }

    private static Component usedTotalTooltip(StorageHostText.UsedTotal text, long used, long max) {
        MutableComponent line = Component.literal(text.usedText()).withColor(StorageHostText.usedValueColor(used, max))
            .append(Component.literal(" / ").withColor(StorageHostText.MUTED))
            .append(Component.literal(text.maxText()).withColor(StorageHostText.VALUE));
        if (!Component.empty().equals(text.suffix())) {
            line.append(Component.literal(" ").append(text.suffix()).withColor(StorageHostText.MUTED));
        }
        return line;
    }

    private static UIElement storageLoadLine(Supplier<Component> text, java.util.function.IntSupplier color) {
        Label label = new Label();
        label.bind(DataBindingBuilder.componentS2C(() -> text.get().copy().withColor(color.getAsInt())).build());
        label.textStyle(StorageHostPanelUI::storageLoadTextStyle);
        return label;
    }

    private static void storageLoadTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true)
            .adaptiveWidth(true)
            .fontSize(RIGHT_DETAIL_FONT_SIZE)
            .textWrap(TextWrap.HOVER_ROLL)
            .textShadow(false);
    }

    private static Component storageStatus(Config config) {
        StorageTypeLine line = highestPressureLine(config);
        if (line == null) {
            return Component.translatable("gui.neoecoae.storage.status.stable");
        }
        long used = line.usedBytes().getAsLong();
        long total = line.totalBytes().getAsLong();
        float ratio = StorageHostText.usageRatio(used, total);
        if (total > 0L && ratio >= 1.0F) {
            return Component.translatable("gui.neoecoae.storage.status.full", line.type().desc());
        }
        if (ratio >= 0.9F) {
            return Component.translatable("gui.neoecoae.storage.status.high", line.type().desc());
        }
        if (ratio >= 0.75F) {
            return Component.translatable("gui.neoecoae.storage.status.warning", line.type().desc());
        }
        return Component.translatable("gui.neoecoae.storage.status.stable");
    }

    private static int storageStatusColor(Config config) {
        StorageTypeLine line = highestPressureLine(config);
        if (line == null) {
            return StorageHostText.MUTED;
        }
        return StorageHostText.usedValueColor(line.usedBytes().getAsLong(), line.totalBytes().getAsLong());
    }

    private static StorageTypeLine highestPressureLine(Config config) {
        StorageTypeLine best = null;
        float bestRatio = -1.0F;
        for (StorageTypeLine line : config.storageTypes()) {
            long total = line.totalBytes().getAsLong();
            if (total <= 0L) {
                continue;
            }
            float ratio = StorageHostText.usageRatio(line.usedBytes().getAsLong(), total);
            if (ratio > bestRatio) {
                bestRatio = ratio;
                best = line;
            }
        }
        return best;
    }

    private static String currentLoadPercent(Config config) {
        StorageTotals totals = storageTotals(config);
        return StorageHostText.percent(totals.usedBytes(), totals.totalBytes());
    }

    private static StorageTotals storageTotals(Config config) {
        long used = 0L;
        long total = 0L;
        for (StorageTypeLine line : config.storageTypes()) {
            used = saturatedAdd(used, line.usedBytes().getAsLong());
            total = saturatedAdd(total, line.totalBytes().getAsLong());
        }
        return new StorageTotals(used, total);
    }

    private static long saturatedAdd(long left, long right) {
        long safeRight = Math.max(0L, right);
        long result = left + safeRight;
        return result < 0L ? Long.MAX_VALUE : result;
    }

    private static boolean shouldShowStorageType(StorageTypeLine line) {
        return line.totalBytes().getAsLong() > 0 || line.totalTypes().getAsLong() > 0;
    }

    private static final class TooltipProgressBarElement extends UIElement implements IBindable<Component> {
        private Component tooltip;

        private TooltipProgressBarElement(
            LongSupplier used,
            LongSupplier max,
            Supplier<Component> tooltip
        ) {
            this.tooltip = tooltip.get();
            bind(DataBindingBuilder.componentS2C(tooltip).build());
            addChild(syncedProgressBar(used, max)
                .layout(layout -> layout.widthPercent(100).height(4)));
            addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                event.hoverTooltips = HoverTooltips.empty().append(this.tooltip));
        }

        @Override
        public IDataSource<Component> setValue(@Nullable Component value) {
            tooltip = value == null ? Component.empty() : value;
            return this;
        }

        @Override
        public Component getValue() {
            return tooltip;
        }
    }
}
