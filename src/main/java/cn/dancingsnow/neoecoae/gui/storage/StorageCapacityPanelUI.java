package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostPanelUI.Config;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostPanelUI.StorageTypeLine;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostWidgets;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

final class StorageCapacityPanelUI {
    private static final int PANEL_PADDING = 2;
    private static final int PANEL_GAP = 2;
    private static final int LEFT_STORAGE_PANEL_HEIGHT_WITH_INVENTORY = 108;
    private static final int LEFT_INVENTORY_HEIGHT = 88;
    private static final int INVENTORY_SLOT_GRID_WIDTH = 162;
    private static final int LEFT_STORAGE_PANEL_WIDTH = INVENTORY_SLOT_GRID_WIDTH;
    private static final int TEXT_MAX_WIDTH = LEFT_STORAGE_PANEL_WIDTH - 16;
    private static final int SCROLLBAR_HORIZONTAL_OFFSET = 2;

    private StorageCapacityPanelUI() {
    }

    static UIElement createLeftPanel(Config config) {
        UIElement panel = new UIElement().layout(layout -> {
            layout.width(StorageHostPanelUI.LEFT_PANEL_WIDTH);
            layout.height(StorageHostPanelUI.PANEL_HEIGHT);
        });
        panel.addChild(
            HostElements.syncedDisplay(
                () -> !config.displayInfiniteStorageControls().getAsBoolean()
            ).addChild(createLeftStoragePanel(config, StorageHostPanelUI.PANEL_HEIGHT))
        );
        panel.addChild(
            HostElements.syncedDisplay(config.displayInfiniteStorageControls())
                .addChild(createLeftPanelWithInventory(config))
        );
        return panel;
    }

    private static UIElement createLeftPanelWithInventory(Config config) {
        UIElement panel = new UIElement().layout(layout -> {
            layout.width(StorageHostPanelUI.LEFT_PANEL_WIDTH);
            layout.height(StorageHostPanelUI.PANEL_HEIGHT);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(4);
        });
        panel.addChild(createLeftStoragePanel(config, LEFT_STORAGE_PANEL_HEIGHT_WITH_INVENTORY));
        panel.addChild(createInventoryPanel());
        return panel;
    }

    private static ScrollerView createLeftStoragePanel(Config config, int height) {
        ScrollerView panel = createPanel(StorageHostPanelUI.LEFT_PANEL_WIDTH, height);
        panel.addScrollViewChild(HostElements.sectionLabel(
            () -> Component.translatable("gui.neoecoae.storage.energy"),
            () -> HostText.PRIMARY
        ));
        panel.addScrollViewChild(
            new StorageMetricElements.PerformanceLabelElement(config.performanceAverageNanos())
                .layout(layout -> {
                    layout.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE);
                    layout.left(LEFT_STORAGE_PANEL_WIDTH - 61);
                    layout.top(2);
                    layout.width(51);
                    layout.height(9);
                })
        );
        panel.addScrollViewChild(StorageMetricElements.usedTotalRow(
            () -> Component.translatable("gui.neoecoae.storage.energy_storage").append(": "),
            () -> HostText.energyUsage(
                config.storedEnergy().getAsLong(),
                config.maxEnergy().getAsLong(),
                TEXT_MAX_WIDTH
            ),
            config.storedEnergy(),
            config.maxEnergy()
        ));
        config.storageTypes().forEach(line ->
            panel.addScrollViewChild(
                storageTypeBlock(line, () -> StorageSystemLoadPanelUI.isInfiniteLoad(config))
            )
        );
        return panel;
    }

    private static ScrollerView createPanel(int width, int height) {
        return ECOHostWidgets.storagePanel(
            width,
            height,
            PANEL_PADDING,
            PANEL_GAP,
            SCROLLBAR_HORIZONTAL_OFFSET
        );
    }

    private static UIElement createInventoryPanel() {
        UIElement panel = HostElements.syncedDisplay(() -> true);
        panel.layout(layout -> {
            layout.widthPercent(100);
            layout.height(LEFT_INVENTORY_HEIGHT);
            layout.flexDirection(FlexDirection.COLUMN);
        });
        panel.addChild(
            new TextElement()
                .setText("container.inventory", true)
                .textStyle(StorageCapacityPanelUI::inventoryTitleTextStyle)
        );
        InventorySlots inventorySlots = new InventorySlots();
        inventorySlots.layout(layout -> {
            layout.width(INVENTORY_SLOT_GRID_WIDTH);
            layout.marginTop(2);
        });
        inventorySlots.getChildren().forEach(
            child -> child.layout(layout -> layout.width(INVENTORY_SLOT_GRID_WIDTH))
        );
        panel.addChild(inventorySlots);
        return panel;
    }

    private static void inventoryTitleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true)
            .adaptiveWidth(true)
            .textWrap(TextWrap.HOVER_ROLL)
            .textColor(0x3f3d52)
            .textShadow(false);
    }

    private static UIElement storageTypeBlock(StorageTypeLine line, BooleanSupplier infiniteLoad) {
        UIElement block = HostElements.syncedDisplay(() -> shouldShowStorageType(line));
        block.layout(layout -> {
            layout.gapAll(2);
            layout.flexDirection(FlexDirection.COLUMN);
        });
        block.addChild(
            HostElements.sectionLabel(
                () -> line.type().desc(),
                () -> HostText.storageTypeAccentColor(line.type(), line.registryIndex())
            )
        );
        block.addChild(StorageMetricElements.infiniteAwareUsageRow(
            () -> Component.translatable("gui.neoecoae.host.metric.types"),
            () -> HostText.typeProgress(line.usedTypes().getAsLong(), line.totalTypes().getAsLong()),
            () -> StorageMetricElements.usedTotalTooltip(
                HostText.fullTypeProgress(line.usedTypes().getAsLong(), line.totalTypes().getAsLong()),
                line.usedTypes().getAsLong(),
                line.totalTypes().getAsLong()
            ),
            () -> StorageMetricElements.usedOnlyTooltip(
                HostText.fullTypeProgress(line.usedTypes().getAsLong(), 0L),
                line.usedTypes().getAsLong()
            ),
            line.usedTypes(),
            line.totalTypes(),
            () -> infiniteLoad.getAsBoolean() || line.infiniteTypes().getAsBoolean(),
            null
        ));
        block.addChild(StorageMetricElements.infiniteAwareUsageRow(
            () -> Component.translatable("gui.neoecoae.host.metric.bytes"),
            () -> HostText.byteProgress(line.usedBytes().getAsLong(), line.totalBytes().getAsLong()),
            () -> StorageMetricElements.usedTotalTooltip(
                HostText.fullByteProgressValues(line.usedBytes().getAsLong(), line.totalBytes().getAsLong()),
                line.usedBytes().getAsLong(),
                line.totalBytes().getAsLong()
            ),
            () -> StorageMetricElements.usedOnlyTooltip(
                new HostText.UsedTotal(
                    line.infiniteBytesTooltipText().get(),
                    "",
                    Component.translatable("gui.neoecoae.host.metric.bytes")
                ),
                line.usedBytes().getAsLong()
            ),
            line.usedBytes(),
            line.totalBytes(),
            infiniteLoad,
            line.infiniteBytesText()
        ));
        return block;
    }

    private static boolean shouldShowStorageType(StorageTypeLine line) {
        return line.usedBytes().getAsLong() > 0
            || line.usedTypes().getAsLong() > 0
            || line.totalBytes().getAsLong() > 0
            || line.totalTypes().getAsLong() > 0;
    }
}
