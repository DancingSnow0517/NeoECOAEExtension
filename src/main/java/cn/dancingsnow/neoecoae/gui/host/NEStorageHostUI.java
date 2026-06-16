package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;

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
            layout.width(NEStorageLegacyCanvas.UI_WIDTH);
            layout.height(NEStorageLegacyCanvas.UI_HEIGHT);
        });
        root.addChild(new NEStorageLegacyCanvas(storage));
        root.addChild(NELegacyInventorySlots.create(
            8,
            NEStorageLegacyCanvas.PLAYER_INV_Y,
            NEStorageLegacyCanvas.PLAYER_HOTBAR_Y
        ));
        root.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
        root.addChild(priorityButton(priorityWindow));
        root.addChild(buildWindow);
        root.addChild(priorityWindow);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static UIElement priorityButton(UIElement priorityWindow) {
        UIElement holder = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(NEStorageLegacyCanvas.UI_WIDTH - 23);
            layout.top(1);
            layout.width(18);
            layout.height(20);
        });
        holder.addChild(new NEAeIconButtonCanvas(NEAeSprite.PRIORITY));

        Button button = new Button();
        button.noText();
        button.setOnClick(event -> priorityWindow.setDisplay(true));
        button.buttonStyle(style -> style
            .baseTexture(IGuiTexture.EMPTY)
            .hoverTexture(IGuiTexture.EMPTY)
            .pressedTexture(IGuiTexture.EMPTY));
        button.addEventListener(
            com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.HOVER_TOOLTIPS,
            event -> event.hoverTooltips = new com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips(
                    List.of(Component.translatable("gui.neoecoae.storage_priority.open")),
                    null,
                    null,
                    null
                ));
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.top(0);
            layout.width(18);
            layout.height(20);
        });
        holder.addChild(button);
        return holder;
    }
}
