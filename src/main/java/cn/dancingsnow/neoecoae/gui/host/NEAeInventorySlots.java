package cn.dancingsnow.neoecoae.gui.host;

import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import dev.vfyjxf.taffy.style.TaffyPosition;

final class NEAeInventorySlots {
    private NEAeInventorySlots() {
    }

    static InventorySlots create(int x, int y, int hotbarY) {
        InventorySlots slots = new InventorySlots();
        slots.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(NEHostCanvas.PLAYER_INVENTORY_WIDTH);
            layout.height(hotbarY - y + NEHostCanvas.SLOT_SIZE);
            layout.gapAll(0);
        });
        for (InventorySlots.Row row : slots.rows) {
            row.getLayout().height(NEHostCanvas.SLOT_SIZE).gapAll(0);
        }
        slots.hotbar.getLayout()
                .marginTop(hotbarY - y - NEHostCanvas.SLOT_SIZE * 3)
                .height(NEHostCanvas.SLOT_SIZE)
                .gapAll(0);
        slots.apply(slot -> slot.style(style -> style.backgroundTexture(NEAeSprite.SLOT_BACKGROUND.texture())));
        return slots;
    }
}
