package cn.dancingsnow.neoecoae.gui.nativeui.widget;

import appeng.client.gui.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * An AE2-style text button that uses AE2 toolbar button backgrounds
 * and renders centered text.
 */
public class NEAe2TextButton extends Button {

    private final int textColor;
    private final int disabledTextColor;

    public NEAe2TextButton(int x, int y, int width, int height, Component message,
                            OnPress onPress) {
        this(x, y, width, height, message, onPress, 0xFF404040, 0xFF909090);
    }

    public NEAe2TextButton(int x, int y, int width, int height, Component message,
                            OnPress onPress, int textColor, int disabledTextColor) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.textColor = textColor;
        this.disabledTextColor = disabledTextColor;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Icon bg = Icon.TOOLBAR_BUTTON_BACKGROUND;
        float alpha = active ? 1.0F : 0.5F;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(Icon.TEXTURE, getX(), getY(), width, height,
            bg.x, bg.y, bg.width, bg.height,
            Icon.TEXTURE_WIDTH, Icon.TEXTURE_HEIGHT);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int color = active ? textColor : disabledTextColor;
        var font = Minecraft.getInstance().font;
        Component msg = getMessage();
        int textW = font.width(msg);
        int textX = getX() + (width - textW) / 2;
        int textY = getY() + (height - 8) / 2;
        g.drawString(font, msg, textX, textY, color, false);
    }
}
