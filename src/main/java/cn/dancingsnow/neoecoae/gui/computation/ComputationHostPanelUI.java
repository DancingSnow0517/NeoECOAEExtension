package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.client.gui.Icon;
import appeng.core.localization.ButtonToolTips;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskCards;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskEntry;
import cn.dancingsnow.neoecoae.gui.task.HostTaskListElement;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.gui.Font;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
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
    private static final int PROGRESS_ROW_BAR_WIDTH = 70;
    private static final int RIGHT_TASK_PANEL_WIDTH = RIGHT_PANEL_WIDTH - 12;
    private static final int RIGHT_TASK_PANEL_HEIGHT = PANEL_HEIGHT - 15;
    private static final int TASK_CARD_X = 6;
    private static final int TASK_CARD_Y = 19;
    private static final int TASK_CARD_WIDTH = RIGHT_TASK_PANEL_WIDTH - 12;
    private static final int TASK_CARD_HEIGHT = 28;
    private static final int TASK_CARD_STRIDE = 30;
    private static final int TASK_LIST_BOTTOM_Y = RIGHT_TASK_PANEL_HEIGHT - 3;
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
            BooleanSupplier fastPlanningEnabled,
            Runnable toggleFastPlanning,
            IntSupplier networkFrequency,
            IntConsumer adjustNetworkFrequency,
            Supplier<HolderLookup.Provider> registries,
            Supplier<List<ComputationTaskEntry>> tasks) {
    }

    public static UIElement createLeftCapacityPanel(Config config) {
        UIElement panel = HostElements.hostCard(LEFT_PANEL_WIDTH, LEFT_CAPACITY_HEIGHT);
        panel.addClass("eco-computation-capacity");
        panel.addChild(HostElements.localizedTextSegment(
                "gui.neoecoae.host.computation.capacity",
                () -> HostText.PRIMARY));
        panel.addChild(usageProgressBlock(
                "gui.neoecoae.host.computation.cpu_storage",
                () -> HostText.byteProgress(config.usedBytes.getAsLong(), config.totalBytes.getAsLong()),
                config.usedBytes,
                config.totalBytes,
                "gui.neoecoae.host.computation.cpu_storage"));
        panel.addChild(usageProgressBlock(
                "gui.neoecoae.host.computation.thread_usage",
                () -> HostText.typeProgress(config.usedThreads.getAsInt(), config.totalThreads.getAsInt()),
                () -> config.usedThreads.getAsInt(),
                () -> config.totalThreads.getAsInt(),
                null));
        panel.addChild(valueBlock(
                "gui.neoecoae.host.computation.parallel_count",
                () -> Component.literal(Integer.toString(config.parallelCount.getAsInt())),
                () -> HostText.VALUE));
        panel.addChild(valueBlock(
                "gui.neoecoae.host.computation.free_memory",
                () -> Component.literal(HostText.byteProgress(config.availableBytes.getAsLong(), 0).usedText()),
                () -> HostText.MUTED));
        return panel;
    }

    public static Button createCpuSelectionButton(Config config) {
        Button button = new Button().noText();
        button.buttonStyle(style -> style
                .baseTexture(Sprites.RECT_RD)
                .hoverTexture(Sprites.RECT_RD_LIGHT)
                .pressedTexture(Sprites.RECT_RD_DARK));
        button.addClass("eco-host-cpu-mode-button");
        button.layout(layout -> layout.width(CPU_MODE_BUTTON_SIZE).height(CPU_MODE_BUTTON_SIZE));

        UIElement icon = cpuSelectionIcon(config.cpuSelectionMode.get());
        button.addChild(icon);
        button.setOnServerClick(event -> config.cycleCpuSelectionMode.run());

        var syncedMode = HostElements.syncedInt(() -> config.cpuSelectionMode.get().ordinal());
        syncedMode.registerValueListener(value -> icon
                .style(style -> style.backgroundTexture(cpuSelectionModeIcon(cpuSelectionModeFromOrdinal(value)))));
        button.addChild(syncedMode);
        HostElements.tooltips(button, () -> {
            CpuSelectionMode mode = cpuSelectionModeFromOrdinal(syncedMode.getValue());
            return List.of(
                    ButtonToolTips.CpuSelectionMode.text(),
                    cpuSelectionModeTooltip(mode));
        });
        return button;
    }

    public static Button createFastPlanningButton(Config config) {
        Button button = new Button()
                .noText()
                .setOnServerClick(event -> config.toggleFastPlanning.run());
        button.layout(layout -> layout.width(18).height(20));
        UIElement icon = new UIElement()
                .layout(layout -> layout.width(12).height(12))
                .style(style -> style.backgroundTexture(fastPlanningIcon(config.fastPlanningEnabled.getAsBoolean())));
        button.addChild(icon);

        var syncedEnabled = HostElements.syncedBoolean(config.fastPlanningEnabled::getAsBoolean);
        syncedEnabled.registerValueListener(
                value -> icon.style(style -> style.backgroundTexture(fastPlanningIcon(Boolean.TRUE.equals(value)))));
        button.addChild(syncedEnabled);
        HostElements.tooltips(button, () -> {
            boolean enabled = Boolean.TRUE.equals(syncedEnabled.getValue());
            return List.of(
                    Component.translatable("gui.neoecoae.host.computation.fast_planning"),
                    Component.translatable(enabled
                            ? "gui.neoecoae.host.computation.fast_planning.enabled"
                            : "gui.neoecoae.host.computation.fast_planning.disabled"));
        });
        return button;
    }

    public static UIElement createFastPlanningOpenButton(Config config) {
        UIElement buttonPanel = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(-22);
            layout.top(26);
            layout.paddingAll(2);
            layout.paddingBottom(4);
        }).style(style -> style.background(NETextures.BACKGROUND));
        buttonPanel.addChild(createFastPlanningButton(config));
        return buttonPanel;
    }

    private static IGuiTexture fastPlanningIcon(boolean enabled) {
        return AETextures.icon(enabled ? Icon.S_MACHINE : Icon.CLEAR);
    }

    private static UIElement cpuSelectionIcon(CpuSelectionMode mode) {
        return new UIElement()
                .layout(layout -> layout.width(12).height(12))
                .style(style -> style.backgroundTexture(cpuSelectionModeIcon(mode)));
    }

    private static IGuiTexture cpuSelectionModeIcon(CpuSelectionMode mode) {
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
        return index < 0 || index >= values.length ? CpuSelectionMode.ANY : values[index];
    }

    public static UIElement createInventoryPanel() {
        UIElement title = new TextElement()
                .setText("container.inventory", true)
                .textStyle(ComputationHostPanelUI::inventoryTitleTextStyle);
        return HostElements.inventoryPanel(title, LEFT_PANEL_WIDTH, LEFT_INVENTORY_HEIGHT);
    }

    private static void inventoryTitleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0x3f3d52)
                .textShadow(false);
    }

    public static UIElement createRightPanel(Config config) {
        UIElement panel = new UIElement()
                .addClasses("eco-host-card", "eco-computation-task-panel")
                .layout(layout -> layout.width(RIGHT_PANEL_WIDTH).height(PANEL_HEIGHT));
        panel.addChild(new HostTaskListElement(
                config.registries,
                config.tasks,
                RIGHT_TASK_PANEL_WIDTH,
                RIGHT_TASK_PANEL_HEIGHT,
                TASK_CARD_X,
                TASK_CARD_Y,
                TASK_CARD_WIDTH,
                TASK_CARD_HEIGHT,
                TASK_CARD_STRIDE,
                TASK_LIST_BOTTOM_Y,
                TASK_SCROLLBAR_WIDTH) {
            @Override
            protected List<Component> tooltipLines(ComputationTaskEntry entry) {
                return ComputationTaskCards.tooltipLines(entry);
            }

            @Override
            protected void drawTaskCard(GUIContext guiContext, Font font, ComputationTaskEntry entry, float x,
                    float y) {
                ComputationTaskCards.drawCard(guiContext, font, entry, Math.round(x), Math.round(y), TASK_CARD_WIDTH,
                        TASK_CARD_HEIGHT);
            }
        }.layout(layout -> layout.width(RIGHT_TASK_PANEL_WIDTH).height(RIGHT_TASK_PANEL_HEIGHT)));
        return panel;
    }

    private static UIElement usageProgressBlock(
            String labelKey,
            Supplier<HostText.UsedTotal> text,
            LongSupplier used,
            LongSupplier max,
            String tooltipKey) {
        UIElement block = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .height(20)
                .gapAll(1)
                .flexDirection(FlexDirection.COLUMN));
        block.addChild(HostElements.localizedTextSegment(labelKey, () -> HostText.MUTED)
                .layout(layout -> layout.widthPercent(100).height(9)));

        UIElement detail = HostElements.horizontalRow(10, 2);
        detail.addChild(progressBar(used, max, tooltipKey));
        UIElement value = HostElements.horizontalRow(10, 0);
        value.addChild(HostElements.textSegment(
                () -> Component.literal(text.get().usedText()),
                () -> HostText.usedValueColor(used.getAsLong(), max.getAsLong())));
        value.addChild(HostElements.textSegment(() -> Component.literal(" / "), () -> HostText.MUTED));
        value.addChild(HostElements.textSegment(() -> Component.literal(text.get().maxText()), () -> HostText.VALUE));
        detail.addChild(value);
        block.addChild(detail);
        return block;
    }

    private static UIElement progressBar(LongSupplier used, LongSupplier max, String tooltipKey) {
        ProgressBar progressBar = new ProgressBar();
        progressBar.label(label -> label.setText(""));
        progressBar.barContainer(element -> element.layout(layout -> layout.paddingAll(1)));
        progressBar.bind(
                DataBindingBuilder.floatValS2C(() -> HostText.usageRatio(used.getAsLong(), max.getAsLong())).build());
        progressBar.addClass("eco-host-progress");
        progressBar.layout(layout -> layout.width(PROGRESS_ROW_BAR_WIDTH).height(4));
        if (tooltipKey != null) {
            var syncedUsed = HostElements.syncedLong(used::getAsLong);
            var syncedMax = HostElements.syncedLong(max::getAsLong);
            progressBar.addChildren(syncedUsed, syncedMax);
            HostElements.tooltips(progressBar, () -> List.of(Component.translatable(tooltipKey)
                    .append(": ")
                    .append(HostText.fullByteProgress(syncedUsed.getValue(), syncedMax.getValue()))));
        }
        return progressBar;
    }

    private static UIElement valueBlock(String labelKey, Supplier<Component> value, IntSupplier color) {
        UIElement block = new UIElement().layout(layout -> layout
                .widthPercent(100)
                .height(20)
                .gapAll(1)
                .flexDirection(FlexDirection.COLUMN));
        block.addChild(HostElements.localizedTextSegment(labelKey, () -> HostText.MUTED)
                .layout(layout -> layout.widthPercent(100).height(9)));
        block.addChild(HostElements.textSegment(value, color)
                .layout(layout -> layout.widthPercent(100).height(10)));
        return block;
    }

}
