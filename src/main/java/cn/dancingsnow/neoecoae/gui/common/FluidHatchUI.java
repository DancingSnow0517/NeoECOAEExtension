package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonContext;
import cn.dancingsnow.neoecoae.gui.json.NeoUiJsonLoader;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.minecraft.resources.ResourceLocation;

public final class FluidHatchUI {
    private static final ResourceLocation JSON_LAYOUT =
        NeoECOAE.id("ui/fluid_hatch");

    private FluidHatchUI() {
    }

    public static ModularUI createFluidHatchUI(BlockUIMenuType.BlockUIHolder holder, FluidTank tank, String titleKey,
        boolean allowClickFilled, boolean allowClickDrained) {
        UIElement root = NeoUiJsonLoader.load(JSON_LAYOUT,
            new JsonContext(tank, titleKey, allowClickFilled, allowClickDrained));
        return ECOModularUI.create(root, holder.player);
    }

    private static final class JsonContext implements NeoUiJsonContext {
        private final FluidTank tank;
        private final String titleKey;
        private final boolean allowClickFilled;
        private final boolean allowClickDrained;

        private JsonContext(FluidTank tank, String titleKey, boolean allowClickFilled, boolean allowClickDrained) {
            this.tank = tank;
            this.titleKey = titleKey;
            this.allowClickFilled = allowClickFilled;
            this.allowClickDrained = allowClickDrained;
        }

        @Override
        public UIElement createCustom(JsonObject definition) {
            return switch (definition.get("custom").getAsString()) {
                case "title" -> {
                    UIElement titleContainer = new UIElement();
                    titleContainer.addChild(new TextElement()
                        .setText(titleKey, true)
                        .textStyle(textStyle -> textStyle.textWrap(TextWrap.HOVER_ROLL).adaptiveHeight(true)));
                    yield titleContainer;
                }
                case "fluid_slot" -> {
                    UIElement slotContainer = new UIElement();
                    slotContainer.addChild(new FluidSlot()
                        .bind(tank, 0)
                        .setAllowClickDrained(allowClickDrained)
                        .setAllowClickFilled(allowClickFilled)
                        .slotStyle(slotStyle -> slotStyle.fillDirection(FillDirection.DOWN_TO_UP))
                        .addClass("panel_border"));
                    yield slotContainer;
                }
                default -> throw new IllegalArgumentException("Unknown fluid hatch custom element: "
                    + definition.get("custom").getAsString());
            };
        }
    }
}
