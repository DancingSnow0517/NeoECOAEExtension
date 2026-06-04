package cn.dancingsnow.neoecoae.gui;

import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.resources.ResourceLocation;

/** Texture ResourceLocation constants for native-UI screens. Replaces LDLib IGuiTexture registry. */
@SuppressWarnings("unused")
public class NETextures {
    public static final ResourceLocation BACKGROUND = NeoECOAE.id("textures/gui/background.png");
    public static final ResourceLocation INVENTORY_BORDER = NeoECOAE.id("textures/gui/inventory_border.png");
    public static final ResourceLocation BUTTON = NeoECOAE.id("textures/gui/button.png");
    public static final ResourceLocation BUTTON_DISABLED = NeoECOAE.id("textures/gui/button_disabled.png");
    public static final ResourceLocation BUTTON_HOVER = NeoECOAE.id("textures/gui/button_hover.png");
    public static final ResourceLocation BUTTON_HIGHLIGHTED = NeoECOAE.id("textures/gui/button_highlighted.png");
    public static final ResourceLocation ITEM_SLOT = NeoECOAE.id("textures/gui/slot.png");
    public static final ResourceLocation BAR_CONTAINER = NeoECOAE.id("textures/gui/bar_container.png");
    public static final ResourceLocation BAR = NeoECOAE.id("textures/gui/bar.png");
    public static final ResourceLocation PATTERN_OVERLAY = NeoECOAE.id("textures/gui/widget/pattern_overlay.png");
    public static final ResourceLocation OUTPUTS = NeoECOAE.id("textures/gui/widget/outputs.png");
    public static final ResourceLocation COOLING_OFF = NeoECOAE.id("textures/gui/widget/crafting/cooling_off.png");
    public static final ResourceLocation COOLING_OFF_DOWN =
            NeoECOAE.id("textures/gui/widget/crafting/cooling_off_down.png");
    public static final ResourceLocation COOLING_ON = NeoECOAE.id("textures/gui/widget/crafting/cooling_on.png");
    public static final ResourceLocation COOLING_ON_DOWN =
            NeoECOAE.id("textures/gui/widget/crafting/cooling_on_down.png");
    public static final ResourceLocation OVERCLOCK_OFF = NeoECOAE.id("textures/gui/widget/crafting/overclock_off.png");
    public static final ResourceLocation OVERCLOCK_OFF_DOWN =
            NeoECOAE.id("textures/gui/widget/crafting/overclock_off_down.png");
    public static final ResourceLocation OVERCLOCK_ON = NeoECOAE.id("textures/gui/widget/crafting/overclock_on.png");
    public static final ResourceLocation OVERCLOCK_ON_DOWN =
            NeoECOAE.id("textures/gui/widget/crafting/overclock_on_down.png");
    public static final ResourceLocation PROGRESS_BAR_COOLANT =
            NeoECOAE.id("textures/gui/crafting/coolant_progress.png");
    public static final ResourceLocation PROGRESS_BAR_HOT_COOLANT =
            NeoECOAE.id("textures/gui/crafting/hot_coolant_progress.png");
    public static final ResourceLocation PROGRESS_BAR_CRAFTING =
            NeoECOAE.id("textures/gui/crafting/crafting_progress.png");
    public static final ResourceLocation PROGRESS_BAR_LIMIT = NeoECOAE.id("textures/gui/crafting/limit_progress.png");

    public static class Crafting {
        public static final ResourceLocation BACKGROUND_DARK = NeoECOAE.id("textures/gui/crafting/background_dark.png");
        public static final ResourceLocation BACKGROUND_LIGHT =
                NeoECOAE.id("textures/gui/crafting/background_light.png");
        public static final ResourceLocation STATUS_BACKGROUND =
                NeoECOAE.id("textures/gui/crafting/status_background.png");
        public static final ResourceLocation UNAVAILABLE_STATUS =
                NeoECOAE.id("textures/gui/crafting/unavailable_status.png");
        public static final ResourceLocation F0 = NeoECOAE.id("textures/gui/crafting/f0.png");
        public static final ResourceLocation F4 = NeoECOAE.id("textures/gui/crafting/f4.png");
        public static final ResourceLocation F6 = NeoECOAE.id("textures/gui/crafting/f6.png");
        public static final ResourceLocation F9 = NeoECOAE.id("textures/gui/crafting/f9.png");
    }
}
