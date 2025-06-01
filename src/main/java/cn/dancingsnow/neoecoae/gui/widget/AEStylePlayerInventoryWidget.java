package cn.dancingsnow.neoecoae.gui.widget;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;

public class AEStylePlayerInventoryWidget extends WidgetGroup {
    public AEStylePlayerInventoryWidget() {
        super(0, 0, 172, 86);

        AEStyleWidgetGroup hotbar = new AEStyleWidgetGroup();
        hotbar.initTemplate();
        hotbar.setSize(162, 18);
        hotbar.setSelfPosition(5, 5 + 58);
        for (int col = 0; col < 9; col++) {
            String id = "player_inv_" + col;
            var pos = new Position(col * 18, 0);
            var slot = new AEStyleSlotWidget();
            slot.initTemplate();
            slot.setSelfPosition(pos);
            slot.setId(id);
            hotbar.addWidget(slot);
        }
        addWidget(hotbar);

        AEStyleWidgetGroup inv = new AEStyleWidgetGroup();
        inv.initTemplate();
        inv.setSize(162, 54);
        inv.setSelfPosition(5, 5);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                var id = "player_inv_" + (col + (row + 1) * 9);
                var pos = new Position(col * 18, row * 18);
                var slot = new AEStyleSlotWidget();
                slot.initTemplate();
                slot.setSelfPosition(pos);
                slot.setId(id);
                inv.addWidget(slot);
            }
        }
        addWidget(inv);
    }

    @Override
    public void initTemplate() {
    }

    @Override
    public void initWidget() {
        super.initWidget();
        for (int i = 0; i < widgets.size(); i++) {
            if (widgets.get(i) instanceof AEStyleWidgetGroup group) {
                if (group.widgets.size() == 9) {
                    for (int j = 0; j < group.widgets.size(); j++) {
                        if (group.widgets.get(j) instanceof AEStyleSlotWidget slot) {
                            slot.setContainerSlot(gui.entityPlayer.getInventory(), j);
                            slot.setLocationInfo(true, true);
                            initSlot(slot);
                        }
                    }
                } else {
                    for (int j = 0; j < group.widgets.size(); j++) {
                        if (group.widgets.get(j) instanceof AEStyleSlotWidget slot) {
                            slot.setContainerSlot(gui.entityPlayer.getInventory(), 9 + j);
                            slot.setLocationInfo(true, false);
                            initSlot(slot);
                        }
                    }
                }
            }
        }
    }

    private static void initSlot(AEStyleSlotWidget slotWidget) {
        if (LDLib.isClient() && Editor.INSTANCE != null) {
            slotWidget.setCanPutItems(false);
            slotWidget.setCanTakeItems(false);
        } else {
            slotWidget.setCanPutItems(true);
            slotWidget.setCanTakeItems(true);
        }
    }
}
