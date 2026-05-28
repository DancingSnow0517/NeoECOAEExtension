package cn.dancingsnow.neoecoae.gui.nativeui.widget;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.nativeui.NENineSliceRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A textured button that uses the project's nine-slice GUI assets
 * instead of the vanilla button look.
 */
public class NETexturedButton extends Button {

    private static final ResourceLocation TEX_BUTTON = NeoECOAE.id("textures/gui/button.png");
    private static final ResourceLocation TEX_BUTTON_HOVER = NeoECOAE.id("textures/gui/button_hover.png");
    private static final ResourceLocation TEX_BUTTON_DISABLED = NeoECOAE.id("textures/gui/button_disabled.png");
    private static final int TEX_SIZE = 20;
    private static final int BORDER_LEFT = 2;
    private static final int BORDER_TOP = 2;
    private static final int BORDER_RIGHT = 2;
    private static final int BORDER_BOTTOM = 4;

    private final int textColor;
    private final int disabledTextColor;

    public NETexturedButton(int x, int y, int width, int height, Component message,
                             OnPress onPress) {
        this(x, y, width, height, message, onPress, 0xFF20232A, 0xFF808590);
    }

    public NETexturedButton(int x, int y, int width, int height, Component message,
                             OnPress onPress, int textColor, int disabledTextColor) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.textColor = textColor;
        this.disabledTextColor = disabledTextColor;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation tex;
        if (!active) {
            tex = TEX_BUTTON_DISABLED;
        } else if (isHovered()) {
            tex = TEX_BUTTON_HOVER;
        } else {
            tex = TEX_BUTTON;
        }

        NENineSliceRenderer.drawButton(guiGraphics, tex,
            getX(), getY(), width, height,
            TEX_SIZE, TEX_SIZE,
            BORDER_LEFT, BORDER_TOP, BORDER_RIGHT, BORDER_BOTTOM);

        int color = active ? textColor : disabledTextColor;
        guiGraphics.drawCenteredString(
            Minecraft.getInstance().font,
            getMessage(),
            getX() + width / 2,
            getY() + (height - 8) / 2,
            color
        );
    }
}
