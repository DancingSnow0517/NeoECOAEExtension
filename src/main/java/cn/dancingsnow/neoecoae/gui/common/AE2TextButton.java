package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/** AE2's vanilla-style text button: bundled button sprites, label colors and pressed offset. */
public class AE2TextButton extends Button {
    private final String label;

    public AE2TextButton(String label) {
        this.label = label;
        noText();
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        IGuiTexture texture;
        if (!isActive()) {
            texture = NETextures.AE2_BUTTON_DISABLED;
        } else {
            texture = getState() == State.DEFAULT
                ? NETextures.AE2_BUTTON
                : NETextures.AE2_BUTTON_HIGHLIGHTED;
        }
        guiContext.drawTexture(texture, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }

    @Override
    public void drawContents(GUIContext guiContext) {
        super.drawContents(guiContext);

        Font font = Minecraft.getInstance().font;
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        int width = Math.round(getSizeWidth());
        int height = Math.round(getSizeHeight());
        int textWidth = font.width(label);
        int textX = x + width / 2 - textWidth / 2;
        int textY = y + (height - 9) / 2 + 1;
        int yOffset;
        int color;
        if (!isActive()) {
            yOffset = -1;
            color = 0xFF413F54;
        } else if (getState() == State.DEFAULT) {
            yOffset = 1;
            color = 0xFFF2F2F2;
        } else {
            yOffset = 0;
            color = 0xFF517497;
        }
        guiContext.graphics.drawString(font, label, textX, textY - yOffset, color, false);
    }
}
