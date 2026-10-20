package cn.dancingsnow.neoecoae.platform.ui;

import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.world.entity.player.Player;

/** Uses LDLib1's native slots so ordinary clicks, shift-click and carried stacks remain container operations. */
public final class NEPlayerInventory {
    private NEPlayerInventory() {}

    public static NEUiElement create(Player player) {
        var widgets = new WidgetGroup(0, 0, 162, 77);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                var slot = new SlotWidget(player.getInventory(), 9 + row * 9 + column,
                    column * 18, row * 18, true, true);
                slot.isPlayerContainer = true;
                widgets.addWidget(slot);
            }
        }
        for (int column = 0; column < 9; column++) {
            var slot = new SlotWidget(player.getInventory(), column, column * 18, 59, true, true);
            slot.isPlayerContainer = true;
            slot.isPlayerHotBar = true;
            widgets.addWidget(slot);
        }
        return new NEUiElement(widgets, 162, 77);
    }
}
