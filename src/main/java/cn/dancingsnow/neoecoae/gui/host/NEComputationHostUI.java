package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class NEComputationHostUI {
    private NEComputationHostUI() {
    }

    public static ModularUI create(
        ECOComputationSystemBlockEntity computation,
        BlockUIMenuType.BlockUIHolder holder,
        UIElement buildWindow
    ) {
        UIElement root = new UIElement().layout(layout -> {
            layout.width(NEComputationLegacyCanvas.UI_WIDTH);
            layout.height(NEComputationLegacyCanvas.UI_HEIGHT);
        });
        root.addChild(new NEComputationLegacyCanvas(computation));
        root.addChild(cpuModeButton(computation));
        root.addChild(NELegacyInventorySlots.create(
            NEComputationLegacyCanvas.PLAYER_INV_X,
            NEComputationLegacyCanvas.PLAYER_INV_Y,
            NEComputationLegacyCanvas.PLAYER_HOTBAR_Y
        ));
        root.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
        root.addChild(buildWindow);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static Button cpuModeButton(ECOComputationSystemBlockEntity computation) {
        Button button = new Button();
        button.noText();
        button.setOnServerClick(event -> computation.cycleCpuSelectionMode());
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(List.of(
            Component.translatable("gui.neoecoae.computation.cpu_selection_mode"),
            Component.translatable("gui.neoecoae.computation.cpu_selection_mode.click")
        ), null, null, null));
        button.buttonStyle(style -> style
            .baseTexture(IGuiTexture.EMPTY)
            .hoverTexture(IGuiTexture.EMPTY)
            .pressedTexture(IGuiTexture.EMPTY));
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(NEComputationLegacyCanvas.TOOLBAR_BUTTON_X);
            layout.top(NEComputationLegacyCanvas.TOOLBAR_BUTTON_Y);
            layout.width(NEComputationLegacyCanvas.TOOLBAR_BUTTON_W);
            layout.height(NEComputationLegacyCanvas.TOOLBAR_BUTTON_H);
        });
        return button;
    }
}
