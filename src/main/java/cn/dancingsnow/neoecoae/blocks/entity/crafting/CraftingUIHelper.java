package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;

public final class CraftingUIHelper {
    private CraftingUIHelper() {
    }

    public static ModularUI createFluidHatchUI(BlockUIMenuType.BlockUIHolder holder, FluidTank tank, String titleKey,
        boolean allowClickFilled, boolean allowClickDrained) {
        UIElement root = new UIElement().layout(layout -> layout
            .paddingAll(4)
            .gapAll(2)
        ).addClass("panel_bg");

        UIElement titleContainer = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.justifyContent(AlignContent.CENTER);
            layout.alignItems(AlignItems.START);
        });
        titleContainer.addChild(new TextElement()
            .setText(titleKey, true)
            .textStyle(textStyle -> textStyle.textWrap(TextWrap.HOVER_ROLL).adaptiveHeight(true)));
        root.addChild(titleContainer);

        UIElement slotContainer = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.marginTop(10);
            layout.marginBottom(10);
            layout.justifyContent(AlignContent.CENTER);
            layout.alignItems(AlignItems.CENTER);
        });
        slotContainer.addChild(new FluidSlot()
            .bind(tank, 0)
            .setAllowClickDrained(allowClickDrained)
            .setAllowClickFilled(allowClickFilled)
            .slotStyle(slotStyle -> slotStyle.fillDirection(FillDirection.DOWN_TO_UP))
            .addClass("panel_border"));

        root.addChild(slotContainer);
        root.addChild(new InventorySlots());
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }
}