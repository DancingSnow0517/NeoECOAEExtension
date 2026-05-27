package cn.dancingsnow.neoecoae.gui.ldlib1;

import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;

public final class NELDLib1Textures {
    public static final ResourceBorderTexture BACKGROUND = border("textures/gui/background.png", 16, 16, 4, 4);
    public static final ResourceBorderTexture INVENTORY_BORDER = border("textures/gui/inventory_border.png", 16, 16, 1, 1);
    public static final ResourceBorderTexture SLOT = border("textures/gui/slot.png", 18, 18, 1, 1);

    public static final ResourceBorderTexture BUTTON = border("textures/gui/button.png", 20, 20, 2, 4);
    public static final ResourceBorderTexture BUTTON_DISABLED = border("textures/gui/button_disabled.png", 20, 20, 2, 4);
    public static final ResourceBorderTexture BUTTON_HOVER = border("textures/gui/button_hover.png", 20, 20, 2, 4);
    public static final ResourceBorderTexture BUTTON_HIGHLIGHTED = border("textures/gui/button_highlighted.png", 20, 20, 2, 4);

    public static final ResourceTexture BAR_CONTAINER = texture("textures/gui/bar_container.png");
    public static final ResourceTexture BAR = texture("textures/gui/bar.png");

    public static final ResourceTexture PATTERN_OVERLAY = texture("textures/gui/widget/pattern_overlay.png");
    public static final ResourceTexture OUTPUTS = texture("textures/gui/widget/outputs.png");

    public static final ResourceTexture COOLING_OFF = texture("textures/gui/widget/crafting/cooling_off.png");
    public static final ResourceTexture COOLING_OFF_DOWN = texture("textures/gui/widget/crafting/cooling_off_down.png");
    public static final ResourceTexture COOLING_ON = texture("textures/gui/widget/crafting/cooling_on.png");
    public static final ResourceTexture COOLING_ON_DOWN = texture("textures/gui/widget/crafting/cooling_on_down.png");

    public static final ResourceTexture OVERCLOCK_OFF = texture("textures/gui/widget/crafting/overclock_off.png");
    public static final ResourceTexture OVERCLOCK_OFF_DOWN = texture("textures/gui/widget/crafting/overclock_off_down.png");
    public static final ResourceTexture OVERCLOCK_ON = texture("textures/gui/widget/crafting/overclock_on.png");
    public static final ResourceTexture OVERCLOCK_ON_DOWN = texture("textures/gui/widget/crafting/overclock_on_down.png");

    public static final ResourceTexture PROGRESS_BAR_COOLANT = texture("textures/gui/crafting/coolant_progress.png");
    public static final ResourceTexture PROGRESS_BAR_HOT_COOLANT = texture("textures/gui/crafting/hot_coolant_progress.png");
    public static final ResourceTexture PROGRESS_BAR_CRAFTING = texture("textures/gui/crafting/crafting_progress.png");
    public static final ResourceTexture PROGRESS_BAR_LIMIT = texture("textures/gui/crafting/limit_progress.png");

    public static final ResourceBorderTexture CRAFTING_BACKGROUND_DARK =
        border("textures/gui/crafting/background_dark.png", 32, 32, 6, 6);
    public static final ResourceBorderTexture CRAFTING_BACKGROUND_LIGHT =
        border("textures/gui/crafting/background_light.png", 32, 32, 6, 6);
    /** Narrow dark strip used as a decorative scrollbar track on the right edge of terminal panels. */
    public static final ResourceBorderTexture SCROLLBAR_TRACK =
        border("textures/gui/crafting/background_dark.png", 32, 32, 0, 0);

    public static final ResourceTexture CRAFTING_STATUS_BACKGROUND = texture("textures/gui/crafting/status_background.png");
    public static final ResourceTexture CRAFTING_UNAVAILABLE_STATUS = texture("textures/gui/crafting/unavailable_status.png");
    public static final ResourceTexture CRAFTING_F0 = texture("textures/gui/crafting/f0.png");
    public static final ResourceTexture CRAFTING_F4 = texture("textures/gui/crafting/f4.png");
    public static final ResourceTexture CRAFTING_F6 = texture("textures/gui/crafting/f6.png");
    public static final ResourceTexture CRAFTING_F9 = texture("textures/gui/crafting/f9.png");

    private NELDLib1Textures() {
    }

    public static TextTexture text(String text, int color, int width) {
        return new TextTexture(text).setColor(color).setWidth(width).setDropShadow(false);
    }

    public static TextTexture text(java.util.function.Supplier<String> text, int color, int width) {
        return new TextTexture(text).setColor(color).setWidth(width).setDropShadow(false);
    }

    private static ResourceTexture texture(String path) {
        return new ResourceTexture("neoecoae:" + path);
    }

    private static ResourceBorderTexture border(String path, int imageWidth, int imageHeight, int borderWidth, int borderHeight) {
        return new ResourceBorderTexture("neoecoae:" + path, imageWidth, imageHeight, borderWidth, borderHeight);
    }
}
