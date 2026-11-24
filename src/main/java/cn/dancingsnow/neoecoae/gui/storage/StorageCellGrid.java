package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.api.IECOTier;
import java.util.function.Supplier;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.gui.common.HostPanelTier;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.menu.HostDiskMenu.DiskSlot;
import cn.dancingsnow.neoecoae.storage.ECOStorageCellSettings;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

/** JSON-positioned disk items. A click identifies the displayed physical drive, independent of paging. */
final class StorageCellGrid extends UIElement {
    private final StoragePanelSync sync;
    private final int columns;
    private final Supplier<IECOTier> panelTier;
    private final Map<BlockPos, CellWidget> widgets = new HashMap<>();
    private List<CellWidget> visibleWidgets = List.of();
    private int visibleOffset;
    private final RPCEmitter migrateMarked;
    private final RPCEmitter previewMigration;
    private final RPCEmitter openConfiguration;
    private final ECOBulkCellActionsPopup actionsPopup;
    private final ECOBulkMigrationDialog migrationDialog;
    private BlockPos previewedPosition;
    private UUID previewedCell;

    StorageCellGrid(StoragePanelSync sync, int columns, StorageHostUI.Config config, Supplier<IECOTier> panelTier,
        List<DiskSlot> disks) {
        this.sync = sync;
        this.panelTier = panelTier;
        this.columns = columns;
        openConfiguration = addRPCEvent(RPCEventBuilder.simple(BlockPos.class, UUID.class, Boolean.class,
            (position, expected) -> {
                if (!isOpenMenu(config)) return false;
                config.cellActions().openConfiguration().accept(position, expected);
                return true;
            }));
        previewMigration = addRPCEvent(RPCEventBuilder.simple(BlockPos.class, CompoundTag.class, position -> {
            previewedPosition = null;
            previewedCell = null;
            if (!isOpenMenu(config)) return new CompoundTag();
            CompoundTag preview = config.cellActions().previewMigration().apply(position);
            if (preview.hasUUID("cell_id") && preview.getInt("total") > 0) {
                previewedPosition = position;
                previewedCell = preview.getUUID("cell_id");
            }
            return preview;
        }));
        migrateMarked = addRPCEvent(RPCEventBuilder.simple(BlockPos.class, UUID.class, Boolean.class,
            (position, expected) -> {
                if (!isOpenMenu(config) || !position.equals(previewedPosition)
                    || !expected.equals(previewedCell)) return false;
                previewedPosition = null;
                previewedCell = null;
                config.cellActions().migrateMarked().accept(position, expected);
                return true;
            }));
        migrationDialog = new ECOBulkMigrationDialog(config.registries(),
            (position, expected) -> migrateMarked.send(ignored -> {}, position, expected));
        actionsPopup = new ECOBulkCellActionsPopup(
            this::openMigrationDialog,
            config.cellActions().openPriority(),
            (position, expected) -> openConfiguration.send(ignored -> {}, position, expected),
            config.cellActions().openCompressionDisplay());
        for (DiskSlot disk : disks) {
            var widget = new CellWidget(disk);
            widgets.put(disk.position(), widget);
            addChild(widget);
        }
        sync.onPageUpdate(this::updatePage);
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY == 0) return;
            sync.scroll(event.deltaY < 0 ? 1 : -1);
            event.stopImmediatePropagation();
        });
    }

    private void updatePage() {
        var state = sync.state();
        if (state.cellOffset() != visibleOffset) {
            actionsPopup.close();
            visibleOffset = state.cellOffset();
        }
        var cells = state.cells();
        var visible = new ArrayList<CellWidget>();
        for (int index = 0; state.formed() && index < cells.size(); index++) {
            CellWidget widget = widgets.get(cells.get(index).position());
            if (widget == null) continue;
            widget.setCellIndex(index);
            visible.add(widget);
        }
        for (CellWidget widget : visibleWidgets) {
            if (!visible.contains(widget)) widget.setCellIndex(-1);
        }
        visibleWidgets = visible;
    }

    private void openActionsPopup(StorageHostUI.CellEntry cell, CellWidget widget, UIEvent event) {
        UIElement root = this;
        while (root.getParent() != null) root = root.getParent();
        if (actionsPopup.getParent() == null) {
            // Keep the menu above every host section, including those outside the disk grid.
            root.addChild(actionsPopup);
            root.addEventListener(UIEvents.MOUSE_DOWN, outside -> {
                if (actionsPopup.isOpen() && !actionsPopup.isMouseOverElement(outside.x, outside.y)) {
                    actionsPopup.close();
                    if (outside.button != 1) outside.stopImmediatePropagation();
                }
            }, true);
        }
        actionsPopup.open(cell, widget::currentCell,
            event.x - root.getPositionX() + 2,
            event.y - root.getPositionY() + 2,
            Math.round(root.getSizeWidth()), Math.round(root.getSizeHeight()));
    }

    private void openMigrationDialog(BlockPos position) {
        UIElement root = this;
        while (root.getParent() != null) root = root.getParent();
        if (migrationDialog.getParent() == null) root.addChild(migrationDialog);
        long request = migrationDialog.open(position, root.getSizeWidth(), root.getSizeHeight());
        if (!previewMigration.send((CompoundTag preview) -> migrationDialog.accept(request, preview), position)) {
            migrationDialog.accept(request, new CompoundTag());
        }
    }

    private boolean isOpenMenu(StorageHostUI.Config config) {
        var mui = getModularUI();
        var player = config.player();
        return player != null && mui != null && mui.getMenu() == player.containerMenu
            && player.containerMenu.stillValid(player);
    }

    private StorageHostUI.CellEntry cell(int index) {
        List<StorageHostUI.CellEntry> cells = sync.state().cells();
        return index >= 0 && index < cells.size() ? cells.get(index) : StorageHostUI.CellEntry.EMPTY;
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        var state = sync.state();
        if (!state.formed()) return;
        for (int i = 0; i < Math.min(columns * 3, state.cells().size()); i++) {
            var drive = state.cells().get(i);
            if (drive.position() == null) continue;
            int row = i / columns;
            var background = StoragePanelTextures.forTier(StoragePanelTextures.cellBackground(row), panelTier);
            context.drawTexture(background,
                getPositionX() + i % columns * 18, getPositionY() + row * 18,
                16, background.spriteSize.height);
        }
    }

    private final class CellWidget extends ItemSlot {
        private int index = -1;
        private boolean drawn;

        CellWidget(DiskSlot disk) {
            super(disk);
            layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).width(16).height(16).paddingAll(0));
            style(style -> style.backgroundTexture(IGuiTexture.EMPTY).overflowVisible(true));
            slotStyle(style -> style.hoverOverlay(NETextures.AE2_SLOT_HIGHLIGHT));
            setDisplay(false);
            disk.setClientActive(() -> drawn);
        }

        void setCellIndex(int index) {
            if (this.index == index) return;
            this.index = index;
            drawn = false;
            setDisplay(index >= 0);
            if (index >= 0) layout(layout -> layout.left(index % columns * 18).top(index / columns * 18));
        }

        StorageHostUI.CellEntry currentCell() {
            return cell(index).withStack(getValue());
        }

        @Override
        protected void onMouseDown(UIEvent event) {
            if (drawn && event.button == 1 && !event.isShiftDown()) {
                var cell = currentCell();
                if (cell.interactionId() != null && ECOStorageCellSettings.canConfigure(cell.stack())) {
                    event.stopImmediatePropagation();
                    event.hasHandler = true;
                    openActionsPopup(cell, this, event);
                    return;
                }
            }
            super.onMouseDown(event);
        }

        @Override
        public List<Component> getFullTooltipTexts(boolean withItemTooltips) {
            if (actionsPopup.isOpen()) return List.of();
            return tooltip(currentCell());
        }

        @Override
        protected void onHoverTooltips(UIEvent event) {
            if (actionsPopup.isOpen()) event.hoverTooltips = new HoverTooltips(List.of(), null, null, null);
            else event.hoverTooltips = new HoverTooltips(tooltip(currentCell()), null, null, null);
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            drawn = true;
            super.drawBackgroundAdditional(context);
        }

        @Override
        protected void drawHover(GUIContext context) {
            if (actionsPopup.isOpen() || !sync.state().formed() || cell(index).position() == null) return;
            // Custom slot subclasses do not inherit the item-slot stylesheet selector.
            context.drawTexture(NETextures.AE2_SLOT_HIGHLIGHT, 0, 0, 16, 16);
        }
    }

    private List<Component> tooltip(StorageHostUI.CellEntry cell) {
        List<Component> lines = new ArrayList<>();
        if (!cell.stack().isEmpty()) {
            lines.add(cell.stack().getHoverName().copy());
            long byteCapacity = cell.infinite() || cell.totalBytes() == Long.MAX_VALUE ? -1 : cell.totalBytes();
            lines.add(Component.translatable("gui.neoecoae.storage.legacy.cell_bytes",
                HostText.usedPart(HostText.fullBytes(cell.exactUsedBytes()), cell.usedBytes(), byteCapacity),
                HostText.totalPart(fullCapacity(byteCapacity))));
            lines.add(Component.translatable("gui.neoecoae.storage.legacy.cell_types",
                HostText.usedPart(HostText.ae2Amount(cell.usedTypes()), cell.usedTypes(), cell.totalTypes()),
                HostText.totalPart(capacity(cell.totalTypes()))));
            lines.add(Component.translatable("gui.neoecoae.storage.cell.priority.tooltip",
                ECOStorageCellSettings.priority(cell.stack())));
            if (cell.extractionBlockReason() != null) lines.add(cell.extractionBlockReason());
        } else {
            lines.add(Component.translatable("gui.neoecoae.storage.ui.cell_state.absent"));
        }
        return lines;
    }

    static Component pageText(StoragePanelState state, int pageSize) {
        return Component.translatable("gui.neoecoae.storage.ui.cell_page", state.cellCount(),
            state.cellOffset() / pageSize + 1, Math.max(1, (state.cellCount() + pageSize - 1) / pageSize));
    }

    static String capacity(long value) { return value < 0 ? "\u221E" : HostText.ae2Amount(value); }

    private static String fullCapacity(long value) { return value < 0 ? "\u221E" : HostText.fullBytes(value); }

    static Component tierName(StorageHostUI.CellEntry cell) {
        if (cell.panelTierName() != null) return cell.panelTierName();
        return HostPanelTier.name(cell.stack().getItem() instanceof IBasicECOCellItem item ? item.getTier() : null,
            cell.tier());
    }
}
