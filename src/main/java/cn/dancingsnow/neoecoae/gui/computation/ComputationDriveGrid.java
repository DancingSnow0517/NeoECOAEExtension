package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.bindings.impl.DataBindingBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.RPCEmitter;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.sync.rpc.RPCEventBuilder;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.UIElement;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.BindableValue;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.ItemSlot;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.HoverTooltips;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.event.UIEvent;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import cn.dancingsnow.neoecoae.platform.ui.runtime.layout.TaffyPosition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Physical computation drives as item slots, upper and lower per column; clicks identify the drive by position. */
final class ComputationDriveGrid extends UIElement {
    private static final int COLUMNS = ComputationControllerPanelState.SLOT_COUNT;
    private static final int COLUMN_PITCH = 18;
    private static final int ROW_PITCH = 36;
    private final ComputationControllerUI.Config config;
    private final RPCEmitter extraction;
    private final RPCEmitter insertion;
    private List<ComputationDriveEntry> upper = Collections.nCopies(COLUMNS, ComputationDriveEntry.EMPTY);
    private List<ComputationDriveEntry> lower = Collections.nCopies(COLUMNS, ComputationDriveEntry.EMPTY);
    private boolean interacting;

    ComputationDriveGrid(ComputationControllerUI.Config config) {
        this.config = config;
        extraction = addRPCEvent(RPCEventBuilder.simple(BlockPos.class, ItemStack.class, Boolean.class, Boolean.class,
            (position, expected, shift) -> isOpenMenu() && config.driveExtractor().extract(position, expected, shift)));
        insertion = addRPCEvent(RPCEventBuilder.simple(BlockPos.class, ItemStack.class, Boolean.class,
            (position, expected) -> isOpenMenu() && config.driveInserter().insert(position, expected)));
        for (int i = 0; i < COLUMNS * 2; i++) {
            addChild(new DriveSlot(i));
        }
        Tag initial = snapshot();
        read(initial);
        BindableValue<Tag> synced = new BindableValue<>(initial);
        synced.bind(DataBindingBuilder.tagS2C(this::snapshot).build());
        synced.registerValueListener(this::read);
        synced.setDisplay(false);
        addChild(synced);
    }

    private boolean isOpenMenu() {
        var mui = getModularUI();
        var player = config.player();
        return player != null && mui != null && mui.getMenu() == player.containerMenu
            && player.containerMenu.stillValid(player);
    }

    private Tag snapshot() {
        CompoundTag tag = new CompoundTag();
        tag.put("upper", writeRow(config.upperDrives().get()));
        tag.put("lower", writeRow(config.lowerDrives().get()));
        return tag;
    }

    private ListTag writeRow(List<ComputationDriveEntry> entries) {
        ListTag list = new ListTag();
        for (int i = 0; i < COLUMNS; i++) {
            ComputationDriveEntry entry = entries != null && i < entries.size() ? entries.get(i) : null;
            list.add(writeDrive(entry != null ? entry : ComputationDriveEntry.EMPTY));
        }
        return list;
    }

    private CompoundTag writeDrive(ComputationDriveEntry entry) {
        CompoundTag tag = new CompoundTag();
        if (entry.position() != null) tag.putLong("pos", entry.position().asLong());
        if (!entry.stack().isEmpty()) tag.put("stack", cn.dancingsnow.neoecoae.platform.nbt.NEStacks.save(entry.stack(), config.registries()));
        return tag;
    }

    private void read(Tag value) {
        CompoundTag tag = value instanceof CompoundTag compound ? compound : new CompoundTag();
        upper = readRow(tag.getList("upper", Tag.TAG_COMPOUND));
        lower = readRow(tag.getList("lower", Tag.TAG_COMPOUND));
    }

    private List<ComputationDriveEntry> readRow(ListTag list) {
        List<ComputationDriveEntry> entries = new ArrayList<>(COLUMNS);
        for (int i = 0; i < COLUMNS; i++) {
            entries.add(i < list.size() ? readDrive(list.getCompound(i)) : ComputationDriveEntry.EMPTY);
        }
        return entries;
    }

    private ComputationDriveEntry readDrive(CompoundTag tag) {
        BlockPos position = tag.contains("pos") ? BlockPos.of(tag.getLong("pos")) : null;
        return new ComputationDriveEntry(position, cn.dancingsnow.neoecoae.platform.nbt.NEStacks.read(config.registries(), tag.getCompound("stack")));
    }

    final class DriveSlot extends ItemSlot {
        private final int row;
        private final int column;

        DriveSlot(int index) {
            row = index / COLUMNS;
            column = index % COLUMNS;
            layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(column * COLUMN_PITCH)
                .top(row * ROW_PITCH).width(16).height(16).paddingAll(0));
            style(style -> style.backgroundTexture(IGuiTexture.EMPTY).overflowVisible(true));
            slotStyle(style -> style.hoverOverlay(NETextures.AE2_SLOT_HIGHLIGHT));
        }

        private ComputationDriveEntry entry() {
            return (row == 0 ? upper : lower).get(column);
        }

        @Override public ItemStack getValue() { return entry().stack(); }

        @Override
        protected void onMouseDown(UIEvent event) {
            // Local display slots never enter vanilla's changing slot-index map.
            event.stopImmediatePropagation();
            event.hasHandler = true;
            if (event.button != 0 && event.button != 1 || interacting) return;
            var drive = entry();
            if (drive.position() == null) return;
            var mui = getModularUI();
            if (mui == null || mui.getMenu() == null) return;
            ItemStack carried = mui.getMenu().getCarried();
            if (!carried.isEmpty()) {
                if (!drive.stack().isEmpty()) return;
                interacting = true;
                if (!insertion.send((Boolean success) -> interacting = false,
                    drive.position(), carried.copy())) interacting = false;
            } else if (!drive.stack().isEmpty()) {
                interacting = true;
                if (!extraction.send((Boolean success) -> interacting = false,
                    drive.position(), drive.stack().copy(), UIElement.isShiftDown())) interacting = false;
            }
        }

        @Override
        public List<Component> getFullTooltipTexts(boolean withItemTooltips) {
            List<Component> lines = super.getFullTooltipTexts(withItemTooltips);
            if (entry().position() != null) lines.addAll(tooltip());
            return lines;
        }

        @Override
        protected void onHoverTooltips(UIEvent event) {
            if (entry().position() == null) return;
            if (getValue().isEmpty()) event.hoverTooltips = new HoverTooltips(tooltip(), null, null, null);
            else super.onHoverTooltips(event);
        }

        @Override
        protected void drawHover(GUIContext context) {
            if (entry().position() == null) return;
            context.drawTexture(NETextures.AE2_SLOT_HIGHLIGHT, 0, 0, 16, 16);
        }

        private List<Component> tooltip() {
            if (!getValue().isEmpty()) {
                return List.of(Component.translatable("gui.neoecoae.computation.ui.take_cell")
                    .withStyle(neColor -> neColor.withColor(HostText.MUTED)));
            }
            return List.of(Component.translatable("gui.neoecoae.computation.ui.drive_empty"),
                Component.translatable("gui.neoecoae.computation.ui.insert_cell").withStyle(neColor -> neColor.withColor(HostText.MUTED)));
        }
    }
}
