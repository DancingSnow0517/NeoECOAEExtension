package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
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

public final class NECraftingHostUI {
    private NECraftingHostUI() {
    }

    public static ModularUI create(
        ECOCraftingSystemBlockEntity crafting,
        BlockUIMenuType.BlockUIHolder holder,
        UIElement buildWindow
    ) {
        UIElement root = new UIElement().layout(layout -> {
            layout.width(NECraftingLegacyCanvas.UI_WIDTH);
            layout.height(NECraftingLegacyCanvas.UI_HEIGHT);
        });
        root.addChild(new NECraftingLegacyCanvas(crafting));
        root.addChild(toolbarButton(0, () -> crafting.setOverclocked(!crafting.isOverclocked()), () -> List.of(
            Component.translatable(crafting.isOverclocked()
                ? "gui.neoecoae.crafting.overclock.on"
                : "gui.neoecoae.crafting.overclock.off"),
            Component.translatable("gui.neoecoae.crafting.overclocked.tooltip")
        )));
        root.addChild(toolbarButton(1, () -> crafting.setActiveCooling(!crafting.isActiveCooling()), () -> List.of(
            Component.translatable(crafting.isActiveCooling()
                ? "gui.neoecoae.crafting.active_cooling.on"
                : "gui.neoecoae.crafting.active_cooling.off"),
            Component.translatable("gui.neoecoae.crafting.active_cooling.tooltip")
        )));
        root.addChild(toolbarButton(2, crafting::clearCoolant, () -> List.of(
            Component.translatable("gui.neoecoae.crafting.clear_coolant"),
            Component.translatable("gui.neoecoae.crafting.clear_coolant.tooltip")
        )));
        root.addChild(NELegacyInventorySlots.create(
            NECraftingLegacyCanvas.PLAYER_INV_X,
            NECraftingLegacyCanvas.PLAYER_INV_Y,
            NECraftingLegacyCanvas.PLAYER_HOTBAR_Y
        ));
        root.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
        root.addChild(buildWindow);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static Button toolbarButton(int index, Runnable serverAction, java.util.function.Supplier<List<Component>> tooltip) {
        Button button = new Button();
        button.noText();
        button.setOnServerClick(event -> serverAction.run());
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
            tooltip.get(),
            null,
            null,
            null
        ));
        button.buttonStyle(style -> style
            .baseTexture(IGuiTexture.EMPTY)
            .hoverTexture(IGuiTexture.EMPTY)
            .pressedTexture(IGuiTexture.EMPTY));
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(NECraftingLegacyCanvas.TOOLBAR_X + index * NECraftingLegacyCanvas.TOOLBAR_BUTTON_STRIDE);
            layout.top(NECraftingLegacyCanvas.TOOLBAR_Y);
            layout.width(NECraftingLegacyCanvas.TOOLBAR_BUTTON_SIZE);
            layout.height(NECraftingLegacyCanvas.TOOLBAR_BUTTON_SIZE);
        });
        return button;
    }
}
