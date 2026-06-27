package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.StoragePriorityUI;
import cn.dancingsnow.neoecoae.gui.widget.ECOAeInsetPanel;
import cn.dancingsnow.neoecoae.gui.widget.ECOAePanel;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostStyles;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class NEStorageHostUI {
    static final int UI_WIDTH = 344;
    static final int UI_HEIGHT = 252;
    private static final int LEFT_PANEL_X = 8;
    private static final int LEFT_PANEL_Y = 24;
    private static final int LEFT_PANEL_W = 218;
    private static final int LEFT_PANEL_H = 132;
    private static final int METRIC_PANEL_X = 234;
    private static final int METRIC_PANEL_Y = 24;
    private static final int METRIC_PANEL_W = 106;
    private static final int METRIC_PANEL_H = 132;
    static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_LABEL_Y = 159;
    static final int PLAYER_INV_Y = 171;
    static final int PLAYER_HOTBAR_Y = 229;
    private static final int MATRIX_PANEL_X = PLAYER_INV_X + NEHostUiPrimitives.PLAYER_INVENTORY_WIDTH + 4;
    private static final int MATRIX_PANEL_Y = PLAYER_INV_Y;
    private static final int MATRIX_PANEL_W = UI_WIDTH - MATRIX_PANEL_X - 4;
    private static final int MATRIX_PANEL_H = 249 - MATRIX_PANEL_Y;

    private NEStorageHostUI() {
    }

    public static ModularUI create(
        ECOStorageSystemBlockEntity storage,
        BlockUIMenuType.BlockUIHolder holder,
        UIElement buildWindow,
        UIElement priorityWindow
    ) {
        UIElement root = new ECOAePanel().layout(layout -> {
            layout.width(UI_WIDTH);
            layout.height(UI_HEIGHT);
        });
        SupplierSnapshot snapshot = new SupplierSnapshot(storage);
        var throttledSnapshot = NEHostRefresh.throttledSnapshot(snapshot::get, NEHostRefresh.NORMAL_TICKS);
        root.addChild(header(storage));
        root.addChild(panel(LEFT_PANEL_X, LEFT_PANEL_Y, LEFT_PANEL_W, LEFT_PANEL_H, new NEStorageMonitorPanel(throttledSnapshot)));
        root.addChild(panel(METRIC_PANEL_X, METRIC_PANEL_Y, METRIC_PANEL_W, METRIC_PANEL_H, new NEStorageMetricColumns(throttledSnapshot)));
        root.addChild(panel(MATRIX_PANEL_X, MATRIX_PANEL_Y, MATRIX_PANEL_W, MATRIX_PANEL_H,
            new NEStorageMatrixPanel(throttledSnapshot, NEStorageMatrixPanel.scrollStateKey(storage.getLevel(), storage.getBlockPos()))));
        root.addChild(inventoryLabel());
        root.addChild(NEAeInventorySlots.create(PLAYER_INV_X, PLAYER_INV_Y, PLAYER_HOTBAR_Y));
        root.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
        root.addChild(StoragePriorityUI.createOpenButton(priorityWindow, UI_WIDTH - 24, -5));
        root.addChild(buildWindow);
        root.addChild(priorityWindow);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static UIElement header(ECOStorageSystemBlockEntity storage) {
        UIElement header = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(8);
            layout.top(8);
            layout.width(UI_WIDTH - 32);
            layout.height(11);
            layout.flexDirection(FlexDirection.ROW);
            layout.justifyContent(AlignContent.SPACE_BETWEEN);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(6);
        });
        Label title = new Label();
        title.bind(DataBindingBuilder.componentS2C(storage::getHostTitle).build());
        title.textStyle(ECOHostStyles::titleText).layout(layout -> layout.flexGrow(1));
        header.addChild(title);
        Label status = new Label();
        status.bind(DataBindingBuilder.componentS2C(() -> Component.translatable("gui.neoecoae.machine.formed")
            .append(": ")
            .append(boolLabel(storage.isFormed()))).build());
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

    private static Component boolLabel(boolean value) {
        return value
            ? Component.translatable("gui.neoecoae.common.yes").withStyle(ChatFormatting.GREEN)
            : Component.translatable("gui.neoecoae.common.no").withStyle(ChatFormatting.RED);
    }

    private record SupplierSnapshot(ECOStorageSystemBlockEntity storage) {
        private byte[] get() {
            return NEHostSnapshots.encode(buf -> {
                buf.writeBoolean(storage.isFormed());
                buf.writeVarLong(Math.max(0L, storage.getStoredEnergy()));
                buf.writeVarLong(Math.max(0L, storage.getMaxEnergy()));
                NEHostSnapshots.writeTypeStats(buf, storage.createStorageTypeStats());
                NEHostSnapshots.writeMatrixCells(buf, storage.createStorageMatrixCells());
            });
        }
    }
}
