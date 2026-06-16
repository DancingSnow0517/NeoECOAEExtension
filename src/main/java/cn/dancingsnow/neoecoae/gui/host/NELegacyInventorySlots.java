package cn.dancingsnow.neoecoae.gui.host;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import dev.vfyjxf.taffy.style.TaffyPosition;

final class NELegacyInventorySlots {
    private NELegacyInventorySlots() {
    }

    static InventorySlots create(int x, int y, int hotbarY) {
        InventorySlots slots = new InventorySlots();
        slots.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(162);
            layout.height(hotbarY - y + 18);
            layout.gapAll(0);
        });
        for (InventorySlots.Row row : slots.rows) {
            row.getLayout().height(18).gapAll(0);
        }
        slots.hotbar.getLayout().marginTop(hotbarY - y - 54).height(18).gapAll(0);
        slots.apply(slot -> slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY)));
        return slots;
    }
}
