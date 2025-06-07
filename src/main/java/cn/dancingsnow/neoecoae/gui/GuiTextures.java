package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.NeoECOAE;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

public class GuiTextures {
    public static final IGuiTexture BACKGROUND =
            new ResourceBorderTexture("neoecoae:textures/gui/background.png",
                    16, 16, 4, 4);
    public static final IGuiTexture ITEM_SLOT =
            new ResourceBorderTexture("neoecoae:textures/gui/slot.png",
                    18, 18, 2, 2);
    public static final IGuiTexture INVENTORY_BORDER =
            new ResourceBorderTexture("neoecoae:textures/gui/inventory_border.png",
                    16, 16, 1, 1);

    public static final IGuiTexture PATTERN_OVERLAY = widgetTexture("pattern_overlay.png");

    public static final IGuiTexture COOLING_OFF = widgetTexture("crafting/cooling_off.png");
    public static final IGuiTexture COOLING_OFF_DOWN = widgetTexture("crafting/cooling_off_down.png");
    public static final IGuiTexture COOLING_ON = widgetTexture("crafting/cooling_on.png");
    public static final IGuiTexture COOLING_ON_DOWN = widgetTexture("crafting/cooling_on_down.png");

    public static final IGuiTexture OVERCLOCK_OFF = widgetTexture("crafting/overclock_off.png");
    public static final IGuiTexture OVERCLOCK_OFF_DOWN = widgetTexture("crafting/overclock_off_down.png");
    public static final IGuiTexture OVERCLOCK_ON = widgetTexture("crafting/overclock_on.png");
    public static final IGuiTexture OVERCLOCK_ON_DOWN = widgetTexture("crafting/overclock_on_down.png");

    public static final IGuiTexture PROGRESS_BAR_COOLANT =
            new ResourceTexture(NeoECOAE.id("textures/gui/crafting/coolant_progress.png"));
    public static final IGuiTexture PROGRESS_BAR_HOT_COOLANT =
            new ResourceTexture(NeoECOAE.id("textures/gui/crafting/hot_coolant_progress.png"));
    public static final IGuiTexture PROGRESS_BAR_CRAFTING =
            new ResourceTexture(NeoECOAE.id("textures/gui/crafting/crafting_progress.png"));
    public static final IGuiTexture PROGRESS_BAR_LIMIT =
            new ResourceTexture(NeoECOAE.id("textures/gui/crafting/limit_progress.png"));

    private static ResourceTexture widgetTexture(String path) {
        return new ResourceTexture(NeoECOAE.id("textures/gui/widget/" + path));
    }

    public static class Crafting {
        public static final IGuiTexture BACKGROUND_DARK =
                new ResourceBorderTexture("neoecoae:textures/gui/crafting/background_dark.png",
                        32, 32, 12, 12);
        public static final IGuiTexture BACKGROUND_LIGHT =
                new ResourceBorderTexture("neoecoae:textures/gui/crafting/background_light.png",
                        32, 32, 12, 12);
        public static final IGuiTexture SLOT =
                new ResourceBorderTexture("neoecoae:textures/gui/crafting/slot.png",
                        18, 18, 2, 2);
        public static final IGuiTexture PANEL_BACKGROUND =
                new ResourceBorderTexture("neoecoae:textures/gui/crafting/panel_background.png",
                        16, 16, 2, 2);
        public static final IGuiTexture PANEL_BORDER =
                new ResourceBorderTexture("neoecoae:textures/gui/crafting/panel_border.png",
                        16, 16, 2, 2);
        public static final IGuiTexture PANEL =
                new ResourceBorderTexture("neoecoae:textures/gui/crafting/panel.png",
                        16, 16, 2, 2);
        public static final IGuiTexture PROGRESS_BAR_EMPTY =
                new ResourceBorderTexture("neoecoae:textures/gui/crafting/progress_empty.png",
                        16, 16, 2, 2);

        public static final IGuiTexture STATUS_BACKGROUND =
                new ResourceTexture(NeoECOAE.id("textures/gui/crafting/status_background.png"));
        public static final IGuiTexture UNAVAILABLE_STATUS =
                new ResourceTexture(NeoECOAE.id("textures/gui/crafting/unavailable_status.png"));


        public static final IGuiTexture F0 = new ResourceTexture(NeoECOAE.id("textures/gui/crafting/f0.png"));
        public static final IGuiTexture F4 = new ResourceTexture(NeoECOAE.id("textures/gui/crafting/f4.png"));
        public static final IGuiTexture F6 = new ResourceTexture(NeoECOAE.id("textures/gui/crafting/f6.png"));
        public static final IGuiTexture F9 = new ResourceTexture(NeoECOAE.id("textures/gui/crafting/f9.png"));

    }
}
