package cn.dancingsnow.neoecoae.gui.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.client.gui.Icon;
import appeng.core.definitions.AEParts;
import appeng.core.localization.ButtonToolTips;
import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.CraftingPlanningModeButton;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskCards;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskEntry;
import cn.dancingsnow.neoecoae.gui.task.HostTaskListElement;
import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.gui.Font;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.IntConsumer;
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
        IntConsumer adjustCpuSelectionMode,
        Supplier<HolderLookup.Provider> registries,
        Supplier<List<ComputationTaskEntry>> tasks,
        BooleanSupplier ignoringPatternSubstitutions,
        IntSupplier substitutionPatternCount,
        Runnable toggleIgnoringPatternSubstitutions,
        BooleanSupplier cyclePlanningEnabled,
        Runnable toggleCyclePlanning,
        BooleanSupplier fastPlannerEnabled,
        Runnable toggleFastPlanner,
        IntSupplier networkFrequency,
        IntConsumer adjustNetworkFrequency
    ) {
    }

    public static UIElement createLeftCapacityPanel(Config config) {
        UIElement panel = hostCard(LEFT_PANEL_WIDTH, LEFT_CAPACITY_HEIGHT);
        panel.addClass("eco-computation-capacity");
        panel.addChild(HostElements.sectionLabel(
            () -> Component.translatable("gui.neoecoae.host.computation.capacity"),
            () -> HostText.PRIMARY));
        panel.addChild(usageProgressBlock(
            () -> Component.translatable("gui.neoecoae.host.computation.cpu_storage"),
            () -> HostText.byteProgress(config.usedBytes.getAsLong(), config.totalBytes.getAsLong()),
            config.usedBytes,
            config.totalBytes,
            () -> Component.translatable("gui.neoecoae.host.computation.cpu_storage")
                .append(": ")
                .append(HostText.fullByteProgress(config.usedBytes.getAsLong(), config.totalBytes.getAsLong()))));
        panel.addChild(usageProgressBlock(
            () -> Component.translatable("gui.neoecoae.host.computation.thread_usage"),
            () -> HostText.typeProgress(config.usedThreads.getAsInt(), config.totalThreads.getAsInt()),
            () -> config.usedThreads.getAsInt(),
            () -> config.totalThreads.getAsInt(),
            null));
        panel.addChild(valueBlock(
            () -> Component.translatable("gui.neoecoae.host.computation.parallel_count"),
            () -> Component.literal(Integer.toString(config.parallelCount.getAsInt())),
            () -> HostText.VALUE));
        panel.addChild(valueBlock(
            () -> Component.translatable("gui.neoecoae.host.computation.free_memory"),
            () -> Component.literal(HostText.byteProgress(config.availableBytes.getAsLong(), 0).usedText()),
            () -> HostText.MUTED));
        return panel;
    }

    public static Button createCpuSelectionButton(Config config) {
        Button button = HostSideButtonBar.createButton().noText();
        button.addClass("eco-host-cpu-mode-button");
        button.layout(layout -> layout.width(CPU_MODE_BUTTON_SIZE).height(CPU_MODE_BUTTON_SIZE));

        CpuSelectionIcon icon = new CpuSelectionIcon(config.cpuSelectionMode.get());
        button.addChild(icon);
        button.setOnServerClick(event -> {
            if (event.button == 0) config.adjustCpuSelectionMode.accept(1);
            else if (event.button == 1) config.adjustCpuSelectionMode.accept(-1);
        });

        BindableValue<Integer> syncedMode = new BindableValue<>(config.cpuSelectionMode.get().ordinal());
        syncedMode.bind(DataBindingBuilder.intValS2C(() -> config.cpuSelectionMode.get().ordinal()).build());
        syncedMode.registerValueListener(value -> icon.setMode(cpuSelectionModeFromOrdinal(value)));
        syncedMode.setDisplay(false);
        button.addChild(syncedMode);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            CpuSelectionMode mode = cpuSelectionModeFromOrdinal(syncedMode.getValue());
            event.hoverTooltips = new HoverTooltips(List.of(
                ButtonToolTips.CpuSelectionMode.text(),
                cpuSelectionModeTooltip(mode)), null, null, null);
        });
        return button;
    }

    public static Button createNetworkFrequencyButton(Config config) {
        Button button = HostSideButtonBar.createButton()
            .noText()
            .addPreIcon(AETextures.icon(Icon.SCHEDULING_ROUND_ROBIN))
            .setOnServerClick(event -> {
                if (event.button == 0) config.adjustNetworkFrequency.accept(1);
                else if (event.button == 1) config.adjustNetworkFrequency.accept(-1);
            });
        button.buttonStyle(style -> style
            .baseTexture(Sprites.RECT_RD)
            .hoverTexture(Sprites.RECT_RD_LIGHT)
            .pressedTexture(Sprites.RECT_RD_DARK));
        button.addClass("eco-host-network-frequency-button");
        button.layout(layout -> layout.width(CPU_MODE_BUTTON_SIZE).height(CPU_MODE_BUTTON_SIZE));

        BindableValue<Component> syncedTooltip = new BindableValue<>(HostElements.networkFrequencyTooltip(config.networkFrequency.getAsInt()));
        syncedTooltip.bind(DataBindingBuilder.componentS2C(() -> HostElements.networkFrequencyTooltip(config.networkFrequency.getAsInt())).build());
        syncedTooltip.setDisplay(false);
        button.addChild(syncedTooltip);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = HoverTooltips.empty().append(syncedTooltip.getValue()));
        return button;
    }

    public static Button createPlanningModeButton(Config config) {
        return CraftingPlanningModeButton.create(
            config.ignoringPatternSubstitutions,
            config.substitutionPatternCount,
            config.toggleIgnoringPatternSubstitutions,
            CPU_MODE_BUTTON_SIZE);
    }

    public static Button createCyclePlanningButton(Config config) {
        Button button = HostSideButtonBar.createButton()
            .noText()
            .addPreIcon(NETextures.aeIcon(16, 240, 16, 16));
        button.buttonStyle(style -> style
            .baseTexture(Sprites.RECT_RD)
            .hoverTexture(Sprites.RECT_RD_LIGHT)
            .pressedTexture(Sprites.RECT_RD_DARK));
        button.addClass("eco-host-cycle-planning-button");
        button.layout(layout -> layout.width(CPU_MODE_BUTTON_SIZE).height(CPU_MODE_BUTTON_SIZE));
        button.setOnServerClick(event -> config.toggleCyclePlanning.run());

        BindableValue<Boolean> syncedEnabled = new BindableValue<>(config.cyclePlanningEnabled.getAsBoolean());
        syncedEnabled.bind(DataBindingBuilder.boolS2C(config.cyclePlanningEnabled::getAsBoolean).build());
        syncedEnabled.setDisplay(false);
        button.addChild(syncedEnabled);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = HoverTooltips.empty().append(Component.translatable(
                Boolean.TRUE.equals(syncedEnabled.getValue())
                    ? "gui.neoecoae.crafting.cycle_planning.on"
                    : "gui.neoecoae.crafting.cycle_planning.off")));
        return button;
    }

    public static Button createFastPlannerButton(Config config) {
        Button button = HostSideButtonBar.createButton()
            .noText()
            .addPreIcon(AETextures.icon(config.fastPlannerEnabled.getAsBoolean() ? Icon.COG : Icon.COG_DISABLED));
        button.buttonStyle(style -> style
            .baseTexture(Sprites.RECT_RD)
            .hoverTexture(Sprites.RECT_RD_LIGHT)
            .pressedTexture(Sprites.RECT_RD_DARK));
        button.addClass("eco-host-fast-planner-button");
        button.layout(layout -> layout.width(CPU_MODE_BUTTON_SIZE).height(CPU_MODE_BUTTON_SIZE));
        button.setOnServerClick(event -> config.toggleFastPlanner.run());

        UIElement icon = button.getChildren().getFirst();
        BindableValue<Boolean> syncedEnabled = new BindableValue<>(config.fastPlannerEnabled.getAsBoolean());
        syncedEnabled.bind(DataBindingBuilder.boolS2C(config.fastPlannerEnabled::getAsBoolean).build());
        syncedEnabled.registerValueListener(value -> icon.style(style -> style.backgroundTexture(
            AETextures.icon(Boolean.TRUE.equals(value) ? Icon.COG : Icon.COG_DISABLED))));
        syncedEnabled.setDisplay(false);
        button.addChild(syncedEnabled);
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
            event.hoverTooltips = HoverTooltips.empty().append(Component.translatable(
                Boolean.TRUE.equals(syncedEnabled.getValue())
                    ? "gui.neoecoae.crafting.cycle_planning.on"
                    : "gui.neoecoae.crafting.cycle_planning.off")));
        return button;
    }

    private static IGuiTexture cpuSelectionModeIcon(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> AETextures.icon(Icon.CRAFT_HAMMER);
            case PLAYER_ONLY -> new ItemStackTexture(new ItemStack(AEParts.TERMINAL));
            case MACHINE_ONLY -> new ItemStackTexture(new ItemStack(AEParts.EXPORT_BUS));
        };
    }

    private static final class CpuSelectionIcon extends UIElement {
        private IGuiTexture texture;

        private CpuSelectionIcon(CpuSelectionMode mode) {
            setMode(mode);
            layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(-1)
                .top(0)
                .width(16)
                .height(16));
        }

        private void setMode(CpuSelectionMode mode) {
            texture = cpuSelectionModeIcon(mode);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext guiContext) {
            guiContext.drawTexture(texture, getPositionX(), getPositionY(), 16, 16);
        }
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
        UIElement panel = new UIElement()
            .addClass("eco-host-inventory")
            .layout(layout -> layout
                .width(LEFT_PANEL_WIDTH)
                .height(LEFT_INVENTORY_HEIGHT)
                .flexDirection(FlexDirection.COLUMN));
        panel.addChild(new TextElement()
            .setText("container.inventory", true)
            .textStyle(ComputationHostPanelUI::inventoryTitleTextStyle));
        panel.addChild(new InventorySlots().layout(layout -> layout.marginTop(2)));
        return panel;
    }

    private static void inventoryTitleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0x3f3d52).textShadow(false);
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
            TASK_SCROLLBAR_WIDTH
        ) {
            @Override
            protected List<Component> tooltipLines(ComputationTaskEntry entry) {
                return ComputationTaskCards.tooltipLines(entry);
            }

            @Override
            protected void drawTaskCard(GUIContext guiContext, Font font, ComputationTaskEntry entry, float x, float y) {
                ComputationTaskCards.drawCard(guiContext, font, entry, Math.round(x), Math.round(y), TASK_CARD_WIDTH, TASK_CARD_HEIGHT);
            }
        }.layout(layout -> layout.width(RIGHT_TASK_PANEL_WIDTH).height(RIGHT_TASK_PANEL_HEIGHT)));
        return panel;
    }

    private static UIElement hostCard(int width, int height) {
        return new UIElement()
            .addClass("eco-host-card")
            .layout(layout -> layout.width(width).height(height).flexDirection(FlexDirection.COLUMN));
    }

    private static UIElement usageProgressBlock(
        Supplier<Component> label,
        Supplier<HostText.UsedTotal> text,
        LongSupplier used,
        LongSupplier max,
        Supplier<Component> tooltip
    ) {
        UIElement block = new UIElement().layout(layout -> layout
            .widthPercent(100)
            .height(20)
            .gapAll(1)
            .flexDirection(FlexDirection.COLUMN));
        block.addChild(HostElements.textSegment(label, () -> HostText.MUTED)
            .layout(layout -> layout.widthPercent(100).height(9)));

        UIElement detail = HostElements.horizontalRow(10, 2);
        detail.addChild(progressBar(used, max, tooltip));
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

    private static UIElement progressBar(LongSupplier used, LongSupplier max, Supplier<Component> tooltip) {
        ProgressBar progressBar = new ProgressBar();
        progressBar.label(label -> label.setText(""));
        progressBar.barContainer(element -> element.layout(layout -> layout.paddingAll(1)));
        progressBar.bind(DataBindingBuilder.floatValS2C(() -> HostText.usageRatio(used.getAsLong(), max.getAsLong())).build());
        progressBar.addClass("eco-host-progress");
        progressBar.layout(layout -> layout.width(PROGRESS_ROW_BAR_WIDTH).height(4));
        if (tooltip != null) {
            BindableValue<Component> syncedTooltip = new BindableValue<>(tooltip.get());
            syncedTooltip.bind(DataBindingBuilder.componentS2C(tooltip).build());
            syncedTooltip.setDisplay(false);
            progressBar.addChild(syncedTooltip);
            progressBar.addEventListener(UIEvents.HOVER_TOOLTIPS, event ->
                event.hoverTooltips = HoverTooltips.empty().append(syncedTooltip.getValue()));
        }
        return progressBar;
    }

    private static UIElement valueBlock(Supplier<Component> label, Supplier<Component> value, IntSupplier color) {
        UIElement block = new UIElement().layout(layout -> layout
            .widthPercent(100)
            .height(20)
            .gapAll(1)
            .flexDirection(FlexDirection.COLUMN));
        block.addChild(HostElements.textSegment(label, () -> HostText.MUTED)
            .layout(layout -> layout.widthPercent(100).height(9)));
        block.addChild(HostElements.textSegment(value, color)
            .layout(layout -> layout.widthPercent(100).height(10)));
        return block;
    }
}
