package cn.dancingsnow.neoecoae.gui.host;

import appeng.api.config.CpuSelectionMode;
import appeng.core.definitions.AEParts;
import appeng.core.localization.ButtonToolTips;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.widget.ECOAeInsetPanel;
import cn.dancingsnow.neoecoae.gui.widget.ECOAePanel;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostStyles;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostTaskList;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostWidgets;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * Computation host screen, built as a declarative LDLib2 element tree: an {@link ECOAePanel} window
 * holding a left stats panel and a right task list, with all values driven by suppliers off the
 * block entity. There is no hardcoded layout maths — flex handles placement and the framework syncs
 * the bound values to the client each tick.
 */
public final class NEComputationHostUI {
    private static final int UI_WIDTH = 344;
    private static final int UI_HEIGHT = 252;
    private static final int EDGE = 7;
    private static final int HEADER_TOP = 8;
    private static final int HEADER_HEIGHT = 11;
    private static final int CONTENT_Y = 24;
    private static final int STATS_PANEL_H = 132;
    private static final int PANEL_GAP = 8;
    private static final int TOOLBAR_BUTTON_SIZE = 16;
    private static final int HOTBAR_Y = 229;
    private static final int TASK_PANEL_H = HOTBAR_Y + NEHostCanvas.SLOT_SIZE - CONTENT_Y;
    private static final int MAIN_PANEL_W = 164;
    private static final int INVENTORY_Y = 171;

    private NEComputationHostUI() {
    }

    public static ModularUI create(
        ECOComputationSystemBlockEntity computation,
        BlockUIMenuType.BlockUIHolder holder,
        UIElement buildWindow
    ) {
        UIElement root = new ECOAePanel().layout(layout -> {
            layout.width(UI_WIDTH);
            layout.height(UI_HEIGHT);
        });

        root.addChild(header(computation));
        root.addChild(contentRow(computation));
        root.addChild(inventoryLabel());
        root.addChild(NEAeInventorySlots.create(EDGE + 1, INVENTORY_Y, HOTBAR_Y));
        root.addChild(cpuModeButton(computation));
        root.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
        root.addChild(buildWindow);

        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static UIElement header(ECOComputationSystemBlockEntity computation) {
        UIElement header = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(EDGE);
            layout.top(HEADER_TOP);
            layout.width(UI_WIDTH - EDGE * 2 - TOOLBAR_BUTTON_SIZE - 4);
            layout.height(HEADER_HEIGHT);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(6);
        });
        Label title = new Label();
        title.bind(DataBindingBuilder.componentS2C(computation::getHostTitle).build());
        title.textStyle(ECOHostStyles::titleText).layout(layout -> layout.flexGrow(1));
        header.addChild(title);

        Label status = new Label();
        status.bind(DataBindingBuilder.componentS2C(() -> statusText(computation)).build());
        status.textStyle(ECOHostStyles::compactValueText);
        header.addChild(status);
        return header;
    }

    private static Component statusText(ECOComputationSystemBlockEntity computation) {
        Component formed = boolLabel(computation.isFormed());
        Component active = boolLabel(computation.isHostActive());
        return Component.translatable("gui.neoecoae.machine.formed").append(": ").append(formed)
            .append("   ")
            .append(Component.translatable("gui.neoecoae.machine.active")).append(": ").append(active);
    }

    private static Component boolLabel(boolean value) {
        return value
            ? Component.translatable("gui.neoecoae.common.yes").withStyle(ChatFormatting.GREEN)
            : Component.translatable("gui.neoecoae.common.no").withStyle(ChatFormatting.RED);
    }

    private static UIElement contentRow(ECOComputationSystemBlockEntity computation) {
        UIElement row = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(EDGE);
            layout.top(CONTENT_Y);
            layout.width(UI_WIDTH - EDGE * 2);
            layout.height(TASK_PANEL_H);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.FLEX_START);
            layout.gapAll(PANEL_GAP);
        });
        row.addChild(statsPanel(computation));
        row.addChild(taskPanel(computation));
        return row;
    }

    private static UIElement statsPanel(ECOComputationSystemBlockEntity computation) {
        UIElement panel = new ECOAeInsetPanel().layout(layout -> {
            layout.width(MAIN_PANEL_W);
            layout.height(STATS_PANEL_H);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.paddingAll(8);
            layout.gapAll(4);
        });
        panel.addChild(ECOHostWidgets.statLine(
            "gui.neoecoae.computation.threads",
            () -> NEHostFormat.coloredUsedTotal(computation.getUsedThread(), computation.getTotalThread()),
            () -> ECOHostStyles.ratio(computation.getUsedThread(), computation.getTotalThread()),
            "gui.neoecoae.computation.threads"
        ));
        panel.addChild(scalarLine("gui.neoecoae.computation.parallel_count",
            () -> Component.literal(NEHostFormat.number(computation.getParallelCount()))));
        panel.addChild(scalarLine("gui.neoecoae.computation.cpu_selection_mode.short",
            () -> cpuModeShortLabel(computation.getCpuSelectionMode())));
        panel.addChild(ECOHostWidgets.statLine(
            "gui.neoecoae.computation.storage_used",
            () -> NEHostFormat.coloredUsedTotalBytes(computation.getUsedComputationBytes(), computation.getTotalBytes()),
            () -> ECOHostStyles.ratio(computation.getUsedComputationBytes(), computation.getTotalBytes()),
            "gui.neoecoae.computation.available_storage"
        ));
        panel.addChild(scalarLine("gui.neoecoae.computation.parallel_cores",
            () -> Component.literal(NEHostFormat.number(computation.getParallelCoreCount()))));
        return panel;
    }

    private static UIElement scalarLine(String key, Supplier<Component> value) {
        UIElement row = new UIElement().layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(4);
            layout.height(10);
        });
        row.addChild(new Label()
            .setValue(Component.translatable(key, Component.empty()))
            .textStyle(ECOHostStyles::hostStatTitleText)
            .layout(layout -> layout.flexGrow(1)));
        Label valueLabel = new Label();
        valueLabel.bind(DataBindingBuilder.componentS2C(value).build());
        valueLabel.textStyle(ECOHostStyles::hostStatValueText);
        row.addChild(valueLabel);
        return row;
    }

    private static UIElement taskPanel(ECOComputationSystemBlockEntity computation) {
        ECOAeInsetPanel panel = new ECOAeInsetPanel();
        panel.layout(layout -> {
            layout.flexGrow(1);
            layout.height(TASK_PANEL_H);
        });
        panel.addChild(new ECOHostTaskList(
            Component.translatable("gui.neoecoae.crafting.tasks"),
            NEHostRefresh.throttledSnapshot(
                () -> NEHostSnapshots.encodeTasks(computation.createComputationTasks()),
                NEHostRefresh.FAST_TICKS
            ),
            NEHostSnapshots::decodeTasks
        ).layout(layout -> layout.widthPercent(100).heightPercent(100)));
        return panel;
    }

    private static UIElement inventoryLabel() {
        return new Label()
            .setValue(Component.translatable("gui.neoecoae.common.inventory"))
            .textStyle(ECOHostStyles::compactLabelText)
            .layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(EDGE + 1);
                layout.top(159);
                layout.height(10);
            });
    }

    private static Button cpuModeButton(ECOComputationSystemBlockEntity computation) {
        // The cpu selection mode lives in the server-side cluster, so sync its ordinal to the client
        // and drive the icon/tooltip from the synced value instead of a stale local copy.
        int[] clientMode = {computation.getCpuSelectionMode().ordinal()};
        Supplier<CpuSelectionMode> mode = () -> {
            CpuSelectionMode[] values = CpuSelectionMode.values();
            return values[Math.clamp(clientMode[0], 0, values.length - 1)];
        };
        Button button = NEAeButtons.aeToolbarContent(
            () -> cpuModeIcon(mode.get()),
            () -> cpuModeItem(mode.get())
        );
        var modeSync = DataBindingBuilder.intValS2C(() -> computation.getCpuSelectionMode().ordinal())
            .build()
            .getSyncValue();
        modeSync.addListener(value -> {
            if (value != null) {
                clientMode[0] = value;
            }
        });
        button.addSyncValue(modeSync);
        button.setOnServerClick(event -> computation.cycleCpuSelectionMode());
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            event.hoverTooltips = new HoverTooltips(List.of(
                ButtonToolTips.CpuSelectionMode.text(),
                cpuModeTooltip(mode.get()),
                Component.translatable("gui.neoecoae.computation.cpu_selection_mode.click")
            ), null, null, null);
            event.stopPropagation();
        });
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(UI_WIDTH - EDGE - TOOLBAR_BUTTON_SIZE);
            layout.top(4);
            layout.width(TOOLBAR_BUTTON_SIZE);
            layout.height(TOOLBAR_BUTTON_SIZE);
        });
        return button;
    }

    private static NEAeSprite cpuModeIcon(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> NEAeSprite.CRAFT_HAMMER;
            case PLAYER_ONLY, MACHINE_ONLY -> null;
        };
    }

    private static ItemStack cpuModeItem(CpuSelectionMode mode) {
        return switch (mode) {
            case PLAYER_ONLY -> AEParts.TERMINAL.stack();
            case MACHINE_ONLY -> AEParts.EXPORT_BUS.stack();
            case ANY -> ItemStack.EMPTY;
        };
    }

    private static Component cpuModeShortLabel(CpuSelectionMode mode) {
        return switch (mode) {
            case PLAYER_ONLY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.short.player");
            case MACHINE_ONLY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.short.machine");
            case ANY -> Component.translatable("gui.neoecoae.computation.cpu_selection_mode.short.any");
        };
    }

    private static Component cpuModeTooltip(CpuSelectionMode mode) {
        return switch (mode) {
            case PLAYER_ONLY -> ButtonToolTips.CpuSelectionModePlayersOnly.text();
            case MACHINE_ONLY -> ButtonToolTips.CpuSelectionModeAutomationOnly.text();
            case ANY -> ButtonToolTips.CpuSelectionModeAny.text();
        };
    }
}
