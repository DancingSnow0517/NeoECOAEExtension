package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.StoragePriorityUI;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;

import java.util.List;

public final class NEStorageHostUI {
    private NEStorageHostUI() {
    }

    public static ModularUI create(
        ECOStorageSystemBlockEntity storage,
        BlockUIMenuType.BlockUIHolder holder,
        UIElement buildWindow,
        UIElement priorityWindow
    ) {
        UIElement root = new UIElement().layout(layout -> {
            layout.width(NEStorageAeCanvas.UI_WIDTH);
            layout.height(NEStorageAeCanvas.UI_HEIGHT);
        });
        root.addChild(new NEStorageAeCanvas(storage));
        root.addChild(NEAeInventorySlots.create(
            NEStorageAeCanvas.PLAYER_INV_X,
            NEStorageAeCanvas.PLAYER_INV_Y,
            NEStorageAeCanvas.PLAYER_HOTBAR_Y
        ));
        root.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
        root.addChild(StoragePriorityUI.createOpenButton(priorityWindow, NEStorageAeCanvas.UI_WIDTH - 24, -5));
        root.addChild(buildWindow);
        root.addChild(priorityWindow);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }
}
