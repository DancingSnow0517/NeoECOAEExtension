package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.api.IECOTier;
import java.util.function.Supplier;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.gui.common.HostPanelTier;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.storage.ECOStorageCellSettings;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.RPCEventBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.RPCEmitter;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvents;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
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
    private final RPCEmitter extraction;
    private final RPCEmitter insertion;
    private final RPCEmitter migrateMarked;
    private final RPCEmitter previewMigration;
    private final RPCEmitter openConfiguration;
    private final ECOBulkCellActionsPopup actionsPopup;
    private final ECOBulkMigrationDialog migrationDialog;
    private BlockPos previewedPosition;
    private UUID previewedCell;
    private boolean interacting;

    StorageCellGrid(StoragePanelSync sync, int columns, StorageHostUI.Config config) {
        this(sync, columns, config, () -> null);
    }

    StorageCellGrid(StoragePanelSync sync, int columns, StorageHostUI.Config config, Supplier<IECOTier> panelTier) {
        this.sync = sync;
        this.panelTier = panelTier;
        this.columns = columns;
        extraction = addRPCEvent(RPCEventBuilder.simple(BlockPos.class, UUID.class, Boolean.class, Boolean.class,
            (position, expected, shift) -> isOpenMenu(config) && config.cellExtractor().extract(position, expected, shift)));
        insertion = addRPCEvent(RPCEventBuilder.simple(BlockPos.class, ItemStack.class, Boolean.class,
            (position, expected) -> isOpenMenu(config) && config.cellInserter().insert(position, expected)));
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
        for (int i = 0; i < columns * 3; i++) {
            addChild(new CellSlot(i));
        }
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY == 0) return;
            sync.scroll(event.deltaY < 0 ? 1 : -1);
            event.stopImmediatePropagation();
        });
    }

    private void openActionsPopup(StorageHostUI.CellEntry cell, int index, UIEvent event) {
        UIElement root = this;
        while (root.getParentElement() != null) root = root.getParentElement();
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
        actionsPopup.open(cell, () -> cell(index),
            event.x - root.getPositionX() + 2,
            event.y - root.getPositionY() + 2,
            Math.round(root.getSizeWidth()), Math.round(root.getSizeHeight()));
    }

    private void openMigrationDialog(BlockPos position) {
        UIElement root = this;
        while (root.getParentElement() != null) root = root.getParentElement();
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
        return index < cells.size() ? cells.get(index) : StorageHostUI.CellEntry.EMPTY;
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

    final class CellSlot extends ItemSlot {
        private final int index;

        CellSlot(int index) {
            this.index = index;
            layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(index % columns * 18)
                .top(index / columns * 18).width(16).height(16).paddingAll(0));
            style(style -> style.backgroundTexture(IGuiTexture.EMPTY).overflowVisible(true));
            slotStyle(style -> style.hoverOverlay(NETextures.AE2_SLOT_HIGHLIGHT));
        }

        @Override public ItemStack getValue() { return cell(index).stack(); }

        @Override
        protected void onMouseDown(UIEvent event) {
            // Local display slots never enter vanilla's changing slot-index map.
            event.stopImmediatePropagation();
            event.hasHandler = true;
            if (event.button != 0 && event.button != 1 || interacting) return;
            var cell = cell(index);
            if (cell.position() == null) return;
            if (event.button == 1 && cell.interactionId() != null && ECOStorageCellSettings.canConfigure(cell.stack())) {
                openActionsPopup(cell, index, event);
                return;
            }
            var mui = getModularUI();
            if (mui == null || mui.getMenu() == null) return;
            ItemStack carried = mui.getMenu().getCarried();
            if (!carried.isEmpty()) {
                if (!cell.stack().isEmpty()) return;
                interacting = true;
                if (!insertion.send((Boolean success) -> interacting = false,
                    cell.position(), carried.copy())) interacting = false;
            } else if (!cell.stack().isEmpty() && cell.interactionId() != null) {
                interacting = true;
                if (!extraction.send((Boolean success) -> interacting = false,
                    cell.position(), cell.interactionId(), UIElement.isShiftDown())) interacting = false;
            }
        }

        @Override
        public List<Component> getFullTooltipTexts(boolean withItemTooltips) {
            if (actionsPopup.isOpen()) return List.of();
            return tooltip(index);
        }

        @Override
        protected void onHoverTooltips(UIEvent event) {
            if (actionsPopup.isOpen()) event.hoverTooltips = new HoverTooltips(List.of(), null, null, null);
            else event.hoverTooltips = new HoverTooltips(tooltip(index), null, null, null);
        }

        @Override
        protected void drawItemStack(GUIContext context, ItemStack stack) {
            super.drawItemStack(context, stack);
        }

        @Override
        protected void drawHover(GUIContext context) {
            if (actionsPopup.isOpen() || !sync.state().formed() || cell(index).position() == null) return;
            // Custom slot subclasses do not inherit the item-slot stylesheet selector.
            context.drawTexture(NETextures.AE2_SLOT_HIGHLIGHT, 0, 0, 16, 16);
        }
    }

    List<Component> tooltip(int index) {
        StoragePanelState state = sync.state();
        List<Component> lines = new ArrayList<>();
        if (index < state.cells().size() && state.cells().get(index).installed()) {
            StorageHostUI.CellEntry cell = state.cells().get(index);
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
