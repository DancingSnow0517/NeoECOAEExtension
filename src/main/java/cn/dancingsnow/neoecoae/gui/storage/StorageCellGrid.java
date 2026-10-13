package cn.dancingsnow.neoecoae.gui.storage;

import cn.dancingsnow.neoecoae.gui.common.HostElements;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** JSON-positioned disk items. A click identifies the displayed physical drive, independent of paging. */
final class StorageCellGrid extends UIElement {
    private final StoragePanelSync sync;
    private final int columns;
    private final RPCEmitter extraction;
    private final RPCEmitter insertion;
    private boolean interacting;

    StorageCellGrid(StoragePanelSync sync, int columns, StorageHostUI.Config config) {
        this.sync = sync;
        this.columns = columns;
        extraction = addRPCEvent(RPCEventBuilder.simple(BlockPos.class, ItemStack.class, Boolean.class, Boolean.class,
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
            var background = StoragePanelTextures.cellBackground(row);
            context.drawTexture(background,
                getPositionX() + i % columns * 18, getPositionY() + row * 18,
                16, background.spriteSize.height);
        }
    }

    private int slotYOffset(int index) { return index / columns == 2 ? 1 : 0; }

    final class CellSlot extends ItemSlot {
        private final int index;

        CellSlot(int index) {
            this.index = index;
            layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(index % columns * 18)
                .top(index / columns * 18 + slotYOffset(index)).width(16).height(16).paddingAll(0));
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
            } else if (!cell.stack().isEmpty()) {
                interacting = true;
                if (!extraction.send((Boolean success) -> interacting = false,
                    cell.position(), cell.stack().copy(), UIElement.isShiftDown())) interacting = false;
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
            if (getValue().isEmpty()) event.hoverTooltips = HostElements.hoverTooltips(tooltip(index));
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
            lines.add(Component.translatable("gui.neoecoae.storage.legacy.cell_info", name, tierName(cell.tier())));
            lines.add(Component.translatable("gui.neoecoae.storage.legacy.cell_types",
                HostText.ae2Amount(cell.usedTypes()), capacity(cell.totalTypes())));
            lines.add(Component.translatable("gui.neoecoae.storage.legacy.cell_bytes",
                HostText.fullBytes(cell.usedBytes()), fullCapacity(cell.totalBytes())));
            lines.add(Component.translatable("gui.neoecoae.storage.ui.cell_state." +
                (!cell.online() || !state.formed() ? "offline" : cell.infinite() ? "infinite"
                    : cell.state().name().toLowerCase(java.util.Locale.ROOT))));
            if (cell.extractionBlockReason() != null) lines.add(cell.extractionBlockReason().copy().withColor(0xFF5555));
            else lines.add(Component.translatable("gui.neoecoae.storage.ui.take_cell").withColor(HostText.MUTED));
        } else {
            lines.add(Component.translatable("gui.neoecoae.storage.ui.cell_state.absent"));
            if (cell(index).position() != null) {
                lines.add(Component.translatable("gui.neoecoae.storage.ui.insert_cell").withColor(HostText.MUTED));
            }
        }
        lines.add(pageText(state, columns * 3));
        lines.add(Component.translatable("gui.neoecoae.storage.ui.scroll_cells").withColor(HostText.MUTED));
        return lines;
    }

    static Component pageText(StoragePanelState state, int pageSize) {
        return Component.translatable("gui.neoecoae.storage.ui.cell_page", state.cellCount(),
            state.cellOffset() / pageSize + 1, Math.max(1, (state.cellCount() + pageSize - 1) / pageSize));
    }

    static String capacity(long value) { return value < 0 ? "\u221E" : HostText.ae2Amount(value); }

    private static String fullCapacity(long value) { return value < 0 ? "\u221E" : HostText.fullBytes(value); }

    private static String tierName(int tier) {
        return switch (tier) { case 1 -> "L4"; case 2 -> "L6"; case 3 -> "L9"; default -> "L0"; };
    }
}
