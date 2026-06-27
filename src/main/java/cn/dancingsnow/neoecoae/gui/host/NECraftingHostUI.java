package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.widget.ECOAeInsetPanel;
import cn.dancingsnow.neoecoae.gui.widget.ECOAePanel;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostStyles;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostTaskList;
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

import java.util.List;
import java.util.function.Supplier;

public final class NECraftingHostUI {
    static final int UI_WIDTH = 304;
    static final int UI_HEIGHT = 216;
    private static final int EDGE = 7;
    private static final int GAP = 7;
    private static final int HEADER_Y = EDGE;
    private static final int HEADER_HEIGHT = 11;
    private static final int TOOLBAR_BUTTON_SIZE = 16;
    private static final int TOOLBAR_BUTTON_GAP = 3;
    private static final int TOOLBAR_BUTTON_STRIDE = TOOLBAR_BUTTON_SIZE + TOOLBAR_BUTTON_GAP;
    private static final int TOOLBAR_X = UI_WIDTH - EDGE - TOOLBAR_BUTTON_SIZE * 3 - TOOLBAR_BUTTON_GAP * 2;
    private static final int TOOLBAR_Y = HEADER_Y;
    private static final int TOP_AREA_Y = TOOLBAR_Y + TOOLBAR_BUTTON_SIZE + GAP;
    private static final int TOP_AREA_H = 88;
    private static final int STATUS_AREA_X = EDGE;
    private static final int STATUS_AREA_W = 66;
    private static final int MODULE_AREA_X = STATUS_AREA_X + STATUS_AREA_W + GAP;
    private static final int MODULE_AREA_W = 132;
    private static final int GAUGE_AREA_X = MODULE_AREA_X + MODULE_AREA_W + GAP;
    private static final int GAUGE_AREA_W = UI_WIDTH - EDGE - GAUGE_AREA_X;
    static final int PLAYER_INV_X = EDGE;
    private static final int PLAYER_INV_LABEL_Y = TOP_AREA_Y + TOP_AREA_H + GAP;
    static final int PLAYER_HOTBAR_Y = UI_HEIGHT - EDGE - NEHostUiPrimitives.SLOT_SIZE;
    static final int PLAYER_INV_Y = PLAYER_HOTBAR_Y - NEHostUiPrimitives.SLOT_SIZE * 3 - 2;
    private static final int TASK_PANEL_X = PLAYER_INV_X + NEHostUiPrimitives.PLAYER_INVENTORY_WIDTH + GAP;
    private static final int TASK_PANEL_Y = PLAYER_INV_LABEL_Y;
    private static final int TASK_PANEL_W = UI_WIDTH - EDGE - TASK_PANEL_X;
    private static final int TASK_PANEL_H = UI_HEIGHT - EDGE - TASK_PANEL_Y;

    private NECraftingHostUI() {
    }

    public static ModularUI create(
        ECOCraftingSystemBlockEntity crafting,
        BlockUIMenuType.BlockUIHolder holder,
        UIElement buildWindow
    ) {
        UIElement root = new ECOAePanel().layout(layout -> {
            layout.width(UI_WIDTH);
            layout.height(UI_HEIGHT);
        });
        root.addChild(header(crafting));
        root.addChild(panel(STATUS_AREA_X, TOP_AREA_Y, STATUS_AREA_W, TOP_AREA_H,
            new NECraftingStatusPanel(NEHostRefresh.throttledSnapshot(() -> statusSnapshot(crafting), NEHostRefresh.FAST_TICKS))));
        root.addChild(panel(MODULE_AREA_X, TOP_AREA_Y, MODULE_AREA_W, TOP_AREA_H,
            new NECraftingModulePreview(NEHostRefresh.throttledSnapshot(() -> moduleSnapshot(crafting), NEHostRefresh.NORMAL_TICKS))));
        root.addChild(panel(GAUGE_AREA_X, TOP_AREA_Y, GAUGE_AREA_W, TOP_AREA_H,
            new NECraftingGaugePanel(NEHostRefresh.throttledSnapshot(() -> gaugeSnapshot(crafting), NEHostRefresh.FAST_TICKS))));
        root.addChild(taskPanel(crafting));
        root.addChild(inventoryLabel());
        root.addChild(toolbarButton(0,
            () -> NEAeSprite.POWER_UNIT_AE,
            () -> crafting.setOverclocked(!crafting.isOverclocked()),
            () -> List.of(
                Component.translatable(crafting.isOverclocked()
                    ? "gui.neoecoae.crafting.overclock.on"
                    : "gui.neoecoae.crafting.overclock.off"),
                Component.translatable("gui.neoecoae.crafting.overclocked.tooltip")
            )
        ));
        root.addChild(toolbarButton(1,
            () -> NEAeSprite.TYPE_FILTER_FLUIDS,
            () -> crafting.setActiveCooling(!crafting.isActiveCooling()),
            () -> List.of(
                Component.translatable(crafting.isActiveCooling()
                    ? "gui.neoecoae.crafting.active_cooling.on"
                    : "gui.neoecoae.crafting.active_cooling.off"),
                Component.translatable("gui.neoecoae.crafting.active_cooling.tooltip")
            )
        ));
        root.addChild(toolbarButton(2, () -> NEAeSprite.CONDENSER_OUTPUT_TRASH, crafting::clearCoolant, () -> List.of(
            Component.translatable("gui.neoecoae.crafting.clear_coolant"),
            Component.translatable("gui.neoecoae.crafting.clear_coolant.tooltip")
        )));
        root.addChild(NEAeInventorySlots.create(PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y));
        root.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
        root.addChild(buildWindow);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static UIElement header(ECOCraftingSystemBlockEntity crafting) {
        UIElement header = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(EDGE);
            layout.top(HEADER_Y);
            layout.width(TOOLBAR_X - EDGE - GAP);
            layout.height(HEADER_HEIGHT);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(6);
        });
        Label title = new Label();
        title.bind(DataBindingBuilder.componentS2C(crafting::getHostTitle).build());
        title.textStyle(ECOHostStyles::titleText).layout(layout -> layout.flexGrow(1));
        header.addChild(title);
        Label status = new Label();
        status.bind(DataBindingBuilder.componentS2C(() -> statusText(crafting)).build());
        status.textStyle(ECOHostStyles::compactValueText);
        header.addChild(status);
        return header;
    }

    private static UIElement panel(int x, int y, int w, int h, UIElement child) {
        UIElement panel = new ECOAeInsetPanel().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(w);
            layout.height(h);
        });
        panel.addChild(child);
        return panel;
    }

    private static UIElement taskPanel(ECOCraftingSystemBlockEntity crafting) {
        UIElement panel = new ECOAeInsetPanel().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(TASK_PANEL_X);
            layout.top(TASK_PANEL_Y);
            layout.width(TASK_PANEL_W);
            layout.height(TASK_PANEL_H);
        });
        panel.addChild(new ECOHostTaskList(
            Component.translatable("gui.neoecoae.crafting.tasks"),
            NEHostRefresh.throttledSnapshot(
                () -> NEHostSnapshots.encodeTasks(crafting.createCraftingTasks()),
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
                layout.left(PLAYER_INV_X);
                layout.top(PLAYER_INV_LABEL_Y);
                layout.height(10);
            });
    }

    private static Button toolbarButton(int index, Supplier<NEAeSprite> icon, Runnable serverAction, Supplier<List<Component>> tooltip) {
        Button button = NEAeButtons.aeToolbarIcon(icon);
        button.setOnServerClick(event -> serverAction.run());
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            event.hoverTooltips = new HoverTooltips(tooltip.get(), null, null, null);
            event.stopPropagation();
        });
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(TOOLBAR_X + index * TOOLBAR_BUTTON_STRIDE);
            layout.top(TOOLBAR_Y);
            layout.width(TOOLBAR_BUTTON_SIZE);
            layout.height(TOOLBAR_BUTTON_SIZE);
        });
        return button;
    }

    private static Component statusText(ECOCraftingSystemBlockEntity crafting) {
        return Component.translatable("gui.neoecoae.machine.formed").append(": ").append(boolLabel(crafting.isFormed()))
            .append("   ")
            .append(Component.translatable("gui.neoecoae.machine.active")).append(": ").append(boolLabel(crafting.isHostActive()));
    }

    private static Component boolLabel(boolean value) {
        return value
            ? Component.translatable("gui.neoecoae.common.yes").withStyle(ChatFormatting.GREEN)
            : Component.translatable("gui.neoecoae.common.no").withStyle(ChatFormatting.RED);
    }

    private static byte[] statusSnapshot(ECOCraftingSystemBlockEntity crafting) {
        return NEHostSnapshots.encode(buf -> {
            buf.writeBoolean(crafting.isOverclocked());
            buf.writeBoolean(crafting.isActiveCooling());
            buf.writeVarInt(Math.max(0, crafting.getCoolant()));
        });
    }

    private static byte[] gaugeSnapshot(ECOCraftingSystemBlockEntity crafting) {
        return NEHostSnapshots.encode(buf -> {
            buf.writeBoolean(crafting.isOverclocked());
            buf.writeBoolean(crafting.isActiveCooling());
            buf.writeVarInt(Math.max(0, crafting.getCoolant()));
            buf.writeVarLong(Math.max(0L, crafting.getMaxEnergyUsage()));
        });
    }

    private static byte[] moduleSnapshot(ECOCraftingSystemBlockEntity crafting) {
        return NEHostSnapshots.encode(buf -> {
            buf.writeVarInt(Math.max(0, crafting.getWorkerCount()));
            buf.writeVarInt(Math.max(0, crafting.getParallelCount()));
            buf.writeVarInt(Math.max(0, crafting.getRunningThreadCount()));
            buf.writeVarInt(Math.max(0, crafting.getThreadCount()));
            buf.writeVarInt(Math.max(0, crafting.getAvailableThreads()));
            buf.writeBoolean(crafting.isOverclocked());
            NEHostSnapshots.writeModuleCells(buf, crafting.createCraftingModuleCells());
            NEHostSnapshots.writeItemStacks(buf, crafting.createCraftingWorkerOutputs());
        });
    }
}
