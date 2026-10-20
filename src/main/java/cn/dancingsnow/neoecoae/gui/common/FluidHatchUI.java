package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.platform.ui.NEPlayerInventory;
import cn.dancingsnow.neoecoae.platform.ui.NEUiElement;
import cn.dancingsnow.neoecoae.platform.ui.NEUiJsonBridge;
import cn.dancingsnow.neoecoae.platform.ui.UiLayout;
import cn.dancingsnow.neoecoae.platform.ui.NEHoverTextWidget;
import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidTransferWrapper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.capability.templates.FluidTank;

/** First retained 1.21 JSON screen backed by the real LDLib1 menu and fluid/item widgets. */
public final class FluidHatchUI {
    private FluidHatchUI() {}

    public static ModularUI createFluidHatchUI(IUIHolder holder, Player player, FluidTank tank, String titleKey,
            boolean allowClickFilled, boolean allowClickDrained) {
        NEUiElement root = NEUiJsonBridge.load(new ResourceLocation("neoecoae", "screens/fluid_hatch"),
            new NEUiJsonBridge.Context() {
                @Override
                public NEUiElement createCustom(JsonObject definition) {
                    NEUiElement wrapper = new NEUiElement();
                    wrapper.layoutNode().style.direction = UiLayout.Direction.ROW;
                    switch (definition.get("custom").getAsString()) {
                        case "title" -> wrapper.addChild(new NEUiElement(new NEHoverTextWidget(titleKey, 162, 10), 162, 10));
                        case "fluid_slot" -> wrapper.addChild(new NEUiElement(new TankWidget(new FluidTransferWrapper(tank), 0,
                            0, 0, 18, 18, allowClickFilled, allowClickDrained)
                            .setShowAmount(true).setFillDirection(ProgressTexture.FillDirection.DOWN_TO_UP), 18, 18));
                        default -> throw new IllegalArgumentException("Unknown fluid hatch custom element: " + definition);
                    }
                    return wrapper;
                }

                @Override
                public NEUiElement playerInventory() { return NEPlayerInventory.create(player); }
            });
        return new ModularUI(root, holder, player);
    }
}
