package cn.dancingsnow.neoecoae.gui.host;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
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
import java.util.function.Supplier;

public final class NECraftingHostUI {
    private NECraftingHostUI() {
    }

    public static ModularUI create(
        ECOCraftingSystemBlockEntity crafting,
        BlockUIMenuType.BlockUIHolder holder,
        UIElement buildWindow
    ) {
        UIElement root = new UIElement().layout(layout -> {
            layout.width(NECraftingAeCanvas.UI_WIDTH);
            layout.height(NECraftingAeCanvas.UI_HEIGHT);
        });
        root.addChild(new NECraftingAeCanvas(crafting));
        root.addChild(toolbarButton(0,
            () -> NEAeSprite.POWER_UNIT_AE,
            () -> crafting.setOverclocked(!crafting.isOverclocked()),
            () -> List.of(
                Component.translatable(crafting.isOverclocked()
                    ? "gui.neoecoae.crafting.overclock.on"
                    : "gui.neoecoae.crafting.overclock.off"),
                Component.translatable("gui.neoecoae.crafting.overclocked.tooltip")
            )
        ));
        root.addChild(toolbarButton(1,
            () -> NEAeSprite.BACKGROUND_DUST,
            () -> crafting.setActiveCooling(!crafting.isActiveCooling()),
            () -> List.of(
                Component.translatable(crafting.isActiveCooling()
                    ? "gui.neoecoae.crafting.active_cooling.on"
                    : "gui.neoecoae.crafting.active_cooling.off"),
                Component.translatable("gui.neoecoae.crafting.active_cooling.tooltip")
            )
        ));
        root.addChild(toolbarButton(2, () -> NEAeSprite.CONDENSER_OUTPUT_TRASH, crafting::clearCoolant, () -> List.of(
            Component.translatable("gui.neoecoae.crafting.clear_coolant"),
            Component.translatable("gui.neoecoae.crafting.clear_coolant.tooltip")
        )));
        root.addChild(NEAeInventorySlots.create(
            NECraftingAeCanvas.PLAYER_INV_X,
            NECraftingAeCanvas.PLAYER_INV_Y,
            NECraftingAeCanvas.PLAYER_HOTBAR_Y
        ));
        root.addChild(MultiblockBuilderUI.createOpenButton(buildWindow));
        root.addChild(buildWindow);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static Button toolbarButton(int index, Supplier<NEAeSprite> icon, Runnable serverAction, Supplier<List<Component>> tooltip) {
        Button button = NEAeButtons.aeToolbarIcon(icon);
        button.setOnServerClick(event -> serverAction.run());
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            event.hoverTooltips = new HoverTooltips(tooltip.get(), null, null, null);
            event.stopPropagation();
        });
        button.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(NECraftingAeCanvas.TOOLBAR_X + index * NECraftingAeCanvas.TOOLBAR_BUTTON_STRIDE);
            layout.top(NECraftingAeCanvas.TOOLBAR_Y);
            layout.width(NECraftingAeCanvas.TOOLBAR_BUTTON_SIZE);
            layout.height(NECraftingAeCanvas.TOOLBAR_BUTTON_SIZE);
        });
        return button;
    }
}
