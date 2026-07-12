package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.client.gui.Icon;
import appeng.core.localization.ButtonToolTips;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskCards;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskEntry;
import cn.dancingsnow.neoecoae.gui.task.HostTaskListElement;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostElements;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostText;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IBindable;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataSource;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostWidgets;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.gui.Font;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class ComputationHostPanelUI {
    public static final int LEFT_PANEL_WIDTH = 162;
    public static final int RIGHT_PANEL_WIDTH = 156;
    public static final int PANEL_HEIGHT = 200;

    private static final int CPU_MODE_BUTTON_SIZE = 18;
    private static final int LEFT_CAPACITY_HEIGHT = 108;
    private static final int LEFT_INVENTORY_HEIGHT = 88;
    private static final int PANEL_PADDING = 2;
    private static final int PANEL_GAP = 2;
    private static final int SCROLLBAR_HORIZONTAL_OFFSET = 2;
    private static final int PROGRESS_ROW_LABEL_WIDTH = 54;
    private static final int PROGRESS_ROW_BAR_WIDTH = 36;
    private static final int RIGHT_TASK_PANEL_X = 0;
    private static final int RIGHT_TASK_PANEL_Y = 0;
    private static final int RIGHT_TASK_PANEL_WIDTH = RIGHT_PANEL_WIDTH - 12;
    private static final int RIGHT_TASK_PANEL_HEIGHT = PANEL_HEIGHT - 15;
    private static final int TASK_CARD_X = RIGHT_TASK_PANEL_X + 6;
    private static final int TASK_CARD_Y = RIGHT_TASK_PANEL_Y + 19;
    private static final int TASK_CARD_WIDTH = RIGHT_TASK_PANEL_WIDTH - 12;
    private static final int TASK_CARD_HEIGHT = 28;
    private static final int TASK_CARD_STRIDE = 30;
    private static final int TASK_LIST_BOTTOM_Y = RIGHT_TASK_PANEL_Y + RIGHT_TASK_PANEL_HEIGHT - 3;
    private static final int TASK_SCROLLBAR_WIDTH = 3;

    private ComputationHostPanelUI() {
    }

    public record Config(
        LongSupplier usedBytes,
        LongSupplier totalBytes,
        LongSupplier availableBytes,
        IntSupplier usedThreads,
        IntSupplier totalThreads,
        IntSupplier parallelCount,
        Supplier<CpuSelectionMode> cpuSelectionMode,
        Runnable cycleCpuSelectionMode,
        Supplier<HolderLookup.Provider> registries,
        Supplier<List<ComputationTaskEntry>> tasks
    ) {
    }

    public static ScrollerView createLeftCapacityPanel(Config config) {
        ScrollerView panel = createPanel(LEFT_PANEL_WIDTH, LEFT_CAPACITY_HEIGHT);
        panel.addScrollViewChild(StorageHostElements.sectionLabel(
            () -> Component.translatable("gui.neoecoae.host.computation.capacity"),
            () -> StorageHostText.PRIMARY
        ));
        panel.addScrollViewChild(usageProgressRow(
            () -> Component.translatable("gui.neoecoae.host.computation.cpu_storage"),
            () -> StorageHostText.byteProgress(config.usedBytes().getAsLong(), config.totalBytes().getAsLong()),
            config.usedBytes(),
            config.totalBytes(),
            () -> Component.translatable("gui.neoecoae.host.computation.cpu_storage")
                .append(": ")
                .append(StorageHostText.fullByteProgress(config.usedBytes().getAsLong(), config.totalBytes().getAsLong()))
        ));
        panel.addScrollViewChild(usageProgressRow(
            () -> Component.translatable("gui.neoecoae.host.computation.thread_usage"),
            () -> StorageHostText.typeProgress(config.usedThreads().getAsInt(), config.totalThreads().getAsInt()),
            () -> config.usedThreads().getAsInt(),
            () -> config.totalThreads().getAsInt()
        ));
        panel.addScrollViewChild(valueRow(
            () -> Component.translatable("gui.neoecoae.host.computation.parallel_count"),
            () -> Component.literal(Integer.toString(config.parallelCount().getAsInt())),
            () -> StorageHostText.VALUE
        ));
        panel.addScrollViewChild(valueRow(
            () -> Component.translatable("gui.neoecoae.host.computation.free_memory"),
            () -> Component.literal(StorageHostText.byteProgress(config.availableBytes().getAsLong(), 0).usedText()),
            () -> StorageHostText.MUTED
        ));
        return panel;
    }

    public static Button createCpuSelectionButton(Config config) {
        Button button = new CpuSelectionModeButton(config);
        button.layout(layout -> layout.width(CPU_MODE_BUTTON_SIZE).height(CPU_MODE_BUTTON_SIZE));
        return button;
    }

    private static UIElement cpuSelectionIcon(CpuSelectionMode mode) {
        return new UIElement()
            .layout(layout -> {
                layout.width(12);
                layout.height(12);
            })
            .style(style -> style.backgroundTexture(cpuSelectionModeIcon(mode)));
    }

    private static com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture cpuSelectionModeIcon(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> AETextures.icon(Icon.CRAFT_HAMMER);
            case PLAYER_ONLY -> AETextures.icon(Icon.S_TERMINAL);
            case MACHINE_ONLY -> AETextures.icon(Icon.S_MACHINE);
        };
    }

    private static Component cpuSelectionModeTooltip(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> ButtonToolTips.CpuSelectionModeAny.text();
            case PLAYER_ONLY -> ButtonToolTips.CpuSelectionModePlayersOnly.text();
            case MACHINE_ONLY -> ButtonToolTips.CpuSelectionModeAutomationOnly.text();
        };
    }

    private static CpuSelectionMode cpuSelectionModeFromOrdinal(Integer ordinal) {
        CpuSelectionMode[] values = CpuSelectionMode.values();
        int index = ordinal == null ? CpuSelectionMode.ANY.ordinal() : ordinal;
        if (index < 0 || index >= values.length) {
            return CpuSelectionMode.ANY;
        }
        return values[index];
    }

    public static UIElement createInventoryPanel() {
        UIElement panel = new UIElement().layout(layout -> {
            layout.width(LEFT_PANEL_WIDTH);
            layout.height(LEFT_INVENTORY_HEIGHT);
            layout.flexDirection(FlexDirection.COLUMN);
        });
        panel.addChild(new TextElement()
            .setText("container.inventory", true)
            .textStyle(ComputationHostPanelUI::inventoryTitleTextStyle));
        panel.addChild(new InventorySlots().layout(layout -> layout.marginTop(2)));
        return panel;
    }

    private static void inventoryTitleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0x3f3d52).textShadow(false);
    }

    public static ScrollerView createRightPanel(Config config) {
        ScrollerView panel = createPanel(RIGHT_PANEL_WIDTH, PANEL_HEIGHT);
        panel.scrollerStyle(style -> style
            .verticalScrollDisplay(ScrollDisplay.NEVER)
            .horizontalScrollDisplay(ScrollDisplay.NEVER));
        panel.viewContainer(view -> {
            view.getLayout().paddingAll(0);
            view.addChild(StorageHostElements.absolute(
                new HostTaskListElement(
                    config.registries(),
                    config.tasks(),
                    RIGHT_TASK_PANEL_WIDTH,
                    RIGHT_TASK_PANEL_HEIGHT,
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
                        return ComputationTaskCards.tooltipLines(entry);
                    }

                    @Override
                    protected void drawTaskCard(GUIContext guiContext, Font font, ComputationTaskEntry entry, float x, float y) {
                        ComputationTaskCards.drawCard(
                            guiContext,
                            font,
                            entry,
                            Math.round(x),
                            Math.round(y),
                            TASK_CARD_WIDTH,
                            TASK_CARD_HEIGHT
                        );
                    }
                },
                RIGHT_TASK_PANEL_X,
                RIGHT_TASK_PANEL_Y,
                RIGHT_TASK_PANEL_WIDTH,
                RIGHT_TASK_PANEL_HEIGHT
            ));
        });
        return panel;
    }

    private static ScrollerView createPanel(int width, int height) {
        return ECOHostWidgets.storagePanel(width, height, PANEL_PADDING, PANEL_GAP, SCROLLBAR_HORIZONTAL_OFFSET);
    }

    private static UIElement usageProgressRow(
        Supplier<Component> label,
        Supplier<StorageHostText.UsedTotal> text,
        LongSupplier used,
        LongSupplier max
    ) {
        return usageProgressRow(label, text, used, max, null);
    }

    private static UIElement usageProgressRow(
        Supplier<Component> label,
        Supplier<StorageHostText.UsedTotal> text,
        LongSupplier used,
        LongSupplier max,
        Supplier<Component> tooltip
    ) {
        UIElement row = StorageHostElements.horizontalRow(10, 2);
        row.addChild(StorageHostElements.textSegment(label, () -> StorageHostText.MUTED)
            .layout(layout -> layout.width(PROGRESS_ROW_LABEL_WIDTH)));
        row.addChild(progressBar(used, max, tooltip));

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

    private static UIElement progressBar(LongSupplier used, LongSupplier max, Supplier<Component> tooltip) {
        if (tooltip != null) {
            return new TooltipProgressBarElement(used, max, tooltip)
                .layout(layout -> layout.width(PROGRESS_ROW_BAR_WIDTH).height(4));
        }
        return syncedProgressBar(used, max)
            .layout(layout -> layout.width(PROGRESS_ROW_BAR_WIDTH).height(4));
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

    private static UIElement valueRow(Supplier<Component> label, Supplier<Component> value, java.util.function.IntSupplier color) {
        UIElement row = StorageHostElements.horizontalRow(10, 2);
        row.addChild(StorageHostElements.textSegment(label, () -> StorageHostText.MUTED)
            .layout(layout -> layout.width(PROGRESS_ROW_LABEL_WIDTH)));
        row.addChild(StorageHostElements.textSegment(value, color));
        return row;
    }

    private static final class TooltipProgressBarElement extends UIElement implements IBindable<Component> {
        private Component tooltip;

        private TooltipProgressBarElement(LongSupplier used, LongSupplier max, Supplier<Component> tooltip) {
            this.tooltip = tooltip.get();
            bind(DataBindingBuilder.componentS2C(tooltip).build());
            addChild(syncedProgressBar(used, max)
                .layout(layout -> layout.widthPercent(100).height(4)));
            addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                event.hoverTooltips = HoverTooltips.empty().append(this.tooltip));
        }

        @Override
        public IDataSource<Component> setValue(Component value) {
            tooltip = value == null ? Component.empty() : value;
            return this;
        }

        @Override
        public Component getValue() {
            return tooltip;
        }
    }

    private static final class CpuSelectionModeButton extends Button implements IBindable<Integer> {
        private final UIElement icon;
        private CpuSelectionMode syncedMode;

        private CpuSelectionModeButton(Config config) {
            syncedMode = config.cpuSelectionMode().get();
            icon = cpuSelectionIcon(syncedMode);
            noText();
            addChild(icon);
            setOnServerClick(event -> config.cycleCpuSelectionMode().run());
            bind(DataBindingBuilder.intValS2C(() -> config.cpuSelectionMode().get().ordinal()).build());
            addEventListener(UIEvents.TICK, event ->
                icon.style(style -> style.backgroundTexture(cpuSelectionModeIcon(syncedMode))));
            addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(
                    ButtonToolTips.CpuSelectionMode.text(),
                    cpuSelectionModeTooltip(syncedMode)
                ),
                null,
                null,
                null
            ));
        }

        @Override
        public IDataSource<Integer> setValue(Integer value) {
            syncedMode = cpuSelectionModeFromOrdinal(value);
            return this;
        }

        @Override
        public Integer getValue() {
            return syncedMode.ordinal();
        }
    }
}
