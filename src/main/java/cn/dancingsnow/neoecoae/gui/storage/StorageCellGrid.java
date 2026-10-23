package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.api.IECOTier;
import java.util.function.Supplier;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.gui.common.HostPanelTier;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
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

/** JSON-positioned disk items. A click identifies the displayed physical drive, independent of paging. */
final class StorageCellGrid extends UIElement {
    private final StoragePanelSync sync;
    private final int columns;
    private final Supplier<IECOTier> panelTier;
    private final RPCEmitter extraction;
    private final RPCEmitter insertion;
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
        for (int i = 0; i < columns * 3; i++) {
            addChild(new CellSlot(i));
        }
        addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            if (event.deltaY == 0) return;
            sync.scroll(event.deltaY < 0 ? 1 : -1);
            event.stopImmediatePropagation();
        });
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
            List<Component> lines = super.getFullTooltipTexts(withItemTooltips);
            lines.addAll(tooltip(index));
            return lines;
        }

        @Override
        protected void onHoverTooltips(UIEvent event) {
            if (getValue().isEmpty()) event.hoverTooltips = new HoverTooltips(tooltip(index), null, null, null);
            else super.onHoverTooltips(event);
        }

        @Override
        protected void drawItemStack(GUIContext context, ItemStack stack) {
            super.drawItemStack(context, stack);
        }

        @Override
        protected void drawHover(GUIContext context) {
            if (!sync.state().formed() || cell(index).position() == null) return;
            // Custom slot subclasses do not inherit the item-slot stylesheet selector.
            context.drawTexture(NETextures.AE2_SLOT_HIGHLIGHT, 0, 0, 16, 16);
        }
    }

    List<Component> tooltip(int index) {
        StoragePanelState state = sync.state();
        List<Component> lines = new ArrayList<>();
        if (index < state.cells().size() && state.cells().get(index).installed()) {
            StorageHostUI.CellEntry cell = state.cells().get(index);
            StoragePanelState.TypeLine type = state.type(cell.typeId());
            Component name = type != null ? type.name() : Component.translatable(switch (cell.kind()) {
                case StorageHostUI.CellEntry.KIND_ITEM -> "gui.neoecoae.storage.legacy.cell_info.item";
                case StorageHostUI.CellEntry.KIND_FLUID -> "gui.neoecoae.storage.legacy.cell_info.fluid";
                default -> "gui.neoecoae.storage.legacy.cell_info.other";
            });
            lines.add(Component.translatable("gui.neoecoae.storage.legacy.cell_info", name, tierName(cell)));
            lines.add(Component.translatable("gui.neoecoae.storage.legacy.cell_types",
                HostText.usedPart(HostText.ae2Amount(cell.usedTypes()), cell.usedTypes(), cell.totalTypes()),
                HostText.totalPart(capacity(cell.totalTypes()))));
            lines.add(Component.translatable("gui.neoecoae.storage.legacy.cell_bytes",
                HostText.usedPart(HostText.fullBytes(cell.usedBytes()), cell.usedBytes(), cell.totalBytes()),
                HostText.totalPart(fullCapacity(cell.totalBytes()))));
            lines.add(Component.translatable("gui.neoecoae.storage.ui.cell_state." +
                (!cell.online() || !state.formed() ? "offline" : cell.infinite() ? "infinite"
                    : cell.state().name().toLowerCase(java.util.Locale.ROOT))));
            if (cell.extractionBlockReason() != null) lines.add(cell.extractionBlockReason().copy().withStyle(neColor -> neColor.withColor(0xFF5555)));
            else lines.add(Component.translatable("gui.neoecoae.storage.ui.take_cell").withStyle(neColor -> neColor.withColor(HostText.MUTED)));
        } else {
            lines.add(Component.translatable("gui.neoecoae.storage.ui.cell_state.absent"));
            if (cell(index).position() != null) {
                lines.add(Component.translatable("gui.neoecoae.storage.ui.insert_cell").withStyle(neColor -> neColor.withColor(HostText.MUTED)));
            }
        }
        lines.add(pageText(state, columns * 3));
        lines.add(Component.translatable("gui.neoecoae.storage.ui.scroll_cells").withStyle(neColor -> neColor.withColor(HostText.MUTED)));
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
