package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

public class GuiTextures {
    public static final IGuiTexture BACKGROUND = new ResourceBorderTexture("neoecoae:textures/gui/background.png", 16, 16, 4, 4);
    public static final IGuiTexture ITEM_SLOT = new ResourceBorderTexture("neoecoae:textures/gui/slot.png", 18, 18, 2, 2);
    public static final IGuiTexture INVENTORY_BORDER = new ResourceBorderTexture("neoecoae:textures/gui/inventory_border.png", 16, 16, 1, 1);

    public static final IGuiTexture PATTERN_OVERLAY = widgetTexture("pattern_overlay.png");

    public static final IGuiTexture COOLING_OFF = widgetTexture("crafting/cooling_off.png");
    public static final IGuiTexture COOLING_ON = widgetTexture("crafting/cooling_on.png");
    public static final IGuiTexture OVERCLOCK_OFF = widgetTexture("crafting/overclock_off.png");
    public static final IGuiTexture OVERCLOCK_ON = widgetTexture("crafting/overclock_on.png");

    private static ResourceTexture widgetTexture(String path) {
        return new ResourceTexture(NeoECOAE.id("textures/gui/widget/" + path));
    }
}
