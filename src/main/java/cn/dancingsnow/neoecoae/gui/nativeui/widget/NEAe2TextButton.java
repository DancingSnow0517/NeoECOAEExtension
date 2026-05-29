package cn.dancingsnow.neoecoae.gui.nativeui.widget;

import cn.dancingsnow.neoecoae.gui.nativeui.screen.NENativeAe2StyleRenderer;
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
        this(x, y, width, height, message, onPress, 0xFFC6C6C6, 0xFF555555);
    }

    public NEAe2TextButton(int x, int y, int width, int height, Component message,
                            OnPress onPress, int textColor, int disabledTextColor) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.textColor = textColor;
        this.disabledTextColor = disabledTextColor;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        NENativeAe2StyleRenderer.drawAeToolbarButtonBackground(g,
            getX(), getY(), width, height, isHovered(), active);

        int color = active ? textColor : disabledTextColor;
        var font = Minecraft.getInstance().font;
        Component msg = getMessage();
        int textW = font.width(msg);
        int textX = getX() + (width - textW) / 2;
        int textY = getY() + (height - 8) / 2;
        g.drawString(font, msg, textX, textY, color, false);
    }
}
