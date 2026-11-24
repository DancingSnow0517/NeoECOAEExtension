package cn.dancingsnow.neoecoae.gui.computation;

import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.menu.HostDiskMenu.DiskSlot;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import dev.vfyjxf.taffy.style.TaffyPosition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

final class ComputationDriveGrid extends UIElement {
    private static final int COLUMNS = ComputationControllerPanelState.SLOT_COUNT;
    private static final int COLUMN_PITCH = 18;
    private static final int ROW_PITCH = 36;

    ComputationDriveGrid(List<DiskSlot> disks) {
        for (DiskSlot disk : disks) {
            addChild(new DriveWidget(disk));
        }
    }

    private static final class DriveWidget extends ItemSlot {
        private DriveWidget(DiskSlot disk) {
            super(disk);
            int row = disk.displayIndex() / COLUMNS;
            int column = disk.displayIndex() % COLUMNS;
            layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(column * COLUMN_PITCH)
                .top(row * ROW_PITCH).width(16).height(16).paddingAll(0));
            style(style -> style.backgroundTexture(IGuiTexture.EMPTY).overflowVisible(true));
            slotStyle(style -> style.hoverOverlay(NETextures.AE2_SLOT_HIGHLIGHT));
        }

        @Override
        public List<Component> getFullTooltipTexts(boolean withItemTooltips) {
            if (getValue().isEmpty()) return emptyTooltip();
            List<Component> lines = new ArrayList<>(super.getFullTooltipTexts(withItemTooltips));
            lines.add(Component.translatable("gui.neoecoae.computation.ui.take_cell").withColor(HostText.MUTED));
            return lines;
        }

        @Override
        protected void onHoverTooltips(UIEvent event) {
            if (getValue().isEmpty()) event.hoverTooltips = new HoverTooltips(emptyTooltip(), null, null, null);
            else super.onHoverTooltips(event);
        }

        private List<Component> emptyTooltip() {
            return List.of(Component.translatable("gui.neoecoae.computation.ui.drive_empty"),
                Component.translatable("gui.neoecoae.computation.ui.insert_cell").withColor(HostText.MUTED));
        }
    }
}
